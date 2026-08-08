package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class WorkoutSetDaoContextScopeTest {

    private lateinit var database: IronLogDatabase
    private lateinit var setDao: WorkoutSetDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            IronLogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        setDao = database.workoutSetDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun contextQueriesSeparateNormalAndMetaHistoryAndExcludeCurrentSession() = runBlocking {
        val exerciseId = database.exerciseDao().insert(
            ExerciseEntity(
                name = "Bankdruecken",
                primaryMuscleGroup = "BRUST",
                secondaryMuscleGroups = "",
                category = "LANGHANTEL"
            )
        )
        val normalSessionId = insertSession(
            id = 101L,
            startTime = 1_000L,
            planId = 3L,
            metaPlanId = null
        )
        val metaASessionId = insertSession(
            id = 102L,
            startTime = 2_000L,
            planId = 3L,
            metaPlanId = 8L
        )
        val metaBSessionId = insertSession(
            id = 103L,
            startTime = 3_000L,
            planId = 3L,
            metaPlanId = 9L
        )
        val currentSessionId = insertSession(
            id = 104L,
            startTime = 4_000L,
            planId = 3L,
            metaPlanId = 8L
        )
        val otherPlanSessionId = insertSession(
            id = 105L,
            startTime = 5_000L,
            planId = 77L,
            metaPlanId = null
        )

        insertSet(normalSessionId, exerciseId, weightKg = 80.0)
        insertSet(metaASessionId, exerciseId, weightKg = 85.0)
        insertSet(metaBSessionId, exerciseId, weightKg = 90.0)
        insertSet(currentSessionId, exerciseId, weightKg = 95.0)
        insertSet(otherPlanSessionId, exerciseId, weightKg = 100.0)

        val normal = setDao.getMostRecentCompletedSetsForNormalPlanExercises(
            currentSessionId = currentSessionId,
            exerciseIds = listOf(exerciseId),
            planId = 3L
        )
        assertEquals(listOf(normalSessionId), normal.map { it.sessionId }.distinct())

        val metaA = setDao.getMostRecentCompletedSetsForMetaPlanExercises(
            currentSessionId = currentSessionId,
            exerciseIds = listOf(exerciseId),
            planId = 3L,
            metaPlanId = 8L
        )
        assertEquals(listOf(metaASessionId), metaA.map { it.sessionId }.distinct())

        val metaB = setDao.getMostRecentCompletedSetsForMetaPlanExercises(
            currentSessionId = currentSessionId,
            exerciseIds = listOf(exerciseId),
            planId = 3L,
            metaPlanId = 9L
        )
        assertEquals(listOf(metaBSessionId), metaB.map { it.sessionId }.distinct())

        val shared = setDao.getMostRecentCompletedSetsForPlanExercises(
            currentSessionId = currentSessionId,
            exerciseIds = listOf(exerciseId),
            planId = 3L
        )
        assertEquals(listOf(metaBSessionId), shared.map { it.sessionId }.distinct())
        assertEquals(listOf(90.0), shared.map { it.weightKg })
    }

    private suspend fun insertSession(
        id: Long,
        startTime: Long,
        planId: Long,
        metaPlanId: Long?
    ): Long {
        database.workoutSessionDao().insert(
            WorkoutSessionEntity(
                id = id,
                startTime = startTime,
                endTime = startTime + 60_000L,
                durationSeconds = 60L,
                name = "Session $id",
                planId = planId,
                metaPlanId = metaPlanId
            )
        )
        return id
    }

    private suspend fun insertSet(
        sessionId: Long,
        exerciseId: Long,
        weightKg: Double
    ) {
        setDao.insert(
            WorkoutSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                reps = 8,
                weightKg = weightKg,
                isWarmup = false,
                completedAt = sessionId * 1_000L
            )
        )
    }
}
