package com.ironlog.app.domain.deload

import com.ironlog.app.domain.model.DeloadSessionInput
import com.ironlog.app.domain.model.DeloadSignal
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeloadDetectorTest {

    private val detector = DeloadDetector()
    private val today = LocalDate.of(2026, 9, 1)
    private val squatId = 1L
    private val curlId = 2L // keine Verbundübung (Kurzhantel)
    private val compoundIds = setOf(squatId)
    private val baseline = LocalDateTime.of(2026, 1, 1, 12, 0)

    // --- helpers -------------------------------------------------------------

    private fun session(
        id: Long,
        date: LocalDate,
        sets: List<WorkoutSet> = emptyList()
    ) = DeloadSessionInput(id = id, startTime = date.atTime(18, 0), sets = sets)

    private fun set(
        weightKg: Double,
        reps: Int,
        setType: SetType = SetType.NORMAL,
        rpe: Double? = null,
        exerciseId: Long = squatId,
        completedAt: LocalDateTime = baseline
    ) = WorkoutSet(
        id = 0,
        sessionId = 0,
        exerciseId = exerciseId,
        setNumber = 1,
        reps = reps,
        weightKg = weightKg,
        setType = setType,
        rpe = rpe,
        completedAt = completedAt
    )

    private fun week(date: LocalDate): LocalDate = date

    /**
     * Builds a window with one session per week. `weeklyE1rm` maps week index
     * to the trained weight used by the session's single NORMAL set; the
     * remaining sets of the session are defined by `rpe`/`failure` flags.
     */
    private fun weeklySessions(
        weeklyE1rm: List<Double>,
        sessionRpe: Double? = null,
        failure: Boolean = false
    ): List<DeloadSessionInput> = weeklyE1rm.mapIndexed { index, weight ->
        val date = today.minusWeeks((weeklyE1rm.size - 1 - index).toLong())
            .with(java.time.DayOfWeek.MONDAY)
        val sets = mutableListOf(
            set(weightKg = weight, reps = 5, rpe = sessionRpe, completedAt = date.atTime(14, 0))
        )
        if (failure) {
            sets += set(
                weightKg = weight,
                reps = 0,
                setType = SetType.FAILURE,
                completedAt = date.atTime(14, 30)
            )
        }
        session(id = index + 1L, date = date, sets = sets)
    }

    // --- e1RM trend ----------------------------------------------------------

    @Test
    fun `stagnant e1rm alone does not recommend a deload`() {
        // 4 Wochen, Gewicht konstant: Stagnation nur +25 Punkte, unter der Schwelle.
        val sessions = weeklySessions(weeklyE1rm = listOf(100.0, 100.0, 100.0, 100.0))
        val result = detector.assess(sessions, compoundIds, today)

        assertTrue(DeloadSignal.E1RM_STAGNATION in result.signals)
        assertEquals(25, result.fatigueScore)
        assertFalse(result.recommended)
        assertEquals(squatId, result.strongestExerciseId)
    }

    @Test
    fun `e1rm drop alone recommends a deload`() {
        // Jüngere Hälfte im Schnitt 5 % unter der früheren Hälfte.
        val sessions = weeklySessions(weeklyE1rm = listOf(100.0, 100.0, 95.0, 95.0))
        val result = detector.assess(sessions, compoundIds, today)

        assertEquals(listOf(DeloadSignal.E1RM_DROP), result.signals)
        assertEquals(60, result.fatigueScore)
        assertTrue(result.recommended)
        assertEquals(squatId, result.strongestExerciseId)
        assertEquals(-5.0, result.strongestExerciseChangePercent!!, 0.001)
    }

    @Test
    fun `improving e1rm produces no e1rm signal`() {
        val sessions = weeklySessions(weeklyE1rm = listOf(90.0, 95.0, 100.0, 105.0))
        val result = detector.assess(sessions, compoundIds, today)

        assertFalse(DeloadSignal.E1RM_STAGNATION in result.signals)
        assertFalse(DeloadSignal.E1RM_DROP in result.signals)
        assertFalse(result.recommended)
    }

    @Test
    fun `non compound exercises are ignored for the e1rm trend`() {
        val sessionSets = listOf(
            set(weightKg = 100.0, reps = 5, exerciseId = curlId),
            set(weightKg = 95.0, reps = 5, exerciseId = curlId),
            set(weightKg = 90.0, reps = 5, exerciseId = curlId),
            set(weightKg = 85.0, reps = 5, exerciseId = curlId)
        )
        val sessions = listOf(
            session(1, week(today.minusWeeks(3)), listOf(sessionSets[0])),
            session(2, week(today.minusWeeks(2)), listOf(sessionSets[1])),
            session(3, week(today.minusWeeks(1)), listOf(sessionSets[2])),
            session(4, week(today), listOf(sessionSets[3]))
        )
        // Nur die Kurzhantelübung im Fenster → kein Compound-Trend, keine Empfehlung.
        val result = detector.assess(sessions, compoundIds, today)

        assertTrue(result.signals.isEmpty())
        assertEquals(0, result.fatigueScore)
        assertNull(result.strongestExerciseId)
        assertFalse(result.recommended)
    }

    @Test
    fun `exercise with fewer than two weekly e1rm points is not analyzed`() {
        // Nur eine der vier Wochen enthält Kniebeugen-Sätze → kein Trend möglich.
        val sessions = listOf(
            session(1, week(today.minusWeeks(3)), listOf(set(weightKg = 100.0, reps = 5))),
            session(2, week(today.minusWeeks(2)), listOf(set(weightKg = 40.0, reps = 12, exerciseId = curlId))),
            session(3, week(today.minusWeeks(1)), listOf(set(weightKg = 40.0, reps = 12, exerciseId = curlId))),
            session(4, week(today), listOf(set(weightKg = 40.0, reps = 12, exerciseId = curlId)))
        )
        val result = detector.assess(sessions, compoundIds, today)

        assertEquals(0, result.analyzedCompoundCount)
        assertTrue(result.signals.isEmpty())
        assertFalse(result.recommended)
    }

    // --- RPE creep -----------------------------------------------------------

    @Test
    fun `rising rpe across the window triggers the rpe signal`() {
        val sessions = weeklySessions(
            weeklyE1rm = listOf(100.0, 100.0, 100.0, 100.0),
            sessionRpe = null
        ).mapIndexed { index, s ->
            // RPE steigt von Woche zu Woche: 7.0, 7.5, 8.0, 8.5
            val rpe = 7.0 + index * 0.5
            s.copy(sets = s.sets.map { it.copy(rpe = rpe) })
        }
        val result = detector.assess(sessions, compoundIds, today)

        assertTrue(DeloadSignal.RPE_CREEP in result.signals)
        // Stagnation (25) + RPE-Creep (25) = 50 → noch keine Empfehlung.
        assertEquals(50, result.fatigueScore)
        assertFalse(result.recommended)
        assertEquals(7.8, result.averageRpe!!, 0.001)
    }

    @Test
    fun `rpe creep combined with failures recommends a deload`() {
        val sessions = weeklySessions(
            weeklyE1rm = listOf(100.0, 100.0, 100.0, 100.0)
        ).mapIndexed { index, s ->
            val rpe = 7.0 + index * 0.5
            s.copy(sets = s.sets.map { it.copy(rpe = rpe) })
        }.map { s ->
            // Zusätzlich ein Fehlversuch pro Session.
            val failure = set(weightKg = 100.0, reps = 0, setType = SetType.FAILURE)
            s.copy(sets = s.sets + failure)
        }
        val result = detector.assess(sessions, compoundIds, today)

        assertTrue(DeloadSignal.RPE_CREEP in result.signals)
        assertTrue(DeloadSignal.FAILURE_FREQUENCY in result.signals)
        // 25 (Stagnation) + 25 (RPE) + 25 (Fehlerquote 50 %) = 75 → Empfehlung.
        assertEquals(75, result.fatigueScore)
        assertTrue(result.recommended)
        assertEquals(0.5, result.failureRate, 0.001)
    }

    @Test
    fun `rpe without a session average requires sets with rpe`() {
        // Nur eine Einheit hat RPE-Werte → kein Creep-Signal.
        val sessions = weeklySessions(weeklyE1rm = listOf(100.0, 100.0, 100.0, 100.0))
            .mapIndexed { index, s ->
                if (index == 3) s else s.copy(sets = s.sets.map { it.copy(rpe = null) })
            }
        val result = detector.assess(sessions, compoundIds, today)

        assertFalse(DeloadSignal.RPE_CREEP in result.signals)
        assertNull(result.averageRpe)
    }

    // --- failure frequency ---------------------------------------------------

    @Test
    fun `frequent failure sets trigger the failure signal`() {
        // 2 Sätze pro Session, einer davon ein Fehlversuch → 50 % Quote.
        val sessions = weeklySessions(weeklyE1rm = listOf(110.0, 110.0, 110.0, 110.0))
            .map { s ->
                val failure = set(weightKg = 110.0, reps = 0, setType = SetType.FAILURE)
                s.copy(sets = s.sets + failure)
            }
        val result = detector.assess(sessions, compoundIds, today)

        assertTrue(DeloadSignal.FAILURE_FREQUENCY in result.signals)
        // Stagnation (25) + Fehlerquote voll (25) = 50 → noch keine Empfehlung.
        assertEquals(50, result.fatigueScore)
        assertFalse(result.recommended)
    }

    @Test
    fun `rare failure sets do not trigger the failure signal`() {
        // 1 von 8 Arbeitssätzen ist ein Fehlversuch → 12,5 % < 15 %.
        val sessions = weeklySessions(weeklyE1rm = listOf(110.0, 110.0, 110.0, 110.0))
            .mapIndexed { index, s ->
                if (index == 3) {
                    val failure = set(weightKg = 110.0, reps = 0, setType = SetType.FAILURE)
                    s.copy(sets = s.sets + failure)
                } else {
                    s.copy(sets = s.sets + set(weightKg = 110.0, reps = 6))
                }
            }
        val result = detector.assess(sessions, compoundIds, today)

        assertFalse(DeloadSignal.FAILURE_FREQUENCY in result.signals)
    }

    // --- data sufficiency ----------------------------------------------------

    @Test
    fun `no sessions never recommend a deload`() {
        val result = detector.assess(emptyList(), compoundIds, today)

        assertEquals(0, result.fatigueScore)
        assertTrue(result.signals.isEmpty())
        assertFalse(result.recommended)
        assertEquals(0, result.sessionCount)
    }

    @Test
    fun `fewer than minimum sessions never recommend a deload`() {
        val sessions = weeklySessions(weeklyE1rm = listOf(100.0, 95.0))
        val result = detector.assess(sessions, compoundIds, today)

        assertFalse(result.recommended)
        assertEquals(0, result.fatigueScore)
        assertTrue(result.signals.isEmpty())
    }

    // --- window / scoring details --------------------------------------------

    @Test
    fun `drop alone with a mid window stagnation still recommends via drop`() {
        // Frühere Hälfte 100/100, jüngere Hälfte 97,5/95 → Abfall unter −2,5 %.
        val sessions = weeklySessions(weeklyE1rm = listOf(100.0, 100.0, 97.5, 95.0))
        val result = detector.assess(sessions, compoundIds, today)

        assertEquals(DeloadSignal.E1RM_DROP, result.signals.single())
        assertEquals(60, result.fatigueScore)
        assertTrue(result.recommended)
        assertEquals(-3.75, result.strongestExerciseChangePercent!!, 0.001)
    }

    @Test
    fun `window bounds are reported and sessions outside are the callers problem`() {
        // 4 Wochen Fenster: windowStart = today - 4 Wochen, windowEnd = today.
        val sessions = weeklySessions(weeklyE1rm = listOf(100.0, 100.0, 100.0, 100.0))
        val result = detector.assess(sessions, compoundIds, today)

        assertEquals(today.minusWeeks(4), result.windowStart)
        assertEquals(today, result.windowEnd)
        assertEquals(4, result.sessionCount)
        assertEquals(1, result.analyzedCompoundCount)
    }

    @Test
    fun `warmup and drop sets never feed the e1rm trend`() {
        val dates = (0..3).map { week(today.minusWeeks(3 - it.toLong())) }
        val sessions = listOf(
            session(1, dates[0], listOf(
                set(weightKg = 100.0, reps = 5, completedAt = dates[0].atTime(14, 0)),
                set(weightKg = 60.0, reps = 5, setType = SetType.WARMUP, completedAt = dates[0].atTime(14, 0)),
                set(weightKg = 60.0, reps = 12, setType = SetType.DROP_SET, completedAt = dates[0].atTime(14, 0))
            )),
            session(2, dates[1], listOf(
                set(weightKg = 100.0, reps = 5, completedAt = dates[1].atTime(14, 0)),
                set(weightKg = 60.0, reps = 5, setType = SetType.WARMUP, completedAt = dates[1].atTime(14, 0))
            )),
            session(3, dates[2], listOf(
                set(weightKg = 100.0, reps = 5, completedAt = dates[2].atTime(14, 0)),
                set(weightKg = 60.0, reps = 12, setType = SetType.DROP_SET, completedAt = dates[2].atTime(14, 0))
            )),
            session(4, dates[3], listOf(set(weightKg = 100.0, reps = 5, completedAt = dates[3].atTime(14, 0))))
        )
        val result = detector.assess(sessions, compoundIds, today)

        // Nur die NORMAL-Sätze zählen → 100 kg jede Woche → Stagnation, keine Empfehlung.
        assertTrue(DeloadSignal.E1RM_STAGNATION in result.signals)
        assertFalse(result.recommended)
    }
}