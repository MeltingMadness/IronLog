package com.ironlog.shared.store

import com.ironlog.shared.backup.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ProgressionReliabilityTest {
    @Test
    fun newerWorkBlocksAnOlderProposalEvenBeforeItsEvaluation() = runTest {
        for (scheme in listOf("LINEAR", "DOUBLE")) {
            val store = fixture(scheme = scheme)
            val lifecycle = ProgressionLifecycle(store)
            lifecycle.generateForFinishedSession(20)
            val old = store.snapshot().progressionSuggestions.single()
            appendSession(store, id = 21, reps = 7)

            val result = lifecycle.acceptSuggestion(old.id, requireNotNull(old.suggestedTarget))

            assertIs<ProgressionDecisionResult.Stale>(result, scheme)
            assertEquals(100.0, store.snapshot().planExercises.single().targetWeightKg)
        }
    }

    @Test
    fun generatingANewerRepeatExpiresTheOldIncrease() = runTest {
        val store = fixture()
        val lifecycle = ProgressionLifecycle(store)
        lifecycle.generateForFinishedSession(20)
        appendSession(store, id = 21, reps = 7)

        lifecycle.generateForFinishedSession(21)

        val rows = store.snapshot().progressionSuggestions
        assertEquals("STALE", rows.single { it.sourceSessionId == 20L }.status)
        assertEquals("REPEAT_TARGET", rows.single { it.sourceSessionId == 21L }.reasonCode)
        assertEquals(0, rows.count { it.status == "PENDING" })
        assertEquals(100.0, store.snapshot().planExercises.single().targetWeightKg)
    }

    @Test
    fun reconciliationExpiresOldOffersWithoutRegeneratingDecisions() = runTest {
        val store = fixture()
        val lifecycle = ProgressionLifecycle(store)
        lifecycle.generateForFinishedSession(20)
        val old = store.snapshot().progressionSuggestions.single()
        appendSession(store, id = 21, reps = 7)

        assertEquals(setOf(old.id), lifecycle.reconcileOutstandingSuggestions())
        assertEquals("STALE", store.snapshot().progressionSuggestions.single().status)
        assertEquals(emptySet(), lifecycle.reconcileOutstandingSuggestions())
    }

    @Test
    fun deloadGeneratesNoProposalOrFailureForEitherScheme() = runTest {
        for (scheme in listOf("LINEAR", "DOUBLE")) {
            val store = fixture(scheme = scheme, deload = true, weight = 85.0)
            val lifecycle = ProgressionLifecycle(store)

            assertEquals(0, lifecycle.generateForFinishedSession(20).insertedCount)
            assertEquals(0, lifecycle.generateMissingOutcomes())
            assertTrue(store.snapshot().progressionSuggestions.isEmpty())
            assertEquals(100.0, store.snapshot().planExercises.single().targetWeightKg)
        }
    }

    @Test
    fun deloadWarmupOnlyAndUnfinishedSessionsDoNotSupersedeRegularWork() = runTest {
        for (kind in listOf("deload", "warmup", "unfinished")) {
            val store = fixture()
            val lifecycle = ProgressionLifecycle(store)
            lifecycle.generateForFinishedSession(20)
            val old = store.snapshot().progressionSuggestions.single()
            appendSession(store, id = 21, deload = kind == "deload",
                setType = if (kind == "warmup") "WARMUP" else "NORMAL",
                finished = kind != "unfinished")

            assertEquals(emptySet(), lifecycle.reconcileOutstandingSuggestions(), kind)
            assertIs<ProgressionDecisionResult.Accepted>(
                lifecycle.acceptSuggestion(old.id, requireNotNull(old.suggestedTarget)), kind)
            assertEquals(102.5, store.snapshot().planExercises.single().targetWeightKg)
        }
    }

    @Test
    fun legacyPendingDeloadProposalCannotBeAccepted() = runTest {
        val store = fixture(weight = 85.0)
        val lifecycle = ProgressionLifecycle(store)
        lifecycle.generateForFinishedSession(20)
        val old = store.snapshot().progressionSuggestions.single()
        // Reproduce a pending row written by the previous release for a deload session.
        store.transact { it.copy(workoutSessions = it.workoutSessions.map { s -> s.copy(isDeload = true) }) }

        assertIs<ProgressionDecisionResult.Stale>(lifecycle.acceptSuggestion(old.id, requireNotNull(old.suggestedTarget)))
        assertEquals(100.0, store.snapshot().planExercises.single().targetWeightKg)
    }

    @Test
    fun deloadDoesNotBreakOrIncrementAComparableFailureStreak() = runTest {
        val store = fixture(reps = 7)
        appendSession(store, id = 21, reps = 7, weight = 85.0, deload = true)
        appendSession(store, id = 22, reps = 7)
        val lifecycle = ProgressionLifecycle(store)

        lifecycle.generateForFinishedSession(22)

        val rows = store.snapshot().progressionSuggestions
        assertTrue(rows.none { it.sourceSessionId == 21L })
        val result = rows.single { it.sourceSessionId == 22L }
        assertEquals("STALL_BACKOFF", result.reasonCode)
        assertEquals(90.0, result.suggestedTarget?.weightKg)
    }

    @Test
    fun acceptedDeloadHistoryIsNotRewrittenOrReverted() = runTest {
        val store = fixture()
        val lifecycle = ProgressionLifecycle(store)
        lifecycle.generateForFinishedSession(20)
        val row = store.snapshot().progressionSuggestions.single()
        lifecycle.acceptSuggestion(row.id, requireNotNull(row.suggestedTarget))
        store.transact { it.copy(workoutSessions = it.workoutSessions.map { s -> s.copy(isDeload = true) }) }
        val before = store.snapshot()

        assertEquals(emptySet(), lifecycle.reconcileOutstandingSuggestions())
        assertEquals(0, lifecycle.generateMissingOutcomes())
        assertEquals(before.progressionSuggestions, store.snapshot().progressionSuggestions)
        assertEquals(before.planExercises, store.snapshot().planExercises)
    }

    @Test
    fun twoSuccessesSurviveDeloadAndBackupRoundTripWithFailureEvidence() = runTest {
        val store = fixture(revision = 2, successes = 2)
        store.transact { state -> state.copy(workoutSets = state.workoutSets.map {
            if (it.setNumber == 3) it.copy(setType = "FAILURE") else it
        }) }
        val lifecycle = ProgressionLifecycle(store)
        lifecycle.generateForFinishedSession(20)
        val first = store.snapshot().progressionSuggestions.single()
        assertEquals("SUCCESS_CONFIRMATION_REQUIRED", first.reasonCode)
        assertEquals(listOf(201L, 202L, 203L), first.countedSetIds)
        appendSession(store, 21, weight = 85.0, deload = true)
        val restored = SharedStateStore.decodeAndUpgradeForPreview(store.exportJson())
        assertEquals(2, restored.planExercises.single().progression.successThreshold)
        assertEquals(first, restored.progressionSuggestions.single())
        appendSession(store, 22)
        lifecycle.generateForFinishedSession(22)
        val second = store.snapshot().progressionSuggestions.single { it.sourceSessionId == 22L }
        assertEquals(2.0, second.reasonArguments["successfulSessions"])
        assertEquals(102.5, second.suggestedTarget?.weightKg)
        assertIs<ProgressionDecisionResult.Accepted>(lifecycle.acceptSuggestion(second.id, requireNotNull(second.suggestedTarget)))
        assertEquals(102.5, store.snapshot().planExercises.single().targetWeightKg)
    }

    @Test
    fun livePlansUpgradeButHistoricalTargetsAndDecisionsKeepTheirRevision() = runTest {
        val store = fixture()
        val lifecycle = ProgressionLifecycle(store)
        lifecycle.generateForFinishedSession(20)
        val before = store.snapshot()
        val upgraded = SharedStateStore.decodeAndUpgradeForPreview(store.exportJson())
        assertEquals(2, upgraded.planExercises.single().progression.ruleRevision)
        assertEquals(1, upgraded.workoutPlanTargets.single().progression.ruleRevision)
        assertEquals(before.progressionSuggestions, upgraded.progressionSuggestions)
    }

    @Test
    fun differentActualLoadAndIncompleteWorkoutBreakSuccessConfirmation() = runTest {
        for (kind in listOf("load", "incomplete", "mixedMiss")) {
            val store = fixture(revision = 2, successes = 2)
            appendSession(store, 21, weight = if (kind == "load") 90.0 else 100.0)
            store.transact { state -> state.copy(workoutSets = state.workoutSets.mapNotNull {
                when {
                    kind == "incomplete" && it.sessionId == 21L && it.setNumber == 3 -> null
                    kind == "mixedMiss" && it.sessionId == 21L && it.setNumber == 3 -> it.copy(reps = 6, weightKg = 105.0)
                    else -> it
                }
            }) }
            appendSession(store, 22)
            ProgressionLifecycle(store).generateForFinishedSession(22)
            val latest = store.snapshot().progressionSuggestions.single { it.sourceSessionId == 22L }
            assertEquals("SUCCESS_CONFIRMATION_REQUIRED", latest.reasonCode, kind)
            assertEquals(1.0, latest.reasonArguments["successfulSessions"], kind)
        }
    }

    private suspend fun fixture(
        scheme: String = "LINEAR", reps: Int = if (scheme == "DOUBLE") 10 else 8,
        weight: Double = 100.0, deload: Boolean = false, revision: Int = 1, successes: Int = 1,
    ): SharedStateStore {
        val persistence = object : SharedStatePersistence {
            private var serialized: String? = null
            override fun read(): String? = serialized
            override fun writeAtomically(serialized: String) { this.serialized = serialized }
        }
        val store = SharedStateStore(persistence, seedExercises = listOf(
            BackupExercise(1, "Bankdruecken", "BRUST", "TRIZEPS", "LANGHANTEL", false)), nowEpochMillis = { 500L })
        val config = BackupProgressionConfig(scheme = scheme, incrementValue = 2.5, ruleRevision = revision, successThreshold = successes,
            incrementUnit = "METRIC", incrementKg = 2.5,
            minReps = if (scheme == "DOUBLE") 8 else null, maxReps = if (scheme == "DOUBLE") 10 else null)
        store.transact { it.copy(trainingPlans = listOf(BackupTrainingPlan(10, "Push", 500)),
            planExercises = listOf(BackupPlanExercise(100, 10, 1, 0,
                targetSets = 3, targetReps = 8, targetWeightKg = 100.0, progression = config))) }
        appendSession(store, 20, reps, weight, deload)
        return store
    }

    private suspend fun appendSession(
        store: SharedStateStore, id: Long, reps: Int = 8, weight: Double = 100.0,
        deload: Boolean = false, setType: String = "NORMAL", finished: Boolean = true,
    ) {
        val start = id * 1_000
        store.transact { state ->
            val plan = state.planExercises.single()
            state.copy(
                workoutSessions = state.workoutSessions + BackupWorkoutSession(id, start,
                    if (finished) start + 500 else null, if (finished) 1 else 0, "Training", "", planId = 10, isDeload = deload),
                workoutPlanTargets = state.workoutPlanTargets + BackupWorkoutPlanTarget(id, id, 10, 1, 0,
                    target = BackupProgressionTarget(3, 8, 100.0), progression = plan.progression),
                workoutSets = state.workoutSets + (1..3).map { number ->
                    BackupWorkoutSet(id * 10 + number, id, 1, number, reps, weight,
                        setType = setType, completedAt = start + number * 100, planTargetSnapshotId = id)
                },
            )
        }
    }
}
