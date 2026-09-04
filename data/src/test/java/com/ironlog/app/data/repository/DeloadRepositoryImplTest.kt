package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.domain.deload.DeloadDetector
import com.ironlog.app.domain.model.DeloadSignal
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.SetType
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DeloadRepositoryImplTest {

    private val sessionDao: WorkoutSessionDao = mockk()
    private val setDao: WorkoutSetDao = mockk()
    private val exerciseDao: ExerciseDao = mockk()
    private val today = LocalDate.of(2026, 9, 1)
    private val repository = DeloadRepositoryImpl(
        sessionDao = sessionDao,
        setDao = setDao,
        exerciseDao = exerciseDao,
        detector = DeloadDetector(),
        now = { today }
    )

    private fun session(id: Long, date: LocalDate): WorkoutSessionEntity = WorkoutSessionEntity(
        id = id,
        startTime = EpochConverter.toLong(date.atStartOfDay()),
        endTime = EpochConverter.toLong(date.atStartOfDay().plusHours(1)),
        durationSeconds = 3600,
        name = "Training",
        notes = ""
    )

    private fun entitySet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double,
        date: LocalDate = today,
        reps: Int = 5,
        setType: String = "NORMAL"
    ): WorkoutSetEntity = WorkoutSetEntity(
        id = sessionId * 100 + exerciseId,
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = 1,
        reps = reps,
        weightKg = weightKg,
        setType = setType,
        completedAt = EpochConverter.toLong(date.atStartOfDay().plusHours(1))
    )

    private fun exercise(id: Long, category: ExerciseCategory): ExerciseEntity = ExerciseEntity(
        id = id,
        name = "Exercise $id",
        primaryMuscleGroup = MuscleGroup.BEINE.name,
        secondaryMuscleGroups = "",
        category = category.name,
        isCustom = false,
        notes = "",
        isArchived = false
    )

    @Test
    fun `assess filters the window and classifies barbell exercises as compound`() = runTest {
        val inWindow = session(1, today.minusWeeks(3))
        val inWindow2 = session(2, today.minusWeeks(2))
        val inWindow3 = session(3, today.minusWeeks(1))
        val inWindow4 = session(4, today)
        val stale = session(5, today.minusWeeks(5))

        coEvery { sessionDao.getAllCompletedSessionsList() } returns
            listOf(stale, inWindow, inWindow2, inWindow3, inWindow4)
        coEvery { setDao.getSetsForSessions(any()) } returns listOf(
            entitySet(1, 10, 100.0, date = today.minusWeeks(3)),
            entitySet(2, 10, 100.0, date = today.minusWeeks(2)),
            entitySet(3, 10, 97.5, date = today.minusWeeks(1)),
            entitySet(4, 10, 95.0, date = today),
            entitySet(4, 10, 95.0, date = today, reps = 0, setType = "FAILURE")
        )
        coEvery { exerciseDao.getExercisesByIds(any()) } returns listOf(
            exercise(10, ExerciseCategory.LANGHANTEL),
            exercise(11, ExerciseCategory.KURZHANTEL)
        )

        val assessment = repository.assess()

        // Der abgelaufene Session (5) darf nicht eingehen.
        assertEquals(4, assessment.sessionCount)
        // Kniebeuge-Trend 100 → 96,25 = −3,75 % → Drop; 1 von 5 Sätzen Fehlversuch = 20 %.
        assertEquals(DeloadSignal.E1RM_DROP, assessment.signals.first())
        assertTrue(DeloadSignal.FAILURE_FREQUENCY in assessment.signals)
        assertEquals(10L, assessment.strongestExerciseId)
        assertTrue(assessment.recommended)
        assertEquals(1, assessment.analyzedCompoundCount)
    }

    @Test
    fun `assess without sessions returns an empty assessment`() = runTest {
        coEvery { sessionDao.getAllCompletedSessionsList() } returns emptyList()

        val assessment = repository.assess()

        assertFalse(assessment.recommended)
        assertEquals(0, assessment.sessionCount)
        assertEquals(0, assessment.fatigueScore)
        assertEquals(today.minusWeeks(4), assessment.windowStart)
        assertEquals(today, assessment.windowEnd)
    }

    @Test
    fun `assess without compound exercises never recommends`() = runTest {
        coEvery { sessionDao.getAllCompletedSessionsList() } returns listOf(
            session(1, today.minusWeeks(3)),
            session(2, today.minusWeeks(2)),
            session(3, today.minusWeeks(1)),
            session(4, today)
        )
        coEvery { setDao.getSetsForSessions(any()) } returns listOf(
            entitySet(1, 20, 30.0, date = today.minusWeeks(3)),
            entitySet(2, 20, 30.0, date = today.minusWeeks(2)),
            entitySet(3, 20, 30.0, date = today.minusWeeks(1)),
            entitySet(4, 20, 30.0, date = today)
        )
        // Nur Kurzhantel-/Maschinenübungen → keine Verbundübung.
        coEvery { exerciseDao.getExercisesByIds(any()) } returns listOf(
            exercise(20, ExerciseCategory.MASCHINE)
        )

        val assessment = repository.assess()

        assertFalse(assessment.recommended)
        assertEquals(0, assessment.analyzedCompoundCount)
    }

    @Test
    fun `assess maps all sets of a session into the detector input`() = runTest {
        coEvery { sessionDao.getAllCompletedSessionsList() } returns listOf(
            session(1, today.minusWeeks(3)),
            session(2, today.minusWeeks(2)),
            session(3, today.minusWeeks(1)),
            session(4, today)
        )
        coEvery { setDao.getSetsForSessions(any()) } returns listOf(
            entitySet(1, 10, 100.0, date = today.minusWeeks(3), setType = SetType.WARMUP.name),
            entitySet(2, 10, 100.0, date = today.minusWeeks(2)),
            entitySet(3, 10, 100.0, date = today.minusWeeks(1)),
            entitySet(4, 10, 100.0, date = today)
        )
        coEvery { exerciseDao.getExercisesByIds(any()) } returns listOf(
            exercise(10, ExerciseCategory.LANGHANTEL)
        )

        val assessment = repository.assess()

        // WARMUP-Sätze zählen nicht als E1RM-Punkte: nur 3 Wochen mit NORMAL-Sätzen →
        // die Verbundübung wird trotzdem analysiert, aber der flache Trend ist nur Stagnation.
        assertEquals(1, assessment.analyzedCompoundCount)
        assertFalse(assessment.recommended)
    }
}