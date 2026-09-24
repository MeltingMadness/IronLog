package com.ironlog.shared.store

import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupWorkoutSet
import com.ironlog.shared.backup.NORMAL_SET_TYPE
import com.ironlog.shared.model.MuscleGroup
import com.ironlog.shared.readinessdata.ReadinessCheckIn
import com.ironlog.shared.readinessdata.ReadinessData
import com.ironlog.shared.readinessdata.SetIntention
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Persistence contract for the readiness side channel inside [SharedStateStore].
 *
 * The portable models, codec, validator and merger have their own suite. This file covers the
 * store boundary instead: identity of an upserted check-in, the missing-set guard, orphan-free
 * deletion of a set or a whole session, export/reload, legacy payloads without the side channel,
 * and the guarantee that a failed persistence write never publishes a new state.
 */
class ReadinessPersistenceTest {

    private val day = LocalDate.parse("2026-09-11")

    private val exercise = BackupExercise(
        id = 1L,
        name = "Bankdruecken",
        primaryMuscleGroup = "BRUST",
        secondaryMuscleGroups = "TRIZEPS",
        category = "LANGHANTEL",
        isCustom = false,
    )

    @Test
    fun checkInUpsertEditAndDeleteAreKeyedByLocalDate() = runTest {
        val store = newStore()

        store.upsertCheckIn(ReadinessCheckIn(localDate = day, sleepQuality = 4, energy = 3))
        val created = store.snapshot().readinessData.checkIns.single()
        assertEquals(day, created.localDate)
        assertEquals(4, created.sleepQuality)
        assertEquals(3, created.energy)

        // An edit is an upsert for the same date: still one row, new values, stable identity.
        store.upsertCheckIn(ReadinessCheckIn(localDate = day, sleepQuality = 2, stress = 5))
        val edited = store.snapshot().readinessData.checkIns.single()
        assertEquals(2, edited.sleepQuality)
        assertEquals(5, edited.stress)
        assertNull(edited.energy, "an edit must not invent a value that was not sent")
        assertEquals(created.recordedAtEpochMillis, edited.recordedAtEpochMillis)

        store.deleteCheckIn(day)
        assertTrue(store.snapshot().readinessData.checkIns.isEmpty())

        // Deleting a date without a check-in is a tolerated no-op, not an error.
        store.deleteCheckIn(day)
        assertTrue(store.snapshot().readinessData.checkIns.isEmpty())
    }

    @Test
    fun intentionWithoutAnExistingSetIsRejected() = runTest {
        val store = newStore()

        assertFailsWith<IllegalArgumentException> {
            store.updateSetIntention(999L, SetIntention.PLANNED_FAILURE)
        }

        assertTrue(store.snapshot().readinessData.setIntentions.isEmpty())
    }

    @Test
    fun deletingSetAndSessionLeavesNoOrphanedIntention() = runTest {
        val store = newStore()
        val sessionId = store.startWorkout(name = "Montag")
        val setId = store.addSet(newSet(sessionId), intention = SetIntention.PLANNED_FAILURE)
        assertEquals(
            SetIntention.PLANNED_FAILURE,
            store.snapshot().readinessData.setIntentions.single().intention,
        )

        // UNKNOWN clears the row instead of persisting a meaningless one.
        store.updateSetIntention(setId, SetIntention.UNKNOWN)
        assertTrue(store.snapshot().readinessData.setIntentions.isEmpty())

        store.updateSetIntention(setId, SetIntention.UNEXPECTED_TARGET_MISS)
        store.deleteSet(setId)
        assertTrue(store.snapshot().readinessData.setIntentions.isEmpty())

        // Removing the whole session must drop the intention together with its set.
        val sessionSetId = store.addSet(
            newSet(sessionId, setNumber = 2),
            intention = SetIntention.PLANNED_FAILURE,
        )
        store.finishWorkout(sessionId)
        store.deleteSession(sessionId)

        assertTrue(store.snapshot().workoutSets.none { it.id == sessionSetId })
        assertTrue(store.snapshot().readinessData.setIntentions.isEmpty())
    }

    @Test
    fun exportAndReloadPreservesCheckInAndIntention() = runTest {
        val origin = newStore()
        val sessionId = origin.startWorkout(name = "Montag")
        val setId = origin.addSet(newSet(sessionId), intention = SetIntention.UNEXPECTED_TARGET_MISS)
        origin.finishWorkout(sessionId)
        origin.upsertCheckIn(
            ReadinessCheckIn(
                localDate = day,
                sleepQuality = 3,
                muscleSoreness = mapOf(MuscleGroup.BEINE to 4),
            ),
        )

        val reloaded = newStore(MemoryPersistence(origin.exportJson()))

        assertEquals(origin.snapshot().readinessData, reloaded.snapshot().readinessData)
        assertEquals(
            SetIntention.UNEXPECTED_TARGET_MISS,
            reloaded.snapshot().readinessData.setIntentions.single { it.setId == setId }.intention,
        )
        assertEquals(
            4,
            reloaded.snapshot().readinessData.checkIns.single().muscleSoreness[MuscleGroup.BEINE],
        )
    }

    @Test
    fun legacyPayloadWithoutReadinessImportsEmpty() = runTest {
        val source = newStore()
        val sessionId = source.startWorkout(name = "Montag")
        source.addSet(newSet(sessionId), intention = SetIntention.PLANNED_FAILURE)
        source.upsertCheckIn(ReadinessCheckIn(localDate = day, energy = 5))

        val legacy = withoutReadiness(source.exportJson())
        val store = newStore()

        store.importJson(legacy)

        assertEquals(ReadinessData(), store.snapshot().readinessData)
        assertTrue(
            store.snapshot().workoutSets.isNotEmpty(),
            "the workout graph itself must survive an import without the side channel",
        )
    }

    @Test
    fun failedPersistencePublishesNoNewState() = runTest {
        val persistence = MemoryPersistence()
        val store = newStore(persistence)
        val before = store.snapshot()
        persistence.failWrites = true

        assertFailsWith<IllegalStateException> {
            store.upsertCheckIn(ReadinessCheckIn(localDate = day, energy = 5))
        }

        assertEquals(before, store.snapshot())
        assertEquals(before, store.state.value)
        assertTrue(store.snapshot().readinessData.checkIns.isEmpty())
    }

    /** Reshapes a current export into a schema-12 backup that has no readiness key at all. */
    private fun withoutReadiness(exported: String): String {
        val root = Json.parseToJsonElement(exported).jsonObject.toMutableMap()
        root.remove("readinessData")
        root["schemaVersion"] = JsonPrimitive(12)
        return JsonObject(root).toString()
    }

    private fun newSet(
        sessionId: Long,
        setNumber: Int = 1,
    ) = BackupWorkoutSet(
        id = 0L,
        sessionId = sessionId,
        exerciseId = exercise.id,
        setNumber = setNumber,
        reps = 8,
        weightKg = 60.0,
        setType = NORMAL_SET_TYPE,
        completedAt = 1_000L,
    )

    private fun newStore(
        persistence: MemoryPersistence = MemoryPersistence(),
        now: () -> Long = { 1_000L },
    ) = SharedStateStore(
        persistence = persistence,
        seedExercises = listOf(exercise),
        nowEpochMillis = now,
    )

    /** Mirrors the store-test fixture: inspectable in-memory writes with an injectable failure. */
    private class MemoryPersistence(
        var value: String? = null,
        var failWrites: Boolean = false,
    ) : SharedStatePersistence {
        override fun read(): String? = value

        override fun writeAtomically(serialized: String) {
            check(!failWrites) { "injected write failure" }
            value = serialized
        }
    }
}
