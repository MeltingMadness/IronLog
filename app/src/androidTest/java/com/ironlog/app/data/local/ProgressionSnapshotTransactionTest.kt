package com.ironlog.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.data.db.RoomTransactionRunner
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.data.repository.WorkoutRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressionSnapshotTransactionTest {

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
    fun duplicateSnapshotPositionRollsBackSessionAndTargets() = runBlocking {
        val exerciseId = database.exerciseDao().insert(
            ExerciseEntity(
                name = "Squat",
                primaryMuscleGroup = "BEINE",
                secondaryMuscleGroups = "",
                category = "LANGHANTEL"
            )
        )
        val planId = database.trainingPlanDao().insertPlan(
            TrainingPlanEntity(name = "Duplicate positions", createdAt = 1_000L)
        )
        database.trainingPlanDao().insertExercises(
            listOf(
                PlanExerciseEntity(
                    planId = planId,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    targetWeightKg = 100.0
                ),
                PlanExerciseEntity(
                    planId = planId,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    targetWeightKg = 80.0
                )
            )
        )
        val repository = WorkoutRepositoryImpl(
            database.workoutSessionDao(),
            database.workoutSetDao(),
            database.personalRecordDao(),
            database.trainingPlanDao(),
            database.progressionDao(),
            RoomTransactionRunner(database)
        )

        try {
            repository.startWorkout("Must roll back", planId = planId, metaPlanId = null)
            fail("Expected duplicate snapshot positions to violate the unique index")
        } catch (_: SQLiteConstraintException) {
            // The outer Room transaction must roll back the preceding session and target insert.
        }

        assertNull(database.workoutSessionDao().getActiveSession())
        assertTrue(database.progressionDao().getTargetsForSession(1L).isEmpty())
    }
}
