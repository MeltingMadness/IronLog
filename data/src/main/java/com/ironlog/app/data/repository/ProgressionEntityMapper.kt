package com.ironlog.app.data.repository

import com.ironlog.app.data.local.entity.ProgressionSuggestionEntity
import com.ironlog.app.data.local.entity.ProgressionTargetColumns
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import com.ironlog.app.domain.model.PreviousProgressionOutcome
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionOutcomeType
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.model.WorkoutSet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ProgressionEntityMapper(
    private val json: Json = Json { encodeDefaults = true }
) {
    fun toDomain(row: WorkoutPlanTargetEntity): WorkoutPlanTarget = WorkoutPlanTarget(
        id = row.id,
        sessionId = row.sessionId,
        planId = row.planId,
        exerciseId = row.exerciseId,
        orderIndex = row.orderIndex,
        supersetGroupId = row.supersetGroupId,
        target = row.target.toDomain(),
        config = row.progression.toDomain()
    ).also(::requireStoredTargetIdentity)

    fun toEntity(
        source: WorkoutPlanTargetEntity,
        outcome: ProgressionOutcome,
        status: ProgressionSuggestionStatus,
        createdAtEpochMillis: Long
    ): ProgressionSuggestionEntity {
        val sourceDomain = toDomain(source)
        require(outcome.sourceTarget == sourceDomain.target) {
            "Progression outcome source does not match snapshot ${source.id}"
        }
        require(outcome.reasonArguments.values.all(Double::isFinite)) {
            "Progression reason arguments must be finite"
        }
        requireEvidenceIds(outcome.countedSetIds)

        val suggestedTarget = when (outcome) {
            is ProgressionOutcome.ProposeChange -> ProgressionTargetColumns.fromDomain(outcome.proposedTarget)
            is ProgressionOutcome.KeepTarget,
            is ProgressionOutcome.InsufficientData,
            is ProgressionOutcome.NotApplicable -> null
        }
        return ProgressionSuggestionEntity(
            sourceSessionId = source.sessionId,
            sourceTargetSnapshotId = source.id,
            planId = source.planId,
            exerciseId = source.exerciseId,
            orderIndex = source.orderIndex,
            supersetGroupId = source.supersetGroupId,
            sourceTarget = source.target,
            sourceProgression = source.progression,
            outcomeType = outcome.type.name,
            reasonCode = outcome.reasonCode.name,
            reasonArgumentsJson = json.encodeToString<Map<String, Double>>(
                outcome.reasonArguments.toSortedMap()
            ),
            countedSetIdsJson = json.encodeToString<List<Long>>(outcome.countedSetIds),
            streakEffect = outcome.streakEffect.name,
            suggestedTarget = suggestedTarget,
            status = status.name,
            wasEdited = false,
            finalTarget = null,
            createdAtEpochMillis = createdAtEpochMillis,
            decidedAtEpochMillis = null
        )
    }

    fun decodeCountedSetIds(row: ProgressionSuggestionEntity): List<Long> {
        val values = decodeJson<List<Long>>(row.countedSetIdsJson, "counted set ids")
        requireEvidenceIds(values)
        require(json.encodeToString<List<Long>>(values) == row.countedSetIdsJson) {
            "Counted set ids JSON is not canonical"
        }
        return values
    }

    fun toSourceTarget(row: ProgressionSuggestionEntity): WorkoutPlanTarget = WorkoutPlanTarget(
        id = row.sourceTargetSnapshotId,
        sessionId = row.sourceSessionId,
        planId = row.planId,
        exerciseId = row.exerciseId,
        orderIndex = row.orderIndex,
        supersetGroupId = row.supersetGroupId,
        target = row.sourceTarget.toDomain(),
        config = row.sourceProgression.toDomain()
    ).also(::requireStoredTargetIdentity)

    fun toPreviousOutcome(row: ProgressionSuggestionEntity): PreviousProgressionOutcome =
        PreviousProgressionOutcome(
            sourceTarget = toSourceTarget(row),
            streakEffect = enumValueOf<ProgressionStreakEffect>(row.streakEffect)
        )

    fun toDomain(
        row: ProgressionSuggestionEntity,
        countedSets: List<WorkoutSet>
    ): ProgressionSuggestion {
        val countedSetIds = decodeCountedSetIds(row)
        val hydratedIds = countedSets.map(WorkoutSet::id)
        check(hydratedIds.distinct().size == hydratedIds.size) {
            "Duplicate counted workout sets for suggestion ${row.id}"
        }
        check(hydratedIds.all { it in countedSetIds }) {
            "Hydrated counted sets do not match suggestion ${row.id}"
        }
        countedSets.forEach { set ->
            check(
                set.sessionId == row.sourceSessionId &&
                    set.exerciseId == row.exerciseId &&
                    set.planTargetSnapshotId == row.sourceTargetSnapshotId
            ) {
                "Counted workout set ${set.id} does not belong to suggestion ${row.id}"
            }
        }

        val sourceTarget = toSourceTarget(row)
        val source = row.sourceTarget.toDomain()
        val reason = enumValueOf<ProgressionReasonCode>(row.reasonCode)
        val arguments = decodeReasonArguments(row)
        val streak = enumValueOf<ProgressionStreakEffect>(row.streakEffect)
        val suggested = row.suggestedTarget?.toDomain()
        val outcome = when (enumValueOf<ProgressionOutcomeType>(row.outcomeType)) {
            ProgressionOutcomeType.PROPOSE_CHANGE -> ProgressionOutcome.ProposeChange(
                source,
                requireNotNull(suggested) { "Missing proposed target for suggestion ${row.id}" },
                reason,
                arguments,
                streak,
                hydratedIds
            )

            ProgressionOutcomeType.KEEP_TARGET -> {
                require(suggested == null) { "Unexpected proposed target for suggestion ${row.id}" }
                ProgressionOutcome.KeepTarget(source, reason, arguments, streak, hydratedIds)
            }

            ProgressionOutcomeType.INSUFFICIENT_DATA -> {
                require(suggested == null) { "Unexpected proposed target for suggestion ${row.id}" }
                ProgressionOutcome.InsufficientData(source, reason, arguments, streak, hydratedIds)
            }

            ProgressionOutcomeType.NOT_APPLICABLE -> {
                require(suggested == null) { "Unexpected proposed target for suggestion ${row.id}" }
                ProgressionOutcome.NotApplicable(source, reason, arguments, streak, hydratedIds)
            }
        }
        return ProgressionSuggestion(
            id = row.id,
            sourceTarget = sourceTarget,
            outcome = outcome,
            countedSets = countedSets,
            status = enumValueOf<ProgressionSuggestionStatus>(row.status),
            wasEdited = row.wasEdited,
            finalTarget = row.finalTarget?.toDomain(),
            createdAtEpochMillis = row.createdAtEpochMillis,
            decidedAtEpochMillis = row.decidedAtEpochMillis
        )
    }

    private fun decodeReasonArguments(row: ProgressionSuggestionEntity): Map<String, Double> {
        val values = decodeJson<Map<String, Double>>(row.reasonArgumentsJson, "reason arguments")
        require(values.values.all(Double::isFinite)) {
            "Progression reason arguments must be finite"
        }
        require(
            json.encodeToString<Map<String, Double>>(values.toSortedMap()) == row.reasonArgumentsJson
        ) {
            "Reason arguments JSON is not canonical"
        }
        return values
    }

    private inline fun <reified T> decodeJson(value: String, label: String): T = try {
        json.decodeFromString<T>(value)
    } catch (failure: Exception) {
        throw IllegalArgumentException("Malformed progression $label JSON", failure)
    }

    private fun requireEvidenceIds(ids: List<Long>) {
        require(ids.all { it > 0L }) { "Counted workout set ids must be positive" }
        require(ids.distinct().size == ids.size) { "Counted workout set ids must be unique" }
    }

    private fun requireStoredTargetIdentity(target: WorkoutPlanTarget) {
        require(target.id > 0L) { "Progression target id must be positive" }
        require(target.sessionId > 0L) { "Progression target session id must be positive" }
        require(target.planId > 0L) { "Progression target plan id must be positive" }
        require(target.exerciseId > 0L) { "Progression target exercise id must be positive" }
        require(target.orderIndex >= 0) { "Progression target order index must not be negative" }
    }
}
