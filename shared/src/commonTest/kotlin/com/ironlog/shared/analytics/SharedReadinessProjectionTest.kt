package com.ironlog.shared.analytics

import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupPlanExercise
import com.ironlog.shared.backup.BackupProgressionConfig
import com.ironlog.shared.backup.BackupProgressionTarget
import com.ironlog.shared.backup.BackupWorkoutSession
import com.ironlog.shared.backup.BackupWorkoutPlanTarget
import com.ironlog.shared.backup.BackupWorkoutSet
import com.ironlog.shared.backup.FAILURE_SET_TYPE
import com.ironlog.shared.backup.NORMAL_SET_TYPE
import com.ironlog.shared.model.MuscleGroup
import com.ironlog.shared.readiness.DailyFormDimension
import com.ironlog.shared.readiness.DailyFormState
import com.ironlog.shared.readiness.EquipmentType
import com.ironlog.shared.readiness.MuscleGroupFlag
import com.ironlog.shared.readiness.ReadinessDataQualityNote
import com.ironlog.shared.readiness.ReadinessReasonCode
import com.ironlog.shared.readiness.TrendMetricKind
import com.ironlog.shared.readiness.TrainingTrendStatus
import com.ironlog.shared.readinessdata.CURRENT_READINESS_DATA_SCHEMA_VERSION
import com.ironlog.shared.readinessdata.READINESS_DATA_FORMAT_VERSION
import com.ironlog.shared.readinessdata.ReadinessCheckIn
import com.ironlog.shared.readinessdata.ReadinessData
import com.ironlog.shared.readinessdata.SetIntention
import com.ironlog.shared.readinessdata.SetIntentionRecord
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Targeted adapter tests. They cover the mapping decisions that are easy to get
 * wrong silently: no fabricated index, an explicit deload context, the intention
 * axis, the daily-form dimensions and the muscle load weighting.
 */
class SharedReadinessProjectionTest {

    private val now = epoch("2026-09-11T18:00:00Z")

