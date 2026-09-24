package com.ironlog.shared.readinessdata

import com.ironlog.shared.model.MuscleGroup
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReadinessDataMergerTest {

    private fun checkIn(date: Int, energy: Int? = null, soreness: Map<MuscleGroup, Int> = emptyMap()) =
        ReadinessCheckIn(
            localDate = LocalDate(2026, 9, date),
            energy = energy,
            muscleSoreness = soreness,
        )

    @Test
    fun `copies check-ins only present on one side`() {
        val local = ReadinessData(checkIns = listOf(checkIn(10, energy = 3)))
        val imported = ReadinessData(checkIns = listOf(checkIn(11, energy = 4)))

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(listOf(LocalDate(2026, 9, 10), LocalDate(2026, 9, 11)), result.data.checkIns.map { it.localDate })
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `conflicting check-in for the same date keeps local by default and reports it`() {
        val local = ReadinessData(checkIns = listOf(checkIn(11, energy = 3)))
        val imported = ReadinessData(checkIns = listOf(checkIn(11, energy = 5)))

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(3, result.data.checkIns.single().energy)
        assertEquals(1, result.conflicts.size)
        assertEquals(ReadinessMergeConflictKind.CHECK_IN, result.conflicts.single().kind)
        assertEquals("2026-09-11", result.conflicts.single().key)
    }

    @Test
    fun `importedWins resolves a check-in conflict in favor of the imported record`() {
        val local = ReadinessData(checkIns = listOf(checkIn(11, energy = 3)))
        val imported = ReadinessData(checkIns = listOf(checkIn(11, energy = 5)))

        val result = ReadinessDataMerger.merge(local, imported, importedWins = true)

        assertEquals(5, result.data.checkIns.single().energy)
        assertEquals(1, result.conflicts.size)
    }

    @Test
    fun `a missing intention never downgrades a known intention`() {
        val local = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.PLANNED_FAILURE)),
        )
        val imported = ReadinessData()

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(SetIntention.PLANNED_FAILURE, result.data.setIntentions.single().intention)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `an unknown imported intention is upgraded to the known local intention without a conflict`() {
        val local = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.UNEXPECTED_TARGET_MISS)),
        )
        val imported = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.UNKNOWN)),
        )

        val result = ReadinessDataMerger.merge(local, imported, importedWins = true)

        assertEquals(SetIntention.UNEXPECTED_TARGET_MISS, result.data.setIntentions.single().intention)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `a known imported intention fills a local unknown without a conflict`() {
        val local = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.UNKNOWN)),
        )
        val imported = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.PLANNED_FAILURE)),
        )

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(SetIntention.PLANNED_FAILURE, result.data.setIntentions.single().intention)
        assertTrue(result.conflicts.isEmpty())
    }

    @Test
    fun `two different known intentions are reported as a conflict`() {
        val local = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.PLANNED_FAILURE)),
        )
        val imported = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.UNEXPECTED_TARGET_MISS)),
        )

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(SetIntention.PLANNED_FAILURE, result.data.setIntentions.single().intention)
        assertEquals(ReadinessMergeConflictKind.SET_INTENTION, result.conflicts.single().kind)
        assertEquals("42", result.conflicts.single().key)
    }

    @Test
    fun `equal intention with a differing note is reported instead of silently dropped`() {
        val local = ReadinessData(
            setIntentions = listOf(
                SetIntentionRecord(42, SetIntention.PLANNED_FAILURE, note = "geplant im Plan"),
            ),
        )
        val imported = ReadinessData(
            setIntentions = listOf(
                SetIntentionRecord(42, SetIntention.PLANNED_FAILURE, note = "spontan"),
            ),
        )

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals("geplant im Plan", result.data.setIntentions.single().note)
        assertEquals(ReadinessMergeConflictKind.SET_INTENTION, result.conflicts.single().kind)
        assertTrue(result.conflicts.single().localValue.contains("geplant im Plan"), result.conflicts.single().toString())
        assertTrue(result.conflicts.single().importedValue.contains("spontan"), result.conflicts.single().toString())
    }

    @Test
    fun `equal intention with a differing timestamp is reported`() {
        val local = ReadinessData(
            setIntentions = listOf(
                SetIntentionRecord(42, SetIntention.PLANNED_FAILURE, recordedAtEpochMillis = 100L),
            ),
        )
        val imported = ReadinessData(
            setIntentions = listOf(
                SetIntentionRecord(42, SetIntention.PLANNED_FAILURE, recordedAtEpochMillis = 200L),
            ),
        )

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(100L, result.data.setIntentions.single().recordedAtEpochMillis)
        assertEquals(1, result.conflicts.size)
    }

    @Test
    fun `an unknown record with a note is not treated as a pure upgrade`() {
        val local = ReadinessData(
            setIntentions = listOf(
                SetIntentionRecord(42, SetIntention.UNKNOWN, note = "unklar, aber notiert"),
            ),
        )
        val imported = ReadinessData(
            setIntentions = listOf(SetIntentionRecord(42, SetIntention.PLANNED_FAILURE)),
        )

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(SetIntention.PLANNED_FAILURE, result.data.setIntentions.single().intention)
        assertEquals(1, result.conflicts.size)
    }

    @Test
    fun `check-in metadata difference alone is reported as a conflict`() {
        val local = ReadinessData(
            checkIns = listOf(checkIn(11, energy = 3).copy(recordedAtEpochMillis = 100L)),
        )
        val imported = ReadinessData(
            checkIns = listOf(checkIn(11, energy = 3).copy(recordedAtEpochMillis = 200L)),
        )

        val result = ReadinessDataMerger.merge(local, imported)

        assertEquals(ReadinessMergeConflictKind.CHECK_IN, result.conflicts.single().kind)
        assertEquals(100L, result.data.checkIns.single().recordedAtEpochMillis)
    }

    @Test
    fun `duplicate keys in either input are rejected before merging`() {
        val duplicateDates = ReadinessData(
            checkIns = listOf(checkIn(11, energy = 3), checkIn(11, energy = 4)),
        )
        val duplicateIntentions = ReadinessData(
            setIntentions = listOf(
                SetIntentionRecord(42, SetIntention.PLANNED_FAILURE),
                SetIntentionRecord(42, SetIntention.UNEXPECTED_TARGET_MISS),
            ),
        )

        val fromLocal = assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataMerger.merge(duplicateDates, ReadinessData())
        }
        assertTrue(fromLocal.errors.any { it.startsWith("local document:") }, fromLocal.errors.toString())
        assertTrue(fromLocal.errors.any { it.contains("Duplicate") }, fromLocal.errors.toString())

        val fromImported = assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataMerger.merge(ReadinessData(), duplicateIntentions)
        }
        assertTrue(fromImported.errors.any { it.startsWith("imported document:") }, fromImported.errors.toString())
    }

    @Test
    fun `out of range input values are rejected before merging`() {
        val invalid = ReadinessData(checkIns = listOf(checkIn(11, energy = 9)))

        assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataMerger.merge(invalid, ReadinessData())
        }
    }
}
