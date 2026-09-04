package com.ironlog.app.domain.util

import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class MuscleVolumeCalculatorTest {

    private val monday = LocalDate.of(2026, 1, 5) // Montag
    private val weekStart: LocalDateTime = monday.atStartOfDay()

    private val benchPress = Exercise(
        id = 1L,
        name = "Bankdrücken",
        primaryMuscleGroup = MuscleGroup.BRUST,
        secondaryMuscleGroups = listOf(MuscleGroup.TRIZEPS, MuscleGroup.SCHULTERN),
        category = ExerciseCategory.LANGHANTEL
    )

    private fun set(
        id: Long,
        exerciseId: Long = benchPress.id,
        setType: SetType = SetType.NORMAL,
        completedAt: LocalDateTime = weekStart
    ) = WorkoutSet(
        id = id,
        sessionId = id,
        exerciseId = exerciseId,
        setNumber = 1,
        reps = 8,
        weightKg = 60.0,
        setType = setType,
        completedAt = completedAt
    )

    // --- Aggregation & Gewichtung ---

    @Test
    fun `aggregiert Primaer mit 1,0 und Sekundaer mit 0,5 pro Arbeitssatz`() {
        val sets = listOf(set(1L), set(2L), set(3L, setType = SetType.DROP_SET))

        val volumes = MuscleVolumeCalculator.aggregateByMuscleGroup(sets, listOf(benchPress), monday)

        val brust = volumes.first { it.muscleGroup == MuscleGroup.BRUST }
        val trizeps = volumes.first { it.muscleGroup == MuscleGroup.TRIZEPS }
        val schultern = volumes.first { it.muscleGroup == MuscleGroup.SCHULTERN }
        assertEquals(3.0, brust.weeklySets, 0.0)
        assertEquals(1.5, trizeps.weeklySets, 0.0)
        assertEquals(1.5, schultern.weeklySets, 0.0)
    }

    @Test
    fun `zaehlt NORMAL DROP_SET und FAILURE, ignoriert WARMUP`() {
        val sets = listOf(
            set(1L, setType = SetType.NORMAL),
            set(2L, setType = SetType.DROP_SET),
            set(3L, setType = SetType.FAILURE),
            set(4L, setType = SetType.WARMUP)
        )

        val volumes = MuscleVolumeCalculator.aggregateByMuscleGroup(sets, listOf(benchPress), monday)

        assertEquals(3.0, volumes.first { it.muscleGroup == MuscleGroup.BRUST }.weeklySets, 0.0)
        assertEquals(1.5, volumes.first { it.muscleGroup == MuscleGroup.SCHULTERN }.weeklySets, 0.0)
    }

    @Test
    fun `filtert Saetze ausserhalb der Woche`() {
        val sets = listOf(
            // Montag 00:00 der aktuellen Woche: inklusiv
            set(1L, completedAt = weekStart),
            // Samstag derselben Woche: inklusiv
            set(2L, completedAt = monday.plusDays(5).atTime(23, 59)),
            // Sonntag der Vorwoche: exklusiv
            set(3L, completedAt = monday.minusDays(1).atTime(23, 59)),
            // Montag der Folgewoche 00:00: exklusiv (Wochenende ist exklusiv)
            set(4L, completedAt = monday.plusDays(7).atStartOfDay())
        )

        val volumes = MuscleVolumeCalculator.aggregateByMuscleGroup(sets, listOf(benchPress), monday)

        assertEquals(2.0, volumes.first { it.muscleGroup == MuscleGroup.BRUST }.weeklySets, 0.0)
    }

    @Test
    fun `ignoriert Saetze ohne bekannte Uebung`() {
        val sets = listOf(set(1L, exerciseId = 999L))

        val volumes = MuscleVolumeCalculator.aggregateByMuscleGroup(sets, listOf(benchPress), monday)

        assertTrue(volumes.isEmpty())
    }

    @Test
    fun `sortiert Muskelgruppen absteigend nach Volumen`() {
        val other = Exercise(
            id = 2L,
            name = "Fliegende",
            primaryMuscleGroup = MuscleGroup.BRUST,
            secondaryMuscleGroups = listOf(MuscleGroup.BIZEPS),
            category = ExerciseCategory.KURZHANTEL
        )
        // 1 Satz fuer other (BRUST 1,0 | BIZEPS 0,5), 3 Saetze fuer benchPress
        val sets = listOf(set(1L), set(2L), set(3L)) + listOf(
            set(4L, exerciseId = other.id)
        )

        val volumes = MuscleVolumeCalculator.aggregateByMuscleGroup(
            sets, listOf(benchPress, other), monday
        )

        assertEquals(
            listOf(MuscleGroup.BRUST, MuscleGroup.TRIZEPS, MuscleGroup.SCHULTERN, MuscleGroup.BIZEPS),
            volumes.map { it.muscleGroup }
        )
    }

    // --- Status-Bewertung ---

    @Test
    fun `bewertet Status an den Schwellenwerten`() {
        val brust = MuscleVolumeCalculator.DEFAULT_THRESHOLDS.getValue(MuscleGroup.BRUST) // MEV 10, MRV 22

        assertEquals(VolumeStatus.LOW, MuscleVolumeCalculator.evaluateStatus(9.99, brust))
        assertEquals(VolumeStatus.OPTIMAL, MuscleVolumeCalculator.evaluateStatus(10.0, brust))
        assertEquals(VolumeStatus.OPTIMAL, MuscleVolumeCalculator.evaluateStatus(22.0, brust))
        assertEquals(VolumeStatus.HIGH, MuscleVolumeCalculator.evaluateStatus(22.01, brust))
    }

    @Test
    fun `MuscleVolume berechnet Status und gekappten Fortschritt`() {
        val thresholds = VolumeThresholds(mev = 10.0, mav = 15.0, mrv = 20.0)

        val optimal = MuscleVolume(MuscleGroup.BRUST, weeklySets = 15.0, thresholds = thresholds)
        assertEquals(VolumeStatus.OPTIMAL, optimal.status)
        assertEquals(0.75f, optimal.progress, 0.001f)

        val high = MuscleVolume(MuscleGroup.BRUST, weeklySets = 25.0, thresholds = thresholds)
        assertEquals(VolumeStatus.HIGH, high.status)
        assertEquals(1.0f, high.progress, 0.0f)
    }

    @Test
    fun `validiert MEV kleiner gleich MAV kleiner gleich MRV`() {
        assertThrows(IllegalArgumentException::class.java) {
            VolumeThresholds(mev = 12.0, mav = 10.0, mrv = 20.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            VolumeThresholds(mev = 10.0, mav = 15.0, mrv = 14.0)
        }
    }

    @Test
    fun `nutzt uebergebene Schwellenwerte mit Fallback auf Defaults`() {
        val custom = mapOf(MuscleGroup.BRUST to VolumeThresholds(mev = 2.0, mav = 4.0, mrv = 6.0))
        val sets = listOf(set(1L), set(2L), set(3L))

        val volumes = MuscleVolumeCalculator.aggregateByMuscleGroup(
            sets, listOf(benchPress), monday, thresholds = custom
        )

        val brust = volumes.first { it.muscleGroup == MuscleGroup.BRUST }
        assertEquals(custom.getValue(MuscleGroup.BRUST), brust.thresholds)
        assertEquals(VolumeStatus.OPTIMAL, brust.status) // 3,0 >= MEV 2

        // Sekundaere ohne Eintrag fallen auf DEFAULT_THRESHOLDS zurueck
        val trizeps = volumes.first { it.muscleGroup == MuscleGroup.TRIZEPS }
        assertEquals(
            MuscleVolumeCalculator.DEFAULT_THRESHOLDS.getValue(MuscleGroup.TRIZEPS),
            trizeps.thresholds
        )
    }

    // --- Wochenstart ---

    @Test
    fun `weekStartFor liefert den ersten Wochentag inklusive des Datums`() {
        val mittwoch = LocalDate.of(2026, 1, 7)

        assertEquals(LocalDate.of(2026, 1, 5), MuscleVolumeCalculator.weekStartFor(mittwoch, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 1, 4), MuscleVolumeCalculator.weekStartFor(mittwoch, DayOfWeek.SUNDAY))
        // Wochenstarttag selbst bleibt unveraendert
        assertEquals(mittwoch, MuscleVolumeCalculator.weekStartFor(mittwoch, DayOfWeek.WEDNESDAY))
    }
}