    @Test
    fun `insufficient history never fabricates a training index`() {
        val assessment = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY)),
                sets = (1..3).map { set(id = it.toLong(), sessionId = 1L, completedAt = now - DAY) },
            ),
        )

        assertEquals(TrainingTrendStatus.INSUFFICIENT_DATA, assessment.trainingTrend.status)
        assertNull(assessment.trainingTrend.trainingIndex)
        assertTrue(
            assessment.trainingTrend.reasons.any { it.code == ReadinessReasonCode.INSUFFICIENT_HISTORY },
        )
    }

    @Test
    fun `only an explicit deload flag removes a unit from the series`() {
        val flagged = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY, isDeload = true)),
                sets = (1..3).map { set(id = it.toLong(), sessionId = 1L, completedAt = now - DAY) },
            ),
        )
        assertEquals(1, flagged.trainingTrend.dataQuality.excludedDeloadUnitCount)
        assertTrue(
            flagged.trainingTrend.dataQuality.notes.contains(
                ReadinessDataQualityNote.DELOAD_UNITS_EXCLUDED,
            ),
        )

        // A legacy session (null) stays UNKNOWN and is not retroactively excluded.
        val legacy = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY, isDeload = null)),
                sets = (1..3).map { set(id = it.toLong(), sessionId = 1L, completedAt = now - DAY) },
            ),
        )
        assertEquals(0, legacy.trainingTrend.dataQuality.excludedDeloadUnitCount)
        assertFalse(
            legacy.trainingTrend.dataQuality.notes.contains(
                ReadinessDataQualityNote.DELOAD_UNITS_EXCLUDED,
            ),
        )
    }

    @Test
    fun `intention comes only from the stored records`() {
        val assessment = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY)),
                sets = listOf(
                    set(id = 1L, sessionId = 1L, completedAt = now - DAY, setType = FAILURE_SET_TYPE),
                    set(id = 2L, sessionId = 1L, completedAt = now - DAY),
                ),
                readinessData = readinessData(
                    setIntentions = listOf(
                        SetIntentionRecord(setId = 1L, intention = SetIntention.PLANNED_FAILURE),
                    ),
                ),
            ),
        )

        // Only the set without a record is unknown: the FAILURE set is neutral
        // because the record explicitly says the failure was planned.
        assertEquals(1, assessment.trainingTrend.dataQuality.unknownIntentionSetCount)
        assertTrue(
            assessment.trainingTrend.reasons.any { it.code == ReadinessReasonCode.PLANNED_FAILURE_NEUTRAL },
        )
        assertTrue(
            assessment.trainingTrend.dataQuality.notes.contains(
                ReadinessDataQualityNote.UNKNOWN_SET_INTENTION,
            ),
        )
    }

    @Test
    fun `daily form reports each answered dimension and never invents soreness`() {
        val assessment = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY)),
                sets = (1..3).map { set(id = it.toLong(), sessionId = 1L, completedAt = now - DAY) },
                readinessData = readinessData(
                    checkIns = listOf(
                        ReadinessCheckIn(
                            localDate = LocalDate.parse("2026-09-11"),
                            sleepQuality = 2,
                            energy = 4,
                        ),
                    ),
                ),
            ),
        )

        val dailyForm = requireNotNull(assessment.dailyForm)
        assertEquals(2, dailyForm.coverage)
        assertTrue(dailyForm.hasAnyConcern)
        val byDimension = dailyForm.signals.associate { it.dimension to it.state }
        assertEquals(DailyFormState.CONCERN, byDimension[DailyFormDimension.SLEEP_QUALITY])
        assertEquals(DailyFormState.GOOD, byDimension[DailyFormDimension.ENERGY])
        assertFalse(byDimension.containsKey(DailyFormDimension.SORENESS))
    }

    @Test
    fun `per-muscle soreness reaches the muscle context of today`() {
        val assessment = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - 3_600_000L)),
                sets = (1..3).map {
                    set(id = it.toLong(), sessionId = 1L, completedAt = now - 3_600_000L)
                },
                readinessData = readinessData(
                    checkIns = listOf(
                        ReadinessCheckIn(
                            localDate = LocalDate.parse("2026-09-11"),
                            muscleSoreness = mapOf(MuscleGroup.BRUST to 5),
                        ),
                    ),
                ),
            ),
        )

        val chest = requireNotNull(assessment.muscleGroups.firstOrNull { it.muscleGroup == "BRUST" })
        assertEquals(5, chest.soreness)
        assertEquals(3.0, chest.setsToday)
        assertTrue(chest.flags.contains(MuscleGroupFlag.TRAINED_TODAY))
        assertTrue(chest.flags.contains(MuscleGroupFlag.HIGH_SORENESS))
    }

    @Test
    fun `muscle load weights primary with one and secondary with half`() {
        val assessment = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - 3_600_000L)),
                sets = (1..2).map {
                    set(id = it.toLong(), sessionId = 1L, completedAt = now - 3_600_000L)
                },
            ),
        )

        assertEquals(2.0, assessment.muscleGroups.first { it.muscleGroup == "BRUST" }.setsInWindow)
        assertEquals(1.0, assessment.muscleGroups.first { it.muscleGroup == "TRIZEPS" }.setsInWindow)
    }

    @Test
    fun `setsInWindow is a rolling window and not the calendar week`() {
        // One session inside the trailing 7 days and one outside it. The weekly volume
        // card bins by calendar week; this readiness value must not silently pretend to
        // be that number, so only the session inside the rolling window counts.
        val assessment = assess(
            payload(
                sessions = listOf(
                    session(id = 1L, start = now - 8L * DAY),
                    session(id = 2L, start = now - 6L * DAY),
                ),
                sets = listOf(
                    set(id = 1L, sessionId = 1L, completedAt = now - 8L * DAY),
                    set(id = 2L, sessionId = 2L, completedAt = now - 6L * DAY),
                    set(id = 3L, sessionId = 2L, completedAt = now - 6L * DAY),
                ),
            ),
        )

        assertEquals(2.0, assessment.muscleGroups.first { it.muscleGroup == "BRUST" }.setsInWindow)
    }

    @Test
    fun `exercise category maps to a comparable device class`() {
        val assessment = assess(
            payload(
                exercises = listOf(exercise(category = "KURZHANTEL")),
                sessions = listOf(session(id = 1L, start = now - DAY)),
                sets = (1..3).map { set(id = it.toLong(), sessionId = 1L, completedAt = now - DAY) },
            ),
        )

        assertEquals(
            EquipmentType.DUMBBELL,
            assessment.trainingTrend.exercises.first().equipmentType,
        )
    }

    @Test
    fun `a session that has not ended yet stays out of the series`() {
        val assessment = assess(
            payload(
                sessions = listOf(
                    session(id = 1L, start = now - 3_600_000L, end = now + 3_600_000L),
                ),
                sets = (1..3).map {
                    set(id = it.toLong(), sessionId = 1L, completedAt = now - 3_600_000L)
                },
            ),
        )

        assertTrue(assessment.trainingTrend.exercises.isEmpty())
        assertEquals(0, assessment.trainingTrend.dataQuality.analyzedExerciseCount)
        assertEquals(TrainingTrendStatus.INSUFFICIENT_DATA, assessment.trainingTrend.status)
    }

    @Test
    fun `a goal is only taken from the immutable session snapshot`() {
        val planRow = planExercise()

        // The plan row is present, but the session has no target snapshot. The goal has to
        // stay unknown, so the trend must not switch to the plan-relative goal score.
        val withoutSnapshot = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY, planId = 1L)),
                sets = (1..3).map { set(id = it.toLong(), sessionId = 1L, completedAt = now - DAY) },
                planExercises = listOf(planRow),
            ),
        )
        assertEquals(
            TrendMetricKind.ESTIMATED_ONE_REP_MAX,
            withoutSnapshot.trainingTrend.exercises.first().metricKind,
        )

        val withSnapshot = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY, planId = 1L)),
                sets = (1..3).map { set(id = it.toLong(), sessionId = 1L, completedAt = now - DAY) },
                planExercises = listOf(planRow),
                workoutPlanTargets = listOf(
                    BackupWorkoutPlanTarget(
                        id = 1L,
                        sessionId = 1L,
                        planId = 1L,
                        exerciseId = 1L,
                        orderIndex = 0,
                        target = BackupProgressionTarget(sets = 3, reps = 10, weightKg = 100.0),
                        progression = linearProgression(),
                    ),
                ),
            ),
        )
        assertEquals(
            TrendMetricKind.GOAL_SCORE,
            withSnapshot.trainingTrend.exercises.first().metricKind,
        )
    }

    @Test
    fun `planned muscles and rest day soreness are visible before a session ends`() {
        // Before the workout starts: the caller names the plan the athlete is about to train.
        val planned = assess(
            payload(planExercises = listOf(planExercise())),
            selectedPlanId = 1L,
        )
        assertEquals(listOf("BRUST"), planned.muscleGroups.map { it.muscleGroup })
        assertEquals(0.0, planned.muscleGroups.first().setsToday)

        // A running workout contributes its planned muscles before it is finished.
        val running = assess(
            payload(
                sessions = listOf(session(id = 2L, start = now - 600_000L, end = null, planId = 1L)),
                planExercises = listOf(planExercise()),
            ),
        )
        assertTrue(running.muscleGroups.any { it.muscleGroup == "BRUST" })

        // On a rest day the check-in soreness still shows up, without a session or sets.
        val restDay = assess(
            payload(
                readinessData = readinessData(
                    checkIns = listOf(
                        ReadinessCheckIn(
                            localDate = LocalDate.parse("2026-09-11"),
                            muscleSoreness = mapOf(MuscleGroup.BRUST to 4),
                        ),
                    ),
                ),
            ),
        )
        val chest = requireNotNull(restDay.muscleGroups.firstOrNull { it.muscleGroup == "BRUST" })
        assertEquals(4, chest.soreness)
        assertEquals(0.0, chest.setsToday)
        assertTrue(chest.flags.contains(MuscleGroupFlag.HIGH_SORENESS))
    }

    @Test
    fun `two slots of one exercise stay two rows and are reported as a merge`() {
        val assessment = assess(
            payload(
                sessions = listOf(session(id = 1L, start = now - DAY, planId = 1L)),
                sets = listOf(
                    set(id = 1L, sessionId = 1L, completedAt = now - DAY, planTargetSnapshotId = 10L),
                    set(id = 2L, sessionId = 1L, completedAt = now - DAY, planTargetSnapshotId = 10L),
                    set(id = 3L, sessionId = 1L, completedAt = now - DAY, planTargetSnapshotId = 20L),
                ),
                planExercises = listOf(
                    planExercise(orderIndex = 0),
                    planExercise(id = 2L, orderIndex = 2),
                ),
                workoutPlanTargets = listOf(
                    target(id = 10L, orderIndex = 0, reps = 10, weightKg = 100.0),
                    target(id = 20L, orderIndex = 2, reps = 5, weightKg = 120.0),
                ),
            ),
        )

        // The adapter handed the core two rows for one session, so the core collapses them
        // into one unit and says so. A collapsed adapter would never produce this.
        assertTrue(
            assessment.trainingTrend.reasons.any {
                it.code == ReadinessReasonCode.MULTIPLE_SLOTS_IN_SESSION_MERGED
            },
        )
        assertTrue(
            assessment.trainingTrend.dataQuality.notes.contains(
                ReadinessDataQualityNote.MULTIPLE_SLOTS_IN_SESSION,
            ),
        )
    }

    @Test
    fun `the same slots in consecutive sessions do not fabricate a slot reset`() {
        val assessment = assess(
            payload(
                sessions = listOf(
                    session(id = 1L, start = now - 3L * DAY, planId = 1L),
                    session(id = 2L, start = now - 2L * DAY, planId = 1L),
                ),
                sets = listOf(
                    set(id = 1L, sessionId = 1L, completedAt = now - 3L * DAY, planTargetSnapshotId = 10L),
                    set(id = 2L, sessionId = 1L, completedAt = now - 3L * DAY, planTargetSnapshotId = 20L),
                    set(id = 3L, sessionId = 2L, completedAt = now - 2L * DAY, planTargetSnapshotId = 21L),
                    set(id = 4L, sessionId = 2L, completedAt = now - 2L * DAY, planTargetSnapshotId = 22L),
                ),
                planExercises = listOf(
                    planExercise(orderIndex = 0),
                    planExercise(id = 2L, orderIndex = 2),
                ),
                workoutPlanTargets = listOf(
                    target(id = 10L, orderIndex = 0, reps = 10, weightKg = 100.0),
                    target(id = 20L, orderIndex = 2, reps = 5, weightKg = 120.0),
                    target(id = 21L, sessionId = 2L, orderIndex = 0, reps = 10, weightKg = 100.0),
                    target(id = 22L, sessionId = 2L, orderIndex = 2, reps = 5, weightKg = 120.0),
                ),
            ),
        )

        assertFalse(
            assessment.trainingTrend.dataQuality.notes.contains(
                ReadinessDataQualityNote.EXERCISE_SLOT_CHANGED,
            ),
        )
    }

    @Test
    fun `an unprovable slot stays neutral instead of adopting a guessed snapshot`() {
        // Two slots exist for the exercise, but the second session's sets name none of
        // them. The slot is not provable, so no snapshot may be adopted as the goal.
        val assessment = assess(
            payload(
                sessions = listOf(
                    session(id = 1L, start = now - 3L * DAY, planId = 1L),
                    session(id = 2L, start = now - 2L * DAY, planId = 1L),
                ),
                sets = listOf(
                    set(id = 1L, sessionId = 1L, completedAt = now - 3L * DAY, planTargetSnapshotId = 10L),
                    set(id = 2L, sessionId = 2L, completedAt = now - 2L * DAY),
                    set(id = 3L, sessionId = 2L, completedAt = now - 2L * DAY),
                ),
                planExercises = listOf(
                    planExercise(orderIndex = 0),
                    planExercise(id = 2L, orderIndex = 2),
                ),
                workoutPlanTargets = listOf(
                    target(id = 10L, orderIndex = 0, reps = 10, weightKg = 100.0),
                    target(id = 21L, sessionId = 2L, orderIndex = 0, reps = 10, weightKg = 100.0),
                    target(id = 22L, sessionId = 2L, orderIndex = 2, reps = 5, weightKg = 120.0),
                ),
            ),
        )

        // The ambiguous unit is a neutral reset, not a comparison against a guessed slot.
        assertTrue(
            assessment.trainingTrend.dataQuality.notes.contains(
                ReadinessDataQualityNote.EXERCISE_SLOT_CHANGED,
            ),
        )
        // Without a provable snapshot the metric stays the scheme-independent e1RM; an
        // arbitrary "last" snapshot would have produced a goal score instead.
        assertEquals(
            TrendMetricKind.ESTIMATED_ONE_REP_MAX,
            assessment.trainingTrend.exercises.first().metricKind,
        )
    }

    // --- helpers ---------------------------------------------------------------

    private fun assess(payload: BackupPayloadV1, selectedPlanId: Long? = null) =
        SharedReadinessProjection.assess(
            payload = payload,
            nowEpochMillis = now,
            timeZoneId = "UTC",
            selectedPlanId = selectedPlanId,
        )

    private fun payload(
        exercises: List<BackupExercise> = listOf(exercise()),
        sessions: List<BackupWorkoutSession> = emptyList(),
        sets: List<BackupWorkoutSet> = emptyList(),
        planExercises: List<BackupPlanExercise> = emptyList(),
        workoutPlanTargets: List<BackupWorkoutPlanTarget> = emptyList(),
        readinessData: ReadinessData = ReadinessData(),
    ) = BackupPayloadV1(
        formatVersion = 1,
        schemaVersion = 13,
        appVersion = "test",
        exportedAtEpochMillis = now,
        exercises = exercises,
        workoutSessions = sessions,
        workoutSets = sets,
        trainingPlans = emptyList(),
        planExercises = planExercises,
        workoutPlanTargets = workoutPlanTargets,
        personalRecords = emptyList(),
        readinessData = readinessData,
    )

    private fun planExercise(id: Long = 1L, orderIndex: Int = 0) = BackupPlanExercise(
        id = id,
        planId = 1L,
        exerciseId = 1L,
        orderIndex = orderIndex,
        targetSets = 3,
        targetReps = 10,
        targetWeightKg = 100.0,
        progression = linearProgression(),
    )

    private fun target(
        id: Long,
        orderIndex: Int,
        sessionId: Long = 1L,
        reps: Int = 10,
        weightKg: Double = 100.0,
    ) = BackupWorkoutPlanTarget(
        id = id,
        sessionId = sessionId,
        planId = 1L,
        exerciseId = 1L,
        orderIndex = orderIndex,
        target = BackupProgressionTarget(sets = 3, reps = reps, weightKg = weightKg),
        progression = linearProgression(),
    )

    private fun linearProgression() = BackupProgressionConfig(
        scheme = "LINEAR",
        incrementKg = 2.5,
        minReps = 10,
        maxReps = 10,
    )

    private fun readinessData(
        checkIns: List<ReadinessCheckIn> = emptyList(),
        setIntentions: List<SetIntentionRecord> = emptyList(),
    ) = ReadinessData(
        formatVersion = READINESS_DATA_FORMAT_VERSION,
        schemaVersion = CURRENT_READINESS_DATA_SCHEMA_VERSION,
        checkIns = checkIns,
        setIntentions = setIntentions,
    )

    private fun exercise(category: String = "LANGHANTEL") = BackupExercise(
        id = 1L,
        name = "Bankdruecken",
        primaryMuscleGroup = "BRUST",
        secondaryMuscleGroups = "TRIZEPS",
        category = category,
        isCustom = false,
    )

    private fun session(
        id: Long,
        start: Long,
        end: Long? = start + 3_600_000L,
        isDeload: Boolean? = null,
        planId: Long? = null,
    ) = BackupWorkoutSession(
        id = id,
        startTime = start,
        endTime = end,
        durationSeconds = 3_600L,
        name = "Workout",
        notes = "",
        planId = planId,
        isDeload = isDeload,
    )

    private fun set(
        id: Long,
        sessionId: Long,
        completedAt: Long,
        setType: String = NORMAL_SET_TYPE,
        weightKg: Double = 100.0,
        reps: Int = 8,
        planTargetSnapshotId: Long? = null,
    ) = BackupWorkoutSet(
        id = id,
        sessionId = sessionId,
        exerciseId = 1L,
        setNumber = id.toInt(),
        reps = reps,
        weightKg = weightKg,
        setType = setType,
        completedAt = completedAt,
        planTargetSnapshotId = planTargetSnapshotId,
    )

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L

        fun epoch(value: String): Long = Instant.parse(value).toEpochMilliseconds()
    }
}
