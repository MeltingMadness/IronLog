package com.ironlog.app.data.repository

import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.model.SetType
import com.ironlog.shared.backup.BackupWorkoutSet
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWorkoutSetRoundTripTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    @Test
    fun `all current set types survive export JSON import round trip`() {
        SetType.entries.forEachIndexed { index, type ->
            val source = WorkoutSetEntity(
                id = 20L + index,
                sessionId = 10L,
                exerciseId = 1L,
                setNumber = index + 1,
                reps = 8,
                weightKg = 80.0,
                setType = type.name,
                completedAt = 1_200L + index,
                rpe = 8.5,
                planTargetSnapshotId = 41L
            )

            val exported = source.toBackupWorkoutSet()
            val encoded = json.encodeToString(BackupWorkoutSet.serializer(), exported)
            val decoded = json.decodeFromString(BackupWorkoutSet.serializer(), encoded)
            val imported = decoded.toWorkoutSetEntity()

            assertEquals(type.name, exported.setType)
            assertNull("new exports should omit the legacy alias", exported.isWarmup)
            assertTrue(encoded.contains("\"setType\":\"${type.name}\""))
            assertFalse("new exports must not flatten through isWarmup", encoded.contains("isWarmup"))
            assertEquals(type.name, imported.setType)
            assertEquals(8.5, imported.rpe ?: Double.NaN, 0.0)
            assertEquals(41L, imported.planTargetSnapshotId)
        }
    }

    @Test
    fun `legacy isWarmup flag maps to normal or warmup set type`() {
        listOf(true to "WARMUP", false to "NORMAL").forEach { (isWarmup, expectedType) ->
            val legacyJson =
                """{"id":20,"sessionId":10,"exerciseId":1,"setNumber":1,"reps":8,"weightKg":80.0,"isWarmup":$isWarmup,"completedAt":1200}"""

            val decoded = json.decodeFromString(BackupWorkoutSet.serializer(), legacyJson)
            val imported = decoded.toWorkoutSetEntity()

            assertEquals(expectedType, imported.setType)
        }
    }

    @Test
    fun `unsupported set types are rejected by both adapters`() {
        val source = WorkoutSetEntity(
            id = 20L,
            sessionId = 10L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 8,
            weightKg = 80.0,
            setType = "UNKNOWN",
            completedAt = 1_200L,
            rpe = 8.5,
            planTargetSnapshotId = 41L
        )

        assertThrows(IllegalArgumentException::class.java) {
            source.toBackupWorkoutSet()
        }
        val invalid = BackupWorkoutSet(
            id = 20L,
            sessionId = 10L,
            exerciseId = 1L,
            setNumber = 1,
            reps = 8,
            weightKg = 80.0,
            setType = "UNKNOWN",
            completedAt = 1_200L
        )
        assertThrows(IllegalArgumentException::class.java) {
            invalid.toWorkoutSetEntity()
        }
    }
}
