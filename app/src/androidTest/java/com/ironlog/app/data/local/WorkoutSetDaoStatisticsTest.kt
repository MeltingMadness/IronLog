package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
 * The statistics query must use completed sessions and an upper timestamp
 * bound, while the live-training query continues to expose every set.
 */
@RunWith(AndroidJUnit4::class)
class WorkoutSetDaoStatisticsTest {

    private lateinit var database: IronLogDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            IronLogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getCompletedSetsForExerciseList_excludesActiveAndFutureRows_withoutChangingLiveQuery() = runBlocking {
        val exerciseId = database.exerciseDao().insert(
            ExerciseEntity(
                name = "Bankdruecken",
                primaryMuscleGroup = "BRUST",
                secondaryMuscleGroups = "",
                category = "LANGHANTEL"
            )
        )
        insertSession(id = 1L, startTime = 1_000L, endTime = 2_000L)
        insertSession(id = 2L, startTime = 3_000L, endTime = null)
        insertSession(id = 3L, startTime = 4_000L, endTime = 5_000L)
        insertSession(id = 4L, startTime = 9_000L, endTime = 10_000L)

        insertSet(sessionId = 1L, exerciseId = exerciseId, weightKg = 80.0, completedAt = 1_500L)
        insertSet(sessionId = 2L, exerciseId = exerciseId, weightKg = 90.0, completedAt = 3_500L)
        insertSet(sessionId = 3L, exerciseId = exerciseId, weightKg = 100.0, completedAt = 8_500L)
        insertSet(sessionId = 4L, exerciseId = exerciseId, weightKg = 110.0, completedAt = 9_500L)

        val historical = database.workoutSetDao().getCompletedSetsForExerciseList(
            exerciseId = exerciseId,
            nowEpochMillis = 8_000L
        )
        assertEquals(listOf(80.0), historical.map { it.weightKg })

        val boundedWorkSets = database.workoutSetDao().getWorkSetsCompletedBetween(
            sinceEpochMillis = 0L,
            untilEpochMillis = 8_000L
        )
        assertEquals(listOf(80.0), boundedWorkSets.map { it.weightKg })

        // The query used by active workout screens remains deliberately broad.
        val liveSets = database.workoutSetDao().getSetsForExerciseList(exerciseId)
        assertEquals(listOf(110.0, 100.0, 90.0, 80.0), liveSets.map { it.weightKg })
    }

    private suspend fun insertSession(id: Long, startTime: Long, endTime: Long?) {
        database.workoutSessionDao().insert(
            WorkoutSessionEntity(
                id = id,
                startTime = startTime,
                endTime = endTime,
                durationSeconds = endTime?.minus(startTime) ?: 0L,
                name = "Session $id"
            )
        )
    }

    private suspend fun insertSet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double,
        completedAt: Long
    ) {
        database.workoutSetDao().insert(
            WorkoutSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                reps = 5,
                weightKg = weightKg,
                setType = "NORMAL",
                completedAt = completedAt
            )
        )
    }
}
