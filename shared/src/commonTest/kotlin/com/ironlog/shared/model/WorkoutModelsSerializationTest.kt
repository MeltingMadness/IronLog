package com.ironlog.shared.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutModelsSerializationTest {

    private val json = Json

    @Test
    fun workoutSet_withoutPlanTargetSnapshotId_decodesToNull() {
        val decoded = json.decodeFromString<WorkoutSet>(
            """{"id": 1, "sessionId": 2, "exerciseId": 3, "setNumber": 1, "reps": 10, "weightKg": 80.0, "completedAt": "2026-03-24T10:15:00"}"""
        )

        assertNull(decoded.planTargetSnapshotId)
    }

    @Test
    fun workoutSet_roundTripsPlanTargetSnapshotId() {
        val source = WorkoutSet(
            sessionId = 2,
            exerciseId = 3,
            setNumber = 1,
            reps = 10,
            weightKg = 80.0,
            completedAt = LocalDateTime(2026, 3, 24, 10, 15),
            planTargetSnapshotId = 42L
        )

        val decoded = json.decodeFromString<WorkoutSet>(json.encodeToString(source))

        assertEquals(42L, decoded.planTargetSnapshotId)
    }

    @Test
    fun planExercise_withoutProgressionConfig_decodesToManualDefault() {
        val decoded = json.decodeFromString<PlanExercise>(
            """{"id": 1, "exerciseId": 3, "orderIndex": 0, "targetSets": 3, "targetReps": 10, "targetWeightKg": 80.0}"""
        )

        assertEquals(ProgressionConfig.Manual(), decoded.progressionConfig)
    }

    @Test
    fun planExercise_roundTripsLinearProgressionConfig() {
        val source = PlanExercise(
            exerciseId = 3,
            orderIndex = 0,
            progressionConfig = ProgressionConfig.Linear(
                step = WeightStep(originalValue = 2.5, originalUnit = UnitSystem.METRIC, kilograms = 2.5)
            )
        )

        val decoded = json.decodeFromString<PlanExercise>(json.encodeToString(source))

        assertEquals(source.progressionConfig, decoded.progressionConfig)
    }

    @Test
    fun planExercise_roundTripsRpeRirProgressionConfig() {
        val source = PlanExercise(
            exerciseId = 3,
            orderIndex = 0,
            progressionConfig = ProgressionConfig.RpeRir(
                targetRpe = 8.0,
                tolerance = 0.5,
                step = WeightStep(originalValue = 1.0, originalUnit = UnitSystem.METRIC, kilograms = 1.0)
            )
        )

        val decoded = json.decodeFromString<PlanExercise>(json.encodeToString(source))

        assertEquals(source.progressionConfig, decoded.progressionConfig)
    }
}
