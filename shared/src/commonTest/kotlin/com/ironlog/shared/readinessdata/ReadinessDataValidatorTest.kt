package com.ironlog.shared.readinessdata

import com.ironlog.shared.model.MuscleGroup
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReadinessDataValidatorTest {

    private fun checkIn(
        date: Int = 11,
        sleepQuality: Int? = null,
        energy: Int? = null,
        stress: Int? = null,
        soreness: Map<MuscleGroup, Int> = emptyMap(),
    ) = ReadinessCheckIn(
        localDate = LocalDate(2026, 9, date),
        sleepQuality = sleepQuality,
        energy = energy,
        stress = stress,
        muscleSoreness = soreness,
    )

    @Test
    fun `accepts a fully answered check-in`() {
        val result = ReadinessDataValidator.validate(
            ReadinessData(
                checkIns = listOf(
                    checkIn(
                        sleepQuality = 1,
                        energy = 5,
                        stress = 3,
                        soreness = mapOf(MuscleGroup.BEINE to 5),
                    ),
                ),
            ),
        )

        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `rejects each out of range dimension and names it`() {
        val result = ReadinessDataValidator.validate(
            ReadinessData(
                checkIns = listOf(checkIn(sleepQuality = 0, energy = 6, stress = -1)),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("sleepQuality=0") }, result.errors.toString())
        assertTrue(result.errors.any { it.contains("energy=6") }, result.errors.toString())
        assertTrue(result.errors.any { it.contains("stress=-1") }, result.errors.toString())
    }

    @Test
    fun `rejects out of range soreness and names the muscle group`() {
        val result = ReadinessDataValidator.validate(
            ReadinessData(checkIns = listOf(checkIn(soreness = mapOf(MuscleGroup.WADEN to 9)))),
        )

        assertFalse(result.isValid)
        assertTrue(
            result.errors.any { it.contains("muscleSoreness.WADEN=9") },
            result.errors.toString(),
        )
    }

    @Test
    fun `rejects duplicate check-in dates`() {
        val result = ReadinessDataValidator.validate(
            ReadinessData(checkIns = listOf(checkIn(energy = 3), checkIn(stress = 2))),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Duplicate readiness check-in") }, result.errors.toString())
    }

    @Test
    fun `rejects duplicate and non positive set intention ids`() {
        val result = ReadinessDataValidator.validate(
            ReadinessData(
                setIntentions = listOf(
                    SetIntentionRecord(setId = 0, intention = SetIntention.UNKNOWN),
                    SetIntentionRecord(setId = 5, intention = SetIntention.PLANNED_FAILURE),
                    SetIntentionRecord(setId = 5, intention = SetIntention.UNEXPECTED_TARGET_MISS),
                ),
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("positive set id") }, result.errors.toString())
        assertTrue(result.errors.any { it.contains("Duplicate set intention for set 5") }, result.errors.toString())
    }

    @Test
    fun `allows an explicit unknown intention`() {
        val result = ReadinessDataValidator.validate(
            ReadinessData(
                setIntentions = listOf(SetIntentionRecord(setId = 5, intention = SetIntention.UNKNOWN)),
            ),
        )

        assertTrue(result.isValid, result.errors.toString())
    }

    @Test
    fun `scale helper matches the documented bounds`() {
        assertTrue(ReadinessDataValidator.isValidScale(1))
        assertTrue(ReadinessDataValidator.isValidScale(5))
        assertFalse(ReadinessDataValidator.isValidScale(0))
        assertFalse(ReadinessDataValidator.isValidScale(6))
    }

    @Test
    fun `rejects negative recording timestamps`() {
        val result = ReadinessDataValidator.validate(
            ReadinessData(
                checkIns = listOf(checkIn(energy = 3).copy(recordedAtEpochMillis = -1)),
            ),
        )

        assertFalse(result.isValid)
        assertEquals(
            true,
            result.errors.any { it.contains("recordedAtEpochMillis=-1") },
        )
    }
}
