package com.ironlog.app.data.repository

import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.dao.ProgressionDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.ProgressionConfigColumns
import com.ironlog.app.data.local.entity.ProgressionSuggestionEntity
import com.ironlog.app.data.local.entity.ProgressionTargetColumns
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionContext
import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionOutcomeType
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionScheme
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.progression.ProgressionEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProgressionRepositoryImplTest {

    private lateinit var progressionDao: ProgressionDao
    private lateinit var sessionDao: WorkoutSessionDao
    private lateinit var setDao: WorkoutSetDao
    private lateinit var trainingPlanDao: TrainingPlanDao
    private lateinit var engine: ProgressionEngine
    private lateinit var transactionRunner: RecordingTransactionRunner
    private lateinit var repository: ProgressionRepositoryImpl

    private val sessions = linkedMapOf<Long, WorkoutSessionEntity>()
    private val targetsBySession = linkedMapOf<Long, List<WorkoutPlanTargetEntity>>()
    private val setsBySession = linkedMapOf<Long, List<WorkoutSetEntity>>()
    private val previousTargetsByPosition = mutableMapOf<Triple<Long, Long, Int>, List<WorkoutPlanTargetEntity>>()
    private val missingBefore = mutableMapOf<Pair<Long, Long>, List<Long>>()
    private val requestedTargetSessionIds = mutableListOf<Long>()
    private val previousTargetBounds = mutableListOf<Pair<Long, Long>>()
    private val suggestionQueries = mutableListOf<List<Long>>()
    private val suggestions = mutableListOf<ProgressionSuggestionEntity>()
    private val planExercises = mutableListOf<PlanExerciseEntity>()
    private val contexts = mutableListOf<ProgressionContext>()
    private val engineOutcomes = mutableMapOf<Long, ProgressionOutcome>()
    private val pendingRows = MutableStateFlow<List<ProgressionSuggestionEntity>>(emptyList())
    private val sessionRows = MutableStateFlow<List<ProgressionSuggestionEntity>>(emptyList())
    private val pendingCount = MutableStateFlow(0)
    private var missingSessionIds: List<Long> = emptyList()
    private var hydratedSets: List<WorkoutSetEntity> = emptyList()
    private var hydratedSetQueryCount = 0
    private var nextSuggestionId = 1L
    private var failPlanExerciseUpdateForId: Long? = null

    @Before
    fun setUp() {
        progressionDao = mockk(relaxed = true)
        sessionDao = mockk(relaxed = true)
        setDao = mockk(relaxed = true)
        trainingPlanDao = mockk(relaxed = true)
        engine = mockk()
        transactionRunner = RecordingTransactionRunner(
            snapshot = { TransactionSnapshot(suggestions.toList(), planExercises.toList()) },
            restore = { snapshot ->
                suggestions.clear()
                suggestions += snapshot.suggestions
                planExercises.clear()
                planExercises += snapshot.planExercises
            }
        )

        sessions[9] = completedSession(id = 9, endTime = 1_000)

        coEvery { sessionDao.getSessionById(any()) } answers {
            sessions[firstArg<Long>()]
        }
        coEvery { progressionDao.getTargetsForSession(any()) } answers {
            val sessionId = firstArg<Long>()
            requestedTargetSessionIds += sessionId
            targetsBySession[sessionId].orEmpty()
        }
        coEvery { setDao.getSetsForSessionList(any()) } answers {
            setsBySession[firstArg<Long>()].orEmpty()
        }
        coEvery {
            progressionDao.getPreviousTargets(any(), any(), any(), any(), any())
        } answers {
            val planId = firstArg<Long>()
            val exerciseId = secondArg<Long>()
            val orderIndex = arg<Int>(2)
            val endTime = arg<Long>(3)
            val sessionId = arg<Long>(4)
            previousTargetBounds += endTime to sessionId
            previousTargetsByPosition[Triple(planId, exerciseId, orderIndex)].orEmpty()
        }
        coEvery { progressionDao.getSuggestionsForTargetIds(any()) } answers {
            val ids = firstArg<List<Long>>()
            suggestionQueries += ids
            suggestions.filter { it.sourceTargetSnapshotId in ids }
        }
        coEvery { progressionDao.insertSuggestion(any()) } answers {
            val candidate = firstArg<ProgressionSuggestionEntity>()
            val duplicate = suggestions.any {
                it.sourceTargetSnapshotId == candidate.sourceTargetSnapshotId &&
                    it.sourceProgression.ruleRevision == candidate.sourceProgression.ruleRevision
            }
            if (duplicate) {
                -1L
            } else {
                val id = nextSuggestionId++
                suggestions += candidate.copy(id = id)
                id
            }
        }
        coEvery { progressionDao.getSuggestionsByIds(any()) } answers {
            val ids = firstArg<Set<Long>>()
            suggestions.filter { it.id in ids }.sortedBy { it.id }
        }
        coEvery { progressionDao.getPendingSuggestions() } answers {
            suggestions.filter { it.status == ProgressionSuggestionStatus.PENDING.name }
                .sortedBy { it.id }
        }
        coEvery { progressionDao.updateSuggestion(any()) } answers {
            val updated = firstArg<ProgressionSuggestionEntity>()
            val index = suggestions.indexOfFirst { it.id == updated.id }
            check(index >= 0) { "Missing suggestion ${updated.id}" }
            suggestions[index] = updated
        }
        coEvery { progressionDao.getCompletedSessionIdsWithMissingOutcomes() } answers {
            missingSessionIds
        }
        coEvery {
            progressionDao.getCompletedSessionIdsWithMissingOutcomesBefore(any(), any())
        } answers {
            missingBefore[arg<Long>(0) to arg<Long>(1)].orEmpty()
        }
        every { progressionDao.observePendingSuggestions() } returns pendingRows
        every { progressionDao.observeSuggestionsForSession(any()) } returns sessionRows
        every { progressionDao.observePendingCount() } returns pendingCount
        coEvery { setDao.getSetsByIds(any()) } answers {
            hydratedSetQueryCount += 1
            val ids = firstArg<List<Long>>()
            hydratedSets.filter { it.id in ids }
        }
        coEvery { trainingPlanDao.getPlanExerciseAt(any(), any(), any()) } answers {
            val planId = firstArg<Long>()
            val exerciseId = secondArg<Long>()
            val orderIndex = arg<Int>(2)
            planExercises.singleOrNull {
                it.planId == planId && it.exerciseId == exerciseId && it.orderIndex == orderIndex
            }
        }
        coEvery { trainingPlanDao.updatePlanExerciseTargetsById(any(), any(), any(), any()) } answers {
            val id = firstArg<Long>()
            if (id == failPlanExerciseUpdateForId) {
                throw IllegalStateException("Injected plan exercise update failure for $id")
            }
            val index = planExercises.indexOfFirst { it.id == id }
            if (index < 0) {
                0
            } else {
                planExercises[index] = planExercises[index].copy(
                    targetSets = arg(1),
                    targetReps = arg(2),
                    targetWeightKg = arg(3)
                )
                1
            }
        }
        every { engine.evaluate(any()) } answers {
            val context = firstArg<ProgressionContext>()
            contexts += context
            engineOutcomes[context.sourceTarget.id] ?: ProgressionOutcome.KeepTarget(
                sourceTarget = context.sourceTarget.target,
                reasonCode = ProgressionReasonCode.REPEAT_TARGET,
                streakEffect = ProgressionStreakEffect.RESET,
                countedSetIds = context.setsForTarget.map { it.id }
            )
        }

        repository = newRepository(engine)
    }

    @Test
    fun `generation maps sets by snapshot id and keeps duplicate exercise positions separate`() = runTest {
        targetsBySession[9] = listOf(
            target(id = 41, exerciseId = 7, orderIndex = 0),
            target(id = 42, exerciseId = 7, orderIndex = 1)
        )
        setsBySession[9] = listOf(
            set(id = 1, snapshotId = 41, reps = 8),
            set(id = 2, snapshotId = 42, reps = 5)
        )

        repository.generateOutcomesForSession(9)

        assertEquals(listOf(41L, 42L), suggestions.map { it.sourceTargetSnapshotId })
        assertEquals(listOf(1L), contexts[0].setsForTarget.map { it.id })
        assertEquals(listOf(2L), contexts[1].setsForTarget.map { it.id })
    }

    @Test
    fun `generation is idempotent per snapshot and stored rule revision`() = runTest {
        targetsBySession[9] = listOf(target(id = 41))

        val first = repository.generateOutcomesForSession(9)
        val second = repository.generateOutcomesForSession(9)

        assertEquals(1, first.insertedCount)
        assertEquals(0, second.insertedCount)
        assertEquals(0, second.reviewItemCount)
        assertEquals(1, suggestions.size)
    }

    @Test
    fun `manual targets are not evaluated or stored`() = runTest {
        targetsBySession[9] = listOf(target(id = 41, config = ProgressionConfig.Manual()))

        val result = repository.generateOutcomesForSession(9)

        assertEquals(0, result.insertedCount)
        assertEquals(0, result.reviewItemCount)
        assertEquals(0, result.pendingCount)
        assertTrue(contexts.isEmpty())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `stored invalid and unsupported configurations pass through the engine and persist informational outcomes losslessly`() = runTest {
        val malformed = ProgressionConfigColumns(
            scheme = ProgressionScheme.LINEAR.name,
            incrementValue = null,
            incrementUnit = UnitSystem.METRIC.name,
            incrementKg = 2.5,
            stallThreshold = 4,
            backoffPercent = 12.0,
            ruleRevision = 7
        )
        val unsupported = ProgressionConfigColumns.fromDomain(linearConfig(ruleRevision = 99))
        targetsBySession[9] = listOf(
            target(id = 41, orderIndex = 0, progression = malformed),
            target(id = 42, orderIndex = 1, progression = unsupported)
        )

        val result = newRepository(ProgressionEngine()).generateOutcomesForSession(9)

        assertEquals(2, result.insertedCount)
        assertEquals(0, result.reviewItemCount)
        assertEquals(0, result.pendingCount)
        assertEquals(listOf(malformed, unsupported), suggestions.map { it.sourceProgression })
        assertEquals(
            listOf(
                ProgressionReasonCode.CONFIG_INVALID.name,
                ProgressionReasonCode.RULE_REVISION_UNSUPPORTED.name
            ),
            suggestions.map { it.reasonCode }
        )
        assertTrue(suggestions.all { it.status == ProgressionSuggestionStatus.INFORMATIONAL.name })
    }

    @Test
    fun `generation rereads stored rows for exact review and pending counts`() = runTest {
        targetsBySession[9] = listOf(
            target(id = 41, orderIndex = 0),
            target(id = 42, orderIndex = 1)
        )
        engineOutcomes[41] = ProgressionOutcome.ProposeChange(
            sourceTarget = ProgressionTarget(3, 8, 100.0),
            proposedTarget = ProgressionTarget(3, 8, 102.5),
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            streakEffect = ProgressionStreakEffect.RESET
        )

        val result = repository.generateOutcomesForSession(9)

        assertEquals(2, result.insertedCount)
        assertEquals(1, result.reviewItemCount)
        assertEquals(1, result.pendingCount)
        assertEquals(
            listOf(ProgressionSuggestionStatus.PENDING.name, ProgressionSuggestionStatus.INFORMATIONAL.name),
            suggestions.map { it.status }
        )
    }

    @Test
    fun `missing active and free sessions return zero without evaluating targets`() = runTest {
        sessions[10] = completedSession(id = 10, endTime = 2_000, planId = null)
        sessions[11] = completedSession(id = 11, endTime = 3_000).copy(endTime = null)

        val missing = repository.generateOutcomesForSession(404)
        val free = repository.generateOutcomesForSession(10)
        val active = repository.generateOutcomesForSession(11)

        assertEquals(listOf(0, 0, 0), listOf(missing, free, active).map { it.insertedCount })
        assertTrue(requestedTargetSessionIds.isEmpty())
        assertTrue(contexts.isEmpty())
    }

    @Test
    fun `retry processes completed missing sessions in query order and counts positive inserts`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[9] = listOf(target(id = 41, sessionId = 9))
        targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
        suggestions += suggestion(id = 1, sourceTargetSnapshotId = 41, sourceSessionId = 9)
        missingSessionIds = listOf(9, 12)

        val insertedCount = repository.generateMissingOutcomes()

        assertEquals(1, insertedCount)
        assertEquals(listOf(9L, 12L), requestedTargetSessionIds)
        assertEquals(listOf(41L, 42L), suggestions.map { it.sourceTargetSnapshotId })
    }

    @Test
    fun `current plan edits never participate in generation`() = runTest {
        targetsBySession[9] = listOf(target(id = 41, weightKg = 100.0))

        repository.generateOutcomesForSession(9)

        assertEquals(100.0, contexts.single().sourceTarget.target.weightKg, 0.0)
        coVerify(exactly = 0) { trainingPlanDao.getPlanExerciseAt(any(), any(), any()) }
    }

    @Test
    fun `retry history uses workout completion bounds rather than generation time`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[9] = listOf(target(id = 41, sessionId = 9))
        targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
        missingSessionIds = listOf(9, 12)

        repository.generateMissingOutcomes()

        assertEquals(listOf(9L, 12L), requestedTargetSessionIds)
        assertTrue(1_000L to 9L in previousTargetBounds)
        assertTrue(2_000L to 12L in previousTargetBounds)
    }

    @Test
    fun `an intervening manual target cuts off older otherwise comparable failure history`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[12] = listOf(target(id = 43, sessionId = 12, weightKg = 100.0))
        previousTargetsByPosition[Triple(2, 7, 0)] = listOf(
            target(id = 42, sessionId = 9, weightKg = 100.0, config = ProgressionConfig.Manual()),
            target(id = 41, sessionId = 8, weightKg = 100.0)
        )
        suggestions += suggestion(
            id = 1,
            sourceSessionId = 8,
            sourceTargetSnapshotId = 41,
            streak = ProgressionStreakEffect.INCREMENT
        )

        repository.generateOutcomesForSession(12)

        assertTrue(contexts.single().previousComparableOutcomesNewestFirst.isEmpty())
        assertFalse(suggestionQueries.any { 41L in it })
    }

    @Test
    fun `raw malformed config changes cut history even when compact invalid domains are equal`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        val currentRaw = ProgressionConfigColumns(
            scheme = ProgressionScheme.LINEAR.name,
            stallThreshold = 2,
            ruleRevision = 7
        )
        val interveningRaw = currentRaw.copy(stallThreshold = 3)
        targetsBySession[12] = listOf(target(id = 43, sessionId = 12, progression = currentRaw))
        previousTargetsByPosition[Triple(2, 7, 0)] = listOf(
            target(id = 42, sessionId = 9, progression = interveningRaw),
            target(id = 41, sessionId = 8, progression = currentRaw)
        )
        suggestions += suggestion(
            id = 1,
            sourceSessionId = 8,
            sourceTargetSnapshotId = 41,
            sourceProgression = currentRaw,
            streak = ProgressionStreakEffect.INCREMENT
        )

        newRepository(ProgressionEngine()).generateOutcomesForSession(12)

        assertFalse(suggestionQueries.any { 41L in it })
        assertEquals(ProgressionReasonCode.CONFIG_INVALID.name, suggestions.last().reasonCode)
    }

    @Test
    fun `direct generation catches up older missing sessions before the requested session`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[9] = listOf(target(id = 41, sessionId = 9))
        targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
        missingBefore[2_000L to 12L] = listOf(9)

        repository.generateOutcomesForSession(12)

        assertEquals(listOf(9L, 12L), requestedTargetSessionIds)
        assertEquals(listOf(41L, 42L), suggestions.map { it.sourceTargetSnapshotId })
    }

    @Test
    fun `failed older catchup aborts before the requested session is evaluated`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[9] = listOf(target(id = 41, sessionId = 9))
        targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
        setsBySession[9] = listOf(set(id = 1, sessionId = 9, snapshotId = 999))
        missingBefore[2_000L to 12L] = listOf(9)

        val failure = captureFailure { repository.generateOutcomesForSession(12) }

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(9L), requestedTargetSessionIds)
        assertTrue(contexts.isEmpty())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `comparable history requires one outcome at the target stored revision`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
        previousTargetsByPosition[Triple(2, 7, 0)] = listOf(target(id = 41, sessionId = 9))
        suggestions += suggestion(id = 1, sourceTargetSnapshotId = 41, sourceProgression = linearColumns(2))

        val failure = captureFailure { repository.generateOutcomesForSession(12) }

        assertTrue(failure is IllegalStateException)
        assertTrue(contexts.isEmpty())
        assertEquals(1, suggestions.size)
    }

    @Test
    fun `comparable history rejects duplicate outcomes at the stored revision`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
        previousTargetsByPosition[Triple(2, 7, 0)] = listOf(target(id = 41, sessionId = 9))
        suggestions += suggestion(id = 1, sourceTargetSnapshotId = 41)
        suggestions += suggestion(id = 2, sourceTargetSnapshotId = 41)

        val failure = captureFailure { repository.generateOutcomesForSession(12) }

        assertTrue(failure is IllegalStateException)
        assertTrue(contexts.isEmpty())
        assertEquals(2, suggestions.size)
    }

    @Test
    fun `comparable history rejects duplicated source columns that differ from the target row`() = runTest {
        sessions[12] = completedSession(id = 12, endTime = 2_000)
        targetsBySession[12] = listOf(target(id = 42, sessionId = 12))
        previousTargetsByPosition[Triple(2, 7, 0)] = listOf(target(id = 41, sessionId = 9))
        suggestions += suggestion(id = 1, sourceTargetSnapshotId = 41, sourceWeightKg = 140.0)

        val failure = captureFailure { repository.generateOutcomesForSession(12) }

        assertTrue(failure is IllegalStateException)
        assertTrue(contexts.isEmpty())
        assertEquals(1, suggestions.size)
    }

    @Test
    fun `dangling snapshot links fail before any engine call or partial insert`() = runTest {
        targetsBySession[9] = listOf(
            target(id = 41, orderIndex = 0),
            target(id = 42, orderIndex = 1)
        )
        setsBySession[9] = listOf(
            set(id = 1, snapshotId = 41),
            set(id = 2, snapshotId = 999)
        )

        val failure = captureFailure { repository.generateOutcomesForSession(9) }

        assertTrue(failure is IllegalStateException)
        assertTrue(contexts.isEmpty())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `cross-session snapshot links fail closed`() = runTest {
        targetsBySession[9] = listOf(target(id = 41, sessionId = 9))
        setsBySession[9] = listOf(set(id = 1, sessionId = 12, snapshotId = 41))

        val failure = captureFailure { repository.generateOutcomesForSession(9) }

        assertTrue(failure is IllegalStateException)
        assertTrue(contexts.isEmpty())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `cross-exercise snapshot links fail closed`() = runTest {
        targetsBySession[9] = listOf(target(id = 41, exerciseId = 7))
        setsBySession[9] = listOf(set(id = 1, exerciseId = 8, snapshotId = 41))

        val failure = captureFailure { repository.generateOutcomesForSession(9) }

        assertTrue(failure is IllegalStateException)
        assertTrue(contexts.isEmpty())
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `null snapshot ad hoc sets never enter an engine context`() = runTest {
        targetsBySession[9] = listOf(target(id = 41))
        setsBySession[9] = listOf(
            set(id = 1, snapshotId = null),
            set(id = 2, snapshotId = 41)
        )

        repository.generateOutcomesForSession(9)

        assertEquals(listOf(2L), contexts.single().setsForTarget.map { it.id })
        assertEquals("[2]", suggestions.single().countedSetIdsJson)
    }

    @Test
    fun `generation rejects foreign engine evidence before any suggestion is persisted`() = runTest {
        targetsBySession[9] = listOf(
            target(id = 41, orderIndex = 0),
            target(id = 42, orderIndex = 1)
        )
        setsBySession[9] = listOf(
            set(id = 1, snapshotId = 41),
            set(id = 2, snapshotId = 42)
        )
        engineOutcomes[41] = ProgressionOutcome.KeepTarget(
            sourceTarget = ProgressionTarget(3, 8, 100.0),
            reasonCode = ProgressionReasonCode.REPEAT_TARGET,
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = listOf(2)
        )

        val failure = captureFailure { repository.generateOutcomesForSession(9) }

        assertTrue(failure is IllegalStateException)
        assertEquals(1, transactionRunner.transactionCount)
        assertFalse(transactionRunner.inTransaction)
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `single-session generation runs inside one transaction`() = runTest {
        targetsBySession[9] = listOf(target(id = 41))

        repository.generateOutcomesForSession(9)

        assertEquals(1, transactionRunner.transactionCount)
        assertFalse(transactionRunner.inTransaction)
    }

    @Test
    fun `generation stores canonical reason keys and preserves evidence id order`() = runTest {
        targetsBySession[9] = listOf(target(id = 41))
        setsBySession[9] = listOf(
            set(id = 30, setNumber = 1, snapshotId = 41),
            set(id = 10, setNumber = 2, snapshotId = 41),
            set(id = 20, setNumber = 3, snapshotId = 41)
        )
        engineOutcomes[41] = ProgressionOutcome.KeepTarget(
            sourceTarget = ProgressionTarget(3, 8, 100.0),
            reasonCode = ProgressionReasonCode.REPEAT_TARGET,
            reasonArguments = linkedMapOf("z" to 2.0, "a" to 1.0),
            streakEffect = ProgressionStreakEffect.RESET,
            countedSetIds = listOf(30, 10, 20)
        )

        repository.generateOutcomesForSession(9)

        assertEquals("{\"a\":1.0,\"z\":2.0}", suggestions.single().reasonArgumentsJson)
        assertEquals("[30,10,20]", suggestions.single().countedSetIdsJson)
    }

    @Test
    fun `review rows hydrate counted sets in stored evidence order`() = runTest {
        pendingRows.value = listOf(suggestion(id = 1, countedSetIdsJson = "[30,10,20]"))
        hydratedSets = listOf(
            set(id = 10, snapshotId = 41),
            set(id = 20, snapshotId = 41),
            set(id = 30, snapshotId = 41)
        )

        val item = repository.observeReviewItems(null).first().single()

        assertEquals(listOf(30L, 10L, 20L), item.countedSets.map { it.id })
        assertEquals(listOf(30L, 10L, 20L), item.outcome.countedSetIds)
    }

    @Test
    fun `review skips a missing evidence set but keeps the suggestion with the surviving sets`() = runTest {
        pendingRows.value = listOf(suggestion(id = 1, countedSetIdsJson = "[10,20]"))
        hydratedSets = listOf(set(id = 10, snapshotId = 41))

        val item = repository.observeReviewItems(null).first().single()

        assertEquals(listOf(10L), item.countedSets.map { it.id })
        assertEquals(listOf(10L), item.outcome.countedSetIds)
    }

    @Test
    fun `review skips suggestions whose hydrated evidence identity differs from the stored source`() = runTest {
        pendingRows.value = listOf(suggestion(id = 1, countedSetIdsJson = "[10]"))
        hydratedSets = listOf(set(id = 10, exerciseId = 8, snapshotId = 41))

        val items = repository.observeReviewItems(null).first()

        assertTrue(items.isEmpty())
    }

    @Test
    fun `review skips rows with duplicate or non-positive evidence ids without a set query`() = runTest {
        pendingRows.value = listOf(suggestion(id = 1, countedSetIdsJson = "[10,10]"))
        val duplicateItems = repository.observeReviewItems(null).first()
        pendingRows.value = listOf(suggestion(id = 2, countedSetIdsJson = "[0]"))
        val nonPositiveItems = repository.observeReviewItems(null).first()

        assertTrue(duplicateItems.isEmpty())
        assertTrue(nonPositiveItems.isEmpty())
        assertEquals(0, hydratedSetQueryCount)
    }

    @Test
    fun `review skips rows with malformed non-canonical or non-finite stored JSON`() = runTest {
        pendingRows.value = listOf(suggestion(id = 1, countedSetIdsJson = "not-json"))
        val malformed = repository.observeReviewItems(null).first()
        pendingRows.value = listOf(suggestion(id = 2, countedSetIdsJson = "[10] "))
        val nonCanonical = repository.observeReviewItems(null).first()
        pendingRows.value = listOf(
            suggestion(id = 3, reasonArgumentsJson = "{\"value\":1e999}")
        )
        val nonFinite = repository.observeReviewItems(null).first()

        assertTrue(malformed.isEmpty())
        assertTrue(nonCanonical.isEmpty())
        assertTrue(nonFinite.isEmpty())
    }

    @Test
    fun `review skips rows with unknown persisted enum values`() = runTest {
        val base = suggestion(id = 1)
        val rows = listOf(
            base.copy(outcomeType = "FUTURE_OUTCOME"),
            base.copy(reasonCode = "FUTURE_REASON"),
            base.copy(streakEffect = "FUTURE_STREAK"),
            base.copy(status = "FUTURE_STATUS")
        )

        val results = rows.map { row ->
            pendingRows.value = listOf(row)
            repository.observeReviewItems(null).first()
        }

        assertTrue(results.all { it.isEmpty() })
    }

    @Test
    fun `review skips contradictory outcome rows`() = runTest {
        val missingProposal = suggestion(id = 1).copy(
            outcomeType = ProgressionOutcomeType.PROPOSE_CHANGE.name,
            suggestedTarget = null
        )
        val unexpectedProposal = suggestion(id = 2).copy(
            outcomeType = ProgressionOutcomeType.KEEP_TARGET.name,
            suggestedTarget = ProgressionTargetColumns(3, 8, 102.5)
        )

        pendingRows.value = listOf(missingProposal)
        val missing = repository.observeReviewItems(null).first()
        pendingRows.value = listOf(unexpectedProposal)
        val unexpected = repository.observeReviewItems(null).first()

        assertTrue(missing.isEmpty())
        assertTrue(unexpected.isEmpty())
    }

    @Test
    fun `review with empty evidence bypasses the set id query`() = runTest {
        pendingRows.value = listOf(suggestion(id = 1, countedSetIdsJson = "[]"))

        val item = repository.observeReviewItems(null).first().single()

        assertTrue(item.countedSets.isEmpty())
        assertEquals(0, hydratedSetQueryCount)
    }

    @Test
    fun `single acceptance updates the exact plan position and records final target`() = runTest {
        seedPending(
            id = 1,
            planId = 3,
            exerciseId = 7,
            orderIndex = 1,
            sourceWeight = 100.0,
            proposedWeight = 102.5
        )
        planExercises += matchingPlanExercise(
            id = 11,
            planId = 3,
            exerciseId = 7,
            orderIndex = 1,
            weight = 100.0
        )
        planExercises += matchingPlanExercise(
            id = 12,
            planId = 4,
            exerciseId = 7,
            orderIndex = 1,
            weight = 70.0
        )

        val result = repository.acceptSuggestions(
            mapOf(1L to ProgressionTarget(3, 8, 102.5))
        )

        assertEquals(ProgressionDecisionResult.Accepted(setOf(1L)), result)
        assertEquals(102.5, planExercises.single { it.id == 11L }.targetWeightKg, 0.0)
        assertEquals(70.0, planExercises.single { it.id == 12L }.targetWeightKg, 0.0)
        assertEquals(ProgressionSuggestionStatus.ACCEPTED.name, suggestions.single().status)
        assertFalse(suggestions.single().wasEdited)
        assertEquals(ProgressionTargetColumns(3, 8, 102.5), suggestions.single().finalTarget)
        assertEquals(7_000L, suggestions.single().decidedAtEpochMillis)
        assertEquals(1, transactionRunner.transactionCount)
    }

    @Test
    fun `edited acceptance records the actual final target`() = runTest {
        seedPending(id = 1, sourceWeight = 100.0, proposedWeight = 102.5)
        planExercises += matchingPlanExercise(weight = 100.0)

        val result = repository.acceptSuggestions(
            mapOf(1L to ProgressionTarget(4, 6, 103.0))
        )

        assertEquals(ProgressionDecisionResult.Accepted(setOf(1L)), result)
        assertEquals(4, planExercises.single().targetSets)
        assertEquals(6, planExercises.single().targetReps)
        assertEquals(103.0, planExercises.single().targetWeightKg, 0.0)
        assertTrue(suggestions.single().wasEdited)
        assertEquals(ProgressionTargetColumns(4, 6, 103.0), suggestions.single().finalTarget)
    }

    @Test
    fun `batch conflict changes no plan row and marks conflicting suggestions stale`() = runTest {
        seedPending(id = 1, orderIndex = 0, sourceWeight = 100.0, proposedWeight = 102.5)
        seedPending(id = 2, orderIndex = 1, sourceWeight = 80.0, proposedWeight = 82.5)
        planExercises += matchingPlanExercise(id = 11, orderIndex = 0, weight = 100.0)
        planExercises += matchingPlanExercise(id = 12, orderIndex = 1, weight = 90.0)

        val result = repository.acceptSuggestions(
            mapOf(
                1L to ProgressionTarget(3, 8, 102.5),
                2L to ProgressionTarget(3, 8, 82.5)
            )
        )

        assertEquals(ProgressionDecisionResult.Stale(setOf(2L)), result)
        assertEquals(listOf(100.0, 90.0), planExercises.map { it.targetWeightKg })
        assertEquals(
            listOf(ProgressionSuggestionStatus.PENDING.name, ProgressionSuggestionStatus.STALE.name),
            suggestions.map { it.status }
        )
        assertEquals(listOf(null, 7_000L), suggestions.map { it.decidedAtEpochMillis })
    }

    @Test
    fun `invalid edited target changes neither plan nor status`() = runTest {
        seedPending(id = 1, sourceWeight = 100.0, proposedWeight = 102.5)
        planExercises += matchingPlanExercise(weight = 100.0)

        val result = repository.acceptSuggestions(
            mapOf(1L to ProgressionTarget(0, 8, Double.NaN))
        )

        assertTrue(result is ProgressionDecisionResult.Invalid)
        result as ProgressionDecisionResult.Invalid
        assertTrue("target.sets" in result.message)
        assertTrue("target.weightKg" in result.message)
        assertEquals(100.0, planExercises.single().targetWeightKg, 0.0)
        assertEquals(ProgressionSuggestionStatus.PENDING.name, suggestions.single().status)
        assertEquals(null, suggestions.single().decidedAtEpochMillis)
    }

    @Test
    fun `all final targets are validated before the first plan write`() = runTest {
        seedPending(id = 1, orderIndex = 0, sourceWeight = 100.0, proposedWeight = 102.5)
        seedPending(id = 2, orderIndex = 1, sourceWeight = 80.0, proposedWeight = 82.5)
        planExercises += matchingPlanExercise(id = 11, orderIndex = 0, weight = 100.0)
        planExercises += matchingPlanExercise(id = 12, orderIndex = 1, weight = 80.0)

        val result = repository.acceptSuggestions(
            mapOf(
                1L to ProgressionTarget(3, 8, 102.5),
                2L to ProgressionTarget(3, 0, 82.5)
            )
        )

        assertTrue(result is ProgressionDecisionResult.Invalid)
        assertEquals(listOf(100.0, 80.0), planExercises.map { it.targetWeightKg })
        assertEquals(
            listOf(ProgressionSuggestionStatus.PENDING.name, ProgressionSuggestionStatus.PENDING.name),
            suggestions.map { it.status }
        )
    }

    @Test
    fun `batch rejects two suggestions for the same current plan position`() = runTest {
        seedPending(
            id = 1,
            sourceSessionId = 9,
            orderIndex = 0,
            sourceWeight = 100.0,
            proposedWeight = 102.5
        )
        seedPending(
            id = 2,
            sourceSessionId = 12,
            orderIndex = 0,
            sourceWeight = 100.0,
            proposedWeight = 105.0
        )
        planExercises += matchingPlanExercise(orderIndex = 0, weight = 100.0)

        val result = repository.acceptSuggestions(
            mapOf(
                1L to ProgressionTarget(3, 8, 102.5),
                2L to ProgressionTarget(3, 8, 105.0)
            )
        )

        assertTrue(result is ProgressionDecisionResult.Invalid)
        assertEquals(100.0, planExercises.single().targetWeightKg, 0.0)
        assertEquals(
            listOf(ProgressionSuggestionStatus.PENDING.name, ProgressionSuggestionStatus.PENDING.name),
            suggestions.map { it.status }
        )
    }

    @Test
    fun `empty acceptance selection is invalid without changing stored state`() = runTest {
        seedPending(id = 1)
        planExercises += matchingPlanExercise()

        val result = repository.acceptSuggestions(emptyMap())

        assertTrue(result is ProgressionDecisionResult.Invalid)
        assertEquals(100.0, planExercises.single().targetWeightKg, 0.0)
        assertEquals(ProgressionSuggestionStatus.PENDING.name, suggestions.single().status)
    }

    @Test
    fun `acceptance rejects a selected suggestion that is no longer pending`() = runTest {
        seedPending(id = 1)
        suggestions[0] = suggestions[0].copy(
            status = ProgressionSuggestionStatus.REJECTED.name,
            decidedAtEpochMillis = 6_500L
        )
        planExercises += matchingPlanExercise()

        val decidedResult = repository.acceptSuggestions(
            mapOf(1L to ProgressionTarget(3, 8, 102.5))
        )

        assertTrue(decidedResult is ProgressionDecisionResult.Invalid)
        assertEquals(100.0, planExercises.single().targetWeightKg, 0.0)
        assertEquals(ProgressionSuggestionStatus.REJECTED.name, suggestions.single().status)
        assertEquals(6_500L, suggestions.single().decidedAtEpochMillis)
    }

    @Test
    fun `acceptance rejects an unknown selected suggestion`() = runTest {
        planExercises += matchingPlanExercise()

        val result = repository.acceptSuggestions(
            mapOf(404L to ProgressionTarget(3, 8, 102.5))
        )

        assertTrue(result is ProgressionDecisionResult.Invalid)
        assertEquals(100.0, planExercises.single().targetWeightKg, 0.0)
        assertTrue(suggestions.isEmpty())
    }

    @Test
    fun `acceptance rejects a pending non proposal outcome`() = runTest {
        suggestions += suggestion(id = 1).copy(status = ProgressionSuggestionStatus.PENDING.name)
        planExercises += matchingPlanExercise()

        val result = repository.acceptSuggestions(
            mapOf(1L to ProgressionTarget(3, 8, 102.5))
        )

        assertTrue(result is ProgressionDecisionResult.Invalid)
        assertEquals(100.0, planExercises.single().targetWeightKg, 0.0)
        assertEquals(ProgressionSuggestionStatus.PENDING.name, suggestions.single().status)
    }

    @Test
    fun `acceptance rejects a proposal with no stored target`() = runTest {
        suggestions += suggestion(
            id = 1,
            outcomeType = ProgressionOutcomeType.PROPOSE_CHANGE
        ).copy(status = ProgressionSuggestionStatus.PENDING.name)
        planExercises += matchingPlanExercise()

        val result = repository.acceptSuggestions(
            mapOf(1L to ProgressionTarget(3, 8, 102.5))
        )

        assertTrue(result is ProgressionDecisionResult.Invalid)
        assertEquals(100.0, planExercises.single().targetWeightKg, 0.0)
        assertEquals(ProgressionSuggestionStatus.PENDING.name, suggestions.single().status)
    }

    @Test
    fun `mid batch write failure rolls back plan targets and decisions`() = runTest {
        seedPending(id = 1, orderIndex = 0, sourceWeight = 100.0, proposedWeight = 102.5)
        seedPending(id = 2, orderIndex = 1, sourceWeight = 80.0, proposedWeight = 82.5)
        planExercises += matchingPlanExercise(id = 11, orderIndex = 0, weight = 100.0)
        planExercises += matchingPlanExercise(id = 12, orderIndex = 1, weight = 80.0)
        failPlanExerciseUpdateForId = 12

        val failure = captureFailure {
            repository.acceptSuggestions(
                mapOf(
                    1L to ProgressionTarget(3, 8, 102.5),
                    2L to ProgressionTarget(3, 8, 82.5)
                )
            )
        }

        assertTrue(failure is IllegalStateException)
        assertEquals(listOf(100.0, 80.0), planExercises.map { it.targetWeightKg })
        assertEquals(
            listOf(ProgressionSuggestionStatus.PENDING.name, ProgressionSuggestionStatus.PENDING.name),
            suggestions.map { it.status }
        )
        assertEquals(listOf(null, null), suggestions.map { it.decidedAtEpochMillis })
    }

    @Test
    fun `reconcile marks disabled or raw edited schemes stale and leaves matches pending`() = runTest {
        seedPending(id = 1, orderIndex = 0, sourceWeight = 100.0)
        seedPending(id = 2, orderIndex = 1, sourceWeight = 80.0)
        seedPending(id = 3, orderIndex = 2, sourceWeight = 60.0)
        planExercises += matchingPlanExercise(
            id = 11,
            orderIndex = 0,
            weight = 100.0,
            config = ProgressionConfig.Manual()
        )
        planExercises += matchingPlanExercise(
            id = 12,
            orderIndex = 1,
            weight = 80.0,
            progression = linearColumns().copy(backoffPercent = 11.0)
        )
        planExercises += matchingPlanExercise(id = 13, orderIndex = 2, weight = 60.0)

        val staleIds = repository.reconcileOutstandingSuggestions()

        assertEquals(setOf(1L, 2L), staleIds)
        assertEquals(
            listOf(
                ProgressionSuggestionStatus.STALE.name,
                ProgressionSuggestionStatus.STALE.name,
                ProgressionSuggestionStatus.PENDING.name
            ),
            suggestions.map { it.status }
        )
        assertEquals(listOf(7_000L, 7_000L, null), suggestions.map { it.decidedAtEpochMillis })
    }

    @Test
    fun `rejection is idempotent and never reads the current plan`() = runTest {
        var now = 7_000L
        repository = newRepository(engine, nowEpochMillis = { now++ })
        seedPending(id = 1)
        planExercises += matchingPlanExercise()

        repository.rejectSuggestion(1)
        repository.rejectSuggestion(1)

        assertEquals(ProgressionSuggestionStatus.REJECTED.name, suggestions.single().status)
        assertEquals(7_000L, suggestions.single().decidedAtEpochMillis)
        coVerify(exactly = 0) { trainingPlanDao.getPlanExerciseAt(any(), any(), any()) }
        coVerify(exactly = 0) { trainingPlanDao.updatePlanExerciseTargetsById(any(), any(), any(), any()) }
    }

    private fun newRepository(
        progressionEngine: ProgressionEngine,
        nowEpochMillis: () -> Long = { 7_000L }
    ) = ProgressionRepositoryImpl(
        progressionDao = progressionDao,
        sessionDao = sessionDao,
        setDao = setDao,
        trainingPlanDao = trainingPlanDao,
        engine = progressionEngine,
        transactionRunner = transactionRunner,
        nowEpochMillis = nowEpochMillis
    )

    private fun seedPending(
        id: Long,
        sourceSessionId: Long = 9,
        planId: Long = 3,
        exerciseId: Long = 7,
        orderIndex: Int = 0,
        sourceSets: Int = 3,
        sourceReps: Int = 8,
        sourceWeight: Double = 100.0,
        proposedSets: Int = sourceSets,
        proposedReps: Int = sourceReps,
        proposedWeight: Double = sourceWeight + 2.5,
        sourceProgression: ProgressionConfigColumns = linearColumns()
    ) {
        suggestions += suggestion(
            id = id,
            sourceSessionId = sourceSessionId,
            sourceWeightKg = sourceWeight,
            sourceProgression = sourceProgression,
            outcomeType = ProgressionOutcomeType.PROPOSE_CHANGE
        ).copy(
            planId = planId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            sourceTarget = ProgressionTargetColumns(sourceSets, sourceReps, sourceWeight),
            suggestedTarget = ProgressionTargetColumns(proposedSets, proposedReps, proposedWeight),
            status = ProgressionSuggestionStatus.PENDING.name
        )
    }

    private fun matchingPlanExercise(
        id: Long = 11,
        planId: Long = 3,
        exerciseId: Long = 7,
        orderIndex: Int = 0,
        sets: Int = 3,
        reps: Int = 8,
        weight: Double = 100.0,
        config: ProgressionConfig = linearConfig(),
        progression: ProgressionConfigColumns = ProgressionConfigColumns.fromDomain(config)
    ) = PlanExerciseEntity(
        id = id,
        planId = planId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        targetSets = sets,
        targetReps = reps,
        targetWeightKg = weight,
        progression = progression
    )

    private fun target(
        id: Long,
        sessionId: Long = 9,
        planId: Long = 2,
        exerciseId: Long = 7,
        orderIndex: Int = 0,
        weightKg: Double = 100.0,
        config: ProgressionConfig = linearConfig(),
        progression: ProgressionConfigColumns = ProgressionConfigColumns.fromDomain(config)
    ) = WorkoutPlanTargetEntity(
        id = id,
        sessionId = sessionId,
        planId = planId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        supersetGroupId = null,
        target = ProgressionTargetColumns(sets = 3, reps = 8, weightKg = weightKg),
        progression = progression
    )

    private fun set(
        id: Long,
        sessionId: Long = 9,
        exerciseId: Long = 7,
        setNumber: Int = 1,
        reps: Int = 8,
        snapshotId: Long? = 41
    ) = WorkoutSetEntity(
        id = id,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weightKg = 100.0,
        completedAt = 500L + id,
        planTargetSnapshotId = snapshotId
    )

    private fun completedSession(
        id: Long,
        endTime: Long,
        planId: Long? = 2
    ) = WorkoutSessionEntity(
        id = id,
        startTime = endTime - 500,
        endTime = endTime,
        planId = planId
    )

    private fun suggestion(
        id: Long,
        sourceSessionId: Long = 9,
        sourceTargetSnapshotId: Long = 41,
        sourceWeightKg: Double = 100.0,
        sourceProgression: ProgressionConfigColumns = linearColumns(),
        outcomeType: ProgressionOutcomeType = ProgressionOutcomeType.KEEP_TARGET,
        reasonArgumentsJson: String = "{}",
        countedSetIdsJson: String = "[]",
        streak: ProgressionStreakEffect = ProgressionStreakEffect.RESET
    ) = ProgressionSuggestionEntity(
        id = id,
        sourceSessionId = sourceSessionId,
        sourceTargetSnapshotId = sourceTargetSnapshotId,
        planId = 2,
        exerciseId = 7,
        orderIndex = 0,
        supersetGroupId = null,
        sourceTarget = ProgressionTargetColumns(3, 8, sourceWeightKg),
        sourceProgression = sourceProgression,
        outcomeType = outcomeType.name,
        reasonCode = ProgressionReasonCode.REPEAT_TARGET.name,
        reasonArgumentsJson = reasonArgumentsJson,
        countedSetIdsJson = countedSetIdsJson,
        streakEffect = streak.name,
        suggestedTarget = null,
        status = ProgressionSuggestionStatus.INFORMATIONAL.name,
        wasEdited = false,
        finalTarget = null,
        createdAtEpochMillis = 6_000,
        decidedAtEpochMillis = null
    )

    private fun linearConfig(ruleRevision: Int = 1) = ProgressionConfig.Linear(
        step = WeightStep(2.5, UnitSystem.METRIC, 2.5),
        failurePolicy = FailurePolicy(),
        ruleRevision = ruleRevision
    )

    private fun linearColumns(ruleRevision: Int = 1) =
        ProgressionConfigColumns.fromDomain(linearConfig(ruleRevision))

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (throwable: Throwable) {
        throwable
    }

    private data class TransactionSnapshot(
        val suggestions: List<ProgressionSuggestionEntity>,
        val planExercises: List<PlanExerciseEntity>
    )

    private class RecordingTransactionRunner(
        private val snapshot: () -> TransactionSnapshot,
        private val restore: (TransactionSnapshot) -> Unit
    ) : TransactionRunner {
        var transactionCount = 0
            private set
        var inTransaction = false
            private set

        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            check(!inTransaction)
            transactionCount += 1
            inTransaction = true
            val before = snapshot()
            return try {
                block()
            } catch (failure: Throwable) {
                restore(before)
                throw failure
            } finally {
                inTransaction = false
            }
        }
    }
}
