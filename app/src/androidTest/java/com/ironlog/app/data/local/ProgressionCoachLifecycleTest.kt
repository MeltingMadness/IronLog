package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.data.db.RoomTransactionRunner
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.repository.ProgressionRepositoryImpl
import com.ironlog.app.data.repository.TrainingPlanRepositoryImpl
import com.ironlog.app.data.repository.WorkoutRepositoryImpl
import com.ironlog.app.domain.model.FailurePolicy
import com.ironlog.app.domain.model.PlanExercise
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.progression.ProgressionEngine
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressionCoachLifecycleTest {

    private lateinit var database: IronLogDatabase
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var progressionRepository: ProgressionRepositoryImpl
    private lateinit var trainingPlanRepository: TrainingPlanRepositoryImpl
    private var exerciseId: Long = 0L
    private var nextNowEpochMillis = FIXED_NOW_EPOCH_MILLIS

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            IronLogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()

        val transactionRunner = RoomTransactionRunner(database)
        workoutRepository = WorkoutRepositoryImpl(
            database.workoutSessionDao(),
            database.workoutSetDao(),
            database.personalRecordDao(),
            database.trainingPlanDao(),
            database.progressionDao(),
            transactionRunner
        )
        progressionRepository = ProgressionRepositoryImpl(
            progressionDao = database.progressionDao(),
            sessionDao = database.workoutSessionDao(),
            setDao = database.workoutSetDao(),
            trainingPlanDao = database.trainingPlanDao(),
            engine = ProgressionEngine(),
            transactionRunner = transactionRunner,
            nowEpochMillis = { nextNowEpochMillis++ }
        )
        trainingPlanRepository = TrainingPlanRepositoryImpl(database.trainingPlanDao())
        exerciseId = database.exerciseDao().insert(
            ExerciseEntity(
                name = "Lifecycle squat",
                primaryMuscleGroup = "BEINE",
                secondaryMuscleGroups = "",
                category = "LANGHANTEL"
            )
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun acceptedLinearSuggestionUpdatesReopenedPlanButPreservesSourceSnapshot() = runBlocking {
        val planId = trainingPlanRepository.savePlan(linearPlan(weightKg = 100.0, stepKg = 2.5))
        val sessionId = workoutRepository.startWorkout("Progression lifecycle", planId, null)
        val source = database.progressionDao().getTargetsForSession(sessionId).single()
        assertEquals(ProgressionTarget(sets = 3, reps = 8, weightKg = 100.0), source.target.toDomain())
        assertEquals(linearConfig(stepKg = 2.5), source.progression.toDomain())

        val setIds = (1..3).map { setNumber ->
            workoutRepository.addSet(
                workoutSet(
                    sessionId = sessionId,
                    exerciseId = source.exerciseId,
                    setNumber = setNumber,
                    reps = 8,
                    weightKg = 100.0,
                    snapshotId = source.id
                )
            )
        }
        workoutRepository.finishWorkout(sessionId)

        val generated = progressionRepository.generateOutcomesForSession(sessionId)
        assertEquals(1, generated.reviewItemCount)
        assertEquals(1, generated.pendingCount)
        val repeated = progressionRepository.generateOutcomesForSession(sessionId)
        assertEquals(0, repeated.insertedCount)
        assertEquals(1, progressionRepository.observeReviewItems(sessionId).first().size)
        val suggestion = progressionRepository.observeReviewItems(sessionId).first().single()
        assertEquals(setIds, suggestion.countedSets.map { it.id })
        val proposedTarget = requireNotNull(
            (suggestion.outcome as ProgressionOutcome.ProposeChange).proposedTarget
        )
        assertEquals(102.5, proposedTarget.weightKg, 0.000001)

        val accepted = progressionRepository.acceptSuggestions(
            mapOf(suggestion.id to proposedTarget)
        )
        assertEquals(ProgressionDecisionResult.Accepted(setOf(suggestion.id)), accepted)

        val reopened = requireNotNull(trainingPlanRepository.getPlanById(planId))
        assertEquals(102.5, reopened.exercises.single().targetWeightKg, 0.000001)
        assertEquals(
            100.0,
            database.progressionDao().getTargetsForSession(sessionId).single().target.weightKg,
            0.0
        )
        assertEquals(
            ProgressionSuggestionStatus.ACCEPTED,
            progressionRepository.observeReviewItems(sessionId).first().single().status
        )
    }

    private fun linearPlan(weightKg: Double, stepKg: Double) = TrainingPlan(
        name = "Linear lifecycle",
        exercises = listOf(
            PlanExercise(
                exerciseId = exerciseId,
                orderIndex = 0,
                targetSets = 3,
                targetReps = 8,
                targetWeightKg = weightKg,
                progressionConfig = linearConfig(stepKg)
            )
        )
    )

    private fun linearConfig(stepKg: Double) = ProgressionConfig.Linear(
        step = WeightStep(
            originalValue = stepKg,
            originalUnit = UnitSystem.METRIC,
            kilograms = stepKg
        ),
        failurePolicy = FailurePolicy(),
        ruleRevision = 1
    )

    private fun workoutSet(
        sessionId: Long,
        exerciseId: Long,
        setNumber: Int,
        reps: Int,
        weightKg: Double,
        snapshotId: Long
    ) = WorkoutSet(
        sessionId = sessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weightKg = weightKg,
        completedAt = FIXED_SET_TIME.plusSeconds(setNumber.toLong()),
        planTargetSnapshotId = snapshotId
    )

    private companion object {
        const val FIXED_NOW_EPOCH_MILLIS = 1_786_262_400_000L
        val FIXED_SET_TIME: LocalDateTime = LocalDateTime.of(2026, 8, 9, 12, 0)
    }
}
