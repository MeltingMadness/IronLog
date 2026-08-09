package com.ironlog.app.data.repository

import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.shared.backup.BackupWorkoutSet
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupWorkoutSetRoundTripTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun `non-null RPE and target snapshot survive export JSON import round trip`() {
        val source = WorkoutSetEntity(
            id = 20L,
            sessionId = 10L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 8,
            weightKg = 80.0,
            isWarmup = false,
            completedAt = 1_200L,
            rpe = 8.5,
            planTargetSnapshotId = 41L
        )

        val exported = source.toBackupWorkoutSet()
        val encoded = json.encodeToString(BackupWorkoutSet.serializer(), exported)
        val decoded = json.decodeFromString(BackupWorkoutSet.serializer(), encoded)
        val imported = decoded.toWorkoutSetEntity()

        assertEquals(8.5, imported.rpe ?: Double.NaN, 0.0)
        assertEquals(41L, imported.planTargetSnapshotId)
    }

    @Test
    fun `legacy backup without RPE remains importable`() {
        val legacyJson =
            """{"id":20,"sessionId":10,"exerciseId":1,"setNumber":1,"reps":8,"weightKg":80.0,"isWarmup":false,"completedAt":1200}"""

        val decoded = json.decodeFromString(BackupWorkoutSet.serializer(), legacyJson)
        val imported = decoded.toWorkoutSetEntity()

        assertNull(imported.rpe)
    }
}
