package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sichert die Datums-/Fenster-Queries gegen In-Memory-Room ab:
 * getCompletedSessionCountSince und getWorkSetsCompletedSince
 * (endTime-IS-NOT-NULL- und Warmup-Filter, sinceEpochMillis).
 */
@RunWith(AndroidJUnit4::class)
class WorkoutSessionDateFilterTest {

    private lateinit var database: IronLogDatabase
    private lateinit var sessionDao: WorkoutSessionDao
    private lateinit var setDao: WorkoutSetDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            IronLogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        sessionDao = database.workoutSessionDao()
        setDao = database.workoutSetDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getCompletedSessionCountSince_zaehltNurBeendeteSessionsImFenster() = runBlocking {
        insertSession(id = 1L, startTime = 1_000L, endTime = 2_000L) // vor dem Fenster
        insertSession(id = 2L, startTime = 3_000L, endTime = 4_000L) // im Fenster
        insertSession(id = 3L, startTime = 5_000L, endTime = null) // aktiv -> zaehlt nie

        assertEquals(1, sessionDao.getCompletedSessionCountSince(sinceEpoch = 2_500L))
    }

    @Test
    fun getCompletedSessionCountSince_zaehltStartGenauAmFensterrand() = runBlocking {
        insertSession(id = 1L, startTime = 2_000L, endTime = 3_000L) // startTime == since -> zaehlt
        insertSession(id = 2L, startTime = 2_001L, endTime = 3_001L) // knapp danach -> zaehlt

        assertEquals(2, sessionDao.getCompletedSessionCountSince(sinceEpoch = 2_000L))
        assertEquals(1, sessionDao.getCompletedSessionCountSince(sinceEpoch = 2_001L))
    }

    @Test
    fun getWorkSetsCompletedSince_filtertWarmupUndAktiveSessionsUndSortiertNachSessionstart() = runBlocking {
        val exerciseId = database.exerciseDao().insert(
            ExerciseEntity(
                name = "Bankdruecken",
                primaryMuscleGroup = "BRUST",
                secondaryMuscleGroups = "",
                category = "LANGHANTEL"
            )
        )
        // Session A: beendet, aber vor dem Fenster -> deren Sets zaehlen nicht
        insertSession(id = 1L, startTime = 1_000L, endTime = 2_000L)
        insertSet(sessionId = 1L, exerciseId = exerciseId, weightKg = 10.0, isWarmup = false)
        insertSet(sessionId = 1L, exerciseId = exerciseId, weightKg = 20.0, isWarmup = true)

        // Session B: beendet, startTime exakt am Fensterrand -> Arbeits-Set zaehlt
        insertSession(id = 2L, startTime = 2_500L, endTime = 3_500L)
        insertSet(sessionId = 2L, exerciseId = exerciseId, weightKg = 30.0, isWarmup = false)
        insertSet(sessionId = 2L, exerciseId = exerciseId, weightKg = 35.0, isWarmup = true)

        // Session C: aktiv (endTime IS NULL) -> Sets zaehlen nie
        insertSession(id = 3L, startTime = 4_000L, endTime = null)
        insertSet(sessionId = 3L, exerciseId = exerciseId, weightKg = 40.0, isWarmup = false)

        // Session D: beendet, im Fenster -> Arbeits-Set zaehlt
        insertSession(id = 4L, startTime = 6_000L, endTime = 7_000L)
        insertSet(sessionId = 4L, exerciseId = exerciseId, weightKg = 50.0, isWarmup = false)

        val result = setDao.getWorkSetsCompletedSince(sinceEpochMillis = 2_500L)

        // ORDER BY s.startTime ASC: Session B (2500) vor Session D (6000)
        assertEquals(listOf(30.0, 50.0), result.map { it.weightKg })
    }

    private suspend fun insertSession(
        id: Long,
        startTime: Long,
        endTime: Long?
    ) {
        sessionDao.insert(
            WorkoutSessionEntity(
                id = id,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = if (endTime != null) endTime - startTime else 0L,
                name = "Session $id"
            )
        )
    }

    private suspend fun insertSet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double,
        isWarmup: Boolean
    ) {
        setDao.insert(
            WorkoutSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                reps = 8,
                weightKg = weightKg,
                isWarmup = isWarmup,
                completedAt = sessionId * 1_000L
            )
        )
    }
}
