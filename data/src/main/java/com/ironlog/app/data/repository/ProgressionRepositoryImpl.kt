package com.ironlog.app.data.repository

import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.dao.ProgressionDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.ProgressionSuggestionEntity
import com.ironlog.app.data.local.entity.ProgressionTargetColumns
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import com.ironlog.app.domain.model.PreviousProgressionOutcome
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionGenerationResult
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionOutcomeType
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.progression.ProgressionConfigValidator
import com.ironlog.app.domain.progression.ProgressionEngine
import com.ironlog.app.domain.repository.ProgressionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

class ProgressionRepositoryImpl(
    private val progressionDao: ProgressionDao,
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val trainingPlanDao: TrainingPlanDao,
    private val engine: ProgressionEngine,
    private val transactionRunner: TransactionRunner,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) : ProgressionRepository {
    private val mapper = ProgressionEntityMapper()

    override fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTarget>> =
        progressionDao.observeTargetsForSession(sessionId).map { rows -> rows.map(mapper::toDomain) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>> {
        val rows = if (sessionId == null) {
            progressionDao.observePendingSuggestions()
        } else {
            progressionDao.observeSuggestionsForSession(sessionId)
        }
        return rows.mapLatest(::hydrate)
    }

    override fun observePendingCount(): Flow<Int> = progressionDao.observePendingCount()

    override suspend fun generateOutcomesForSession(sessionId: Long): ProgressionGenerationResult {
        val sourceSession = sessionDao.getSessionById(sessionId)
            ?: return EMPTY_GENERATION_RESULT
        val sourceEndTime = sourceSession.endTime ?: return EMPTY_GENERATION_RESULT
        if (sourceSession.planId == null) return EMPTY_GENERATION_RESULT

        progressionDao.getCompletedSessionIdsWithMissingOutcomesBefore(sourceEndTime, sessionId)
            .forEach { missingSessionId -> generateSingleSession(missingSessionId) }
        return generateSingleSession(sessionId)
    }

    override suspend fun generateMissingOutcomes(): Int =
        progressionDao.getCompletedSessionIdsWithMissingOutcomes()
            .sumOf { sessionId -> generateSingleSession(sessionId).insertedCount }

    override suspend fun reconcileOutstandingSuggestions(): Set<Long> {
        val staleRows = progressionDao.getPendingSuggestions().filter { suggestion ->
            val current = trainingPlanDao.getPlanExerciseAt(
                suggestion.planId,
                suggestion.exerciseId,
                suggestion.orderIndex
            )
            current == null || !current.matches(suggestion)
        }
        staleRows.forEach { row ->
            progressionDao.updateSuggestion(
                row.copy(
                    status = ProgressionSuggestionStatus.STALE.name,
                    decidedAtEpochMillis = nowEpochMillis()
                )
            )
        }
        return staleRows.mapTo(linkedSetOf()) { it.id }
    }

    override suspend fun acceptSuggestions(
        finalTargetsBySuggestionId: Map<Long, ProgressionTarget>
    ): ProgressionDecisionResult = transactionRunner.runInTransaction {
        if (finalTargetsBySuggestionId.isEmpty()) {
            return@runInTransaction ProgressionDecisionResult.Invalid(
                "Select at least one suggestion"
            )
        }

        val rows = progressionDao.getSuggestionsByIds(finalTargetsBySuggestionId.keys)
        if (rows.size != finalTargetsBySuggestionId.size ||
            rows.any { it.status != ProgressionSuggestionStatus.PENDING.name }
        ) {
            return@runInTransaction ProgressionDecisionResult.Invalid(
                "Every selected suggestion must still be PENDING"
            )
        }
        if (rows.any {
                it.outcomeType != ProgressionOutcomeType.PROPOSE_CHANGE.name ||
                    it.suggestedTarget == null
            }
        ) {
            return@runInTransaction ProgressionDecisionResult.Invalid(
                "Every selected suggestion must contain a proposed target"
            )
        }
        if (rows.groupBy { Triple(it.planId, it.exerciseId, it.orderIndex) }
                .any { it.value.size > 1 }
        ) {
            return@runInTransaction ProgressionDecisionResult.Invalid(
                "Select only one suggestion per plan position"
            )
        }

        val currentRowsBySuggestionId = rows.associate { row ->
            row.id to trainingPlanDao.getPlanExerciseAt(
                row.planId,
                row.exerciseId,
                row.orderIndex
            )
        }
        val staleIds = rows.filterTo(mutableListOf()) { row ->
            val current = currentRowsBySuggestionId[row.id]
            current == null || !current.matches(row)
        }.mapTo(linkedSetOf()) { it.id }
        if (staleIds.isNotEmpty()) {
            rows.filter { it.id in staleIds }.forEach { row ->
                progressionDao.updateSuggestion(
                    row.copy(
                        status = ProgressionSuggestionStatus.STALE.name,
                        decidedAtEpochMillis = nowEpochMillis()
                    )
                )
            }
            return@runInTransaction ProgressionDecisionResult.Stale(staleIds)
        }

        val validationErrors = rows.flatMap { row ->
            val finalTarget = requireNotNull(finalTargetsBySuggestionId[row.id])
            ProgressionConfigValidator.validationErrors(
                finalTarget,
                row.sourceProgression.toDomain()
            )
        }.distinct()
        if (validationErrors.isNotEmpty()) {
            return@runInTransaction ProgressionDecisionResult.Invalid(
                validationErrors.joinToString()
            )
        }

        rows.forEach { row ->
            val finalTarget = requireNotNull(finalTargetsBySuggestionId[row.id])
            val current = requireNotNull(currentRowsBySuggestionId[row.id])
            check(
                trainingPlanDao.updatePlanExerciseTargetsById(
                    current.id,
                    finalTarget.sets,
                    finalTarget.reps,
                    finalTarget.weightKg
                ) == 1
            ) { "Expected to update exactly one plan exercise ${current.id}" }
            val proposedTarget = requireNotNull(row.suggestedTarget).toDomain()
            progressionDao.updateSuggestion(
                row.copy(
                    status = ProgressionSuggestionStatus.ACCEPTED.name,
                    wasEdited = finalTarget != proposedTarget,
                    finalTarget = ProgressionTargetColumns.fromDomain(finalTarget),
                    decidedAtEpochMillis = nowEpochMillis()
                )
            )
        }
        ProgressionDecisionResult.Accepted(rows.mapTo(linkedSetOf()) { it.id })
    }

    override suspend fun rejectSuggestion(suggestionId: Long) {
        val row = progressionDao.getSuggestionsByIds(setOf(suggestionId)).singleOrNull() ?: return
        if (row.status != ProgressionSuggestionStatus.PENDING.name) return
        progressionDao.updateSuggestion(
            row.copy(
                status = ProgressionSuggestionStatus.REJECTED.name,
                decidedAtEpochMillis = nowEpochMillis()
            )
        )
    }

    private suspend fun generateSingleSession(sessionId: Long): ProgressionGenerationResult =
        transactionRunner.runInTransaction {
            val session = sessionDao.getSessionById(sessionId)
                ?: return@runInTransaction EMPTY_GENERATION_RESULT
            val sourceEndTime = session.endTime
                ?: return@runInTransaction EMPTY_GENERATION_RESULT
            if (session.planId == null) return@runInTransaction EMPTY_GENERATION_RESULT

            val targets = progressionDao.getTargetsForSession(sessionId)
            val sets = setDao.getSetsForSessionList(sessionId)
            val targetsById = targets.associateBy(WorkoutPlanTargetEntity::id)
            check(targetsById.size == targets.size) {
                "Duplicate progression target snapshot id in session $sessionId"
            }
            targets.forEach { target ->
                check(target.id > 0L && target.sessionId == sessionId) {
                    "Progression target ${target.id} does not belong to session $sessionId"
                }
            }
            sets.forEach { set ->
                val snapshotId = set.planTargetSnapshotId ?: return@forEach
                val target = checkNotNull(targetsById[snapshotId]) {
                    "Dangling progression target snapshot $snapshotId for workout set ${set.id}"
                }
                check(
                    set.sessionId == sessionId &&
                        target.sessionId == set.sessionId &&
                        target.exerciseId == set.exerciseId
                ) {
                    "Workout set ${set.id} is cross-linked to progression target $snapshotId"
                }
            }

            var insertedCount = 0
            targets.forEach { target ->
                val sourceTarget = mapper.toDomain(target)
                if (sourceTarget.config is ProgressionConfig.Manual) return@forEach

                val previousOutcomes = loadPreviousComparableOutcomes(
                    target = target,
                    sourceEndTime = sourceEndTime,
                    sourceSessionId = sessionId
                )
                val setsForTarget = sets
                    .filter { it.planTargetSnapshotId == target.id }
                    .map { it.toDomain() }
                val outcome = engine.evaluate(
                    ProgressionContext(
                        sourceTarget = sourceTarget,
                        setsForTarget = setsForTarget,
                        previousComparableOutcomesNewestFirst = previousOutcomes
                    )
                )
                require(outcome.sourceTarget == sourceTarget.target) {
                    "Engine outcome source does not match progression target ${target.id}"
                }
                validateEvidenceProvenance(outcome, sourceTarget, setsForTarget)
                val status = when (outcome) {
                    is ProgressionOutcome.ProposeChange -> ProgressionSuggestionStatus.PENDING
                    is ProgressionOutcome.KeepTarget,
                    is ProgressionOutcome.InsufficientData -> ProgressionSuggestionStatus.INFORMATIONAL
                    is ProgressionOutcome.NotApplicable -> null
                }
                if (status != null) {
                    val insertedId = progressionDao.insertSuggestion(
                        mapper.toEntity(
                            source = target,
                            outcome = outcome,
                            status = status,
                            createdAtEpochMillis = nowEpochMillis()
                        )
                    )
                    if (insertedId > 0L) insertedCount += 1
                }
            }

            val rows = if (targets.isEmpty()) {
                emptyList()
            } else {
                progressionDao.getSuggestionsForTargetIds(targets.map { it.id })
            }
            val statuses = rows.map { enumValueOf<ProgressionSuggestionStatus>(it.status) }
            ProgressionGenerationResult(
                insertedCount = insertedCount,
                reviewItemCount = statuses.count {
                    it == ProgressionSuggestionStatus.PENDING ||
                        it == ProgressionSuggestionStatus.INFORMATIONAL
                },
                pendingCount = statuses.count { it == ProgressionSuggestionStatus.PENDING }
            )
        }

    private fun validateEvidenceProvenance(
        outcome: ProgressionOutcome,
        sourceTarget: WorkoutPlanTarget,
        setsForTarget: List<WorkoutSet>
    ) {
        val evidenceIds = outcome.countedSetIds
        check(evidenceIds.all { it > 0L } && evidenceIds.distinct().size == evidenceIds.size) {
            "Engine evidence for progression target ${sourceTarget.id} must contain positive unique set ids"
        }
        val setsById = setsForTarget.groupBy(WorkoutSet::id)
        check(evidenceIds.all { setsById[it]?.size == 1 }) {
            "Engine evidence does not belong uniquely to progression target ${sourceTarget.id}"
        }

        val orderedWorkSets = setsForTarget
            .filterNot(WorkoutSet::isWarmup)
            .sortedWith(compareBy(WorkoutSet::setNumber, WorkoutSet::completedAt, WorkoutSet::id))
        val availableEvidenceIds = orderedWorkSets
            .map(WorkoutSet::id)
            .filter { it > 0L }
            .distinct()
        fun countedEvidenceIds(): List<Long> {
            check(sourceTarget.target.sets >= 0) {
                "Engine returned counted evidence for invalid progression target ${sourceTarget.id}"
            }
            return orderedWorkSets.take(sourceTarget.target.sets).map(WorkoutSet::id)
        }

        val expectedEvidenceIds = when (outcome) {
            is ProgressionOutcome.ProposeChange,
            is ProgressionOutcome.KeepTarget -> countedEvidenceIds()
            is ProgressionOutcome.InsufficientData -> when (outcome.reasonCode) {
                ProgressionReasonCode.CONFIG_INVALID,
                ProgressionReasonCode.RULE_REVISION_UNSUPPORTED,
                ProgressionReasonCode.TOO_FEW_WORK_SETS,
                ProgressionReasonCode.SET_NUMBER_INVALID,
                ProgressionReasonCode.SET_VALUE_INVALID -> availableEvidenceIds
                ProgressionReasonCode.MANUAL_WEIGHT_DEVIATION,
                ProgressionReasonCode.RPE_MISSING,
                ProgressionReasonCode.RPE_INVALID -> countedEvidenceIds()
                else -> error(
                    "Unsupported insufficient-data evidence contract ${outcome.reasonCode} " +
                        "for progression target ${sourceTarget.id}"
                )
            }
            is ProgressionOutcome.NotApplicable -> emptyList()
        }
        check(evidenceIds == expectedEvidenceIds) {
            "Engine evidence order or completeness differs from progression target ${sourceTarget.id}"
        }
    }

    private suspend fun loadPreviousComparableOutcomes(
        target: WorkoutPlanTargetEntity,
        sourceEndTime: Long,
        sourceSessionId: Long
    ): List<PreviousProgressionOutcome> {
        val comparableTargets = progressionDao.getPreviousTargets(
            planId = target.planId,
            exerciseId = target.exerciseId,
            orderIndex = target.orderIndex,
            sourceEndTime = sourceEndTime,
            sourceSessionId = sourceSessionId
        ).takeWhile { previous ->
            previous.target == target.target && previous.progression == target.progression
        }
        if (comparableTargets.isEmpty()) return emptyList()

        val rows = progressionDao.getSuggestionsForTargetIds(comparableTargets.map { it.id })
        return comparableTargets.map { previousTarget ->
            val matchingRows = rows.filter { row ->
                row.sourceTargetSnapshotId == previousTarget.id &&
                    row.sourceProgression.ruleRevision == previousTarget.progression.ruleRevision
            }
            check(matchingRows.size == 1) {
                "Expected one progression outcome for target ${previousTarget.id} at revision ${previousTarget.progression.ruleRevision}"
            }
            val row = matchingRows.single()
            check(row.hasExactDuplicatedSource(previousTarget)) {
                "Progression outcome ${row.id} source differs from target ${previousTarget.id}"
            }
            mapper.toPreviousOutcome(row)
        }
    }

    private suspend fun hydrate(rows: List<ProgressionSuggestionEntity>): List<ProgressionSuggestion> {
        val idsByRow = rows.associateWith(mapper::decodeCountedSetIds)
        val allIds = idsByRow.values.flatten().distinct()
        val setsById = if (allIds.isEmpty()) {
            emptyMap()
        } else {
            setDao.getSetsByIds(allIds).associateBy { it.id }
        }
        return rows.map { row ->
            val expectedIds = idsByRow.getValue(row)
            val countedSets = expectedIds.map { id ->
                checkNotNull(setsById[id]) {
                    "Missing counted workout set $id for suggestion ${row.id}"
                }.toDomain()
            }
            mapper.toDomain(row, countedSets)
        }
    }

    private fun ProgressionSuggestionEntity.hasExactDuplicatedSource(
        target: WorkoutPlanTargetEntity
    ): Boolean =
        sourceTargetSnapshotId == target.id &&
            sourceSessionId == target.sessionId &&
            planId == target.planId &&
            exerciseId == target.exerciseId &&
            orderIndex == target.orderIndex &&
            supersetGroupId == target.supersetGroupId &&
            sourceTarget == target.target &&
            sourceProgression == target.progression

    private fun PlanExerciseEntity.matches(source: ProgressionSuggestionEntity): Boolean =
        planId == source.planId &&
            exerciseId == source.exerciseId &&
            orderIndex == source.orderIndex &&
            supersetGroupId == source.supersetGroupId &&
            targetSets == source.sourceTarget.sets &&
            targetReps == source.sourceTarget.reps &&
            targetWeightKg == source.sourceTarget.weightKg &&
            progression == source.sourceProgression

    private companion object {
        val EMPTY_GENERATION_RESULT = ProgressionGenerationResult(
            insertedCount = 0,
            reviewItemCount = 0,
            pendingCount = 0
        )
    }
}
