package com.ironlog.shared.readiness

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Focused regression tests for the corrected scheme-aware comparison metric.
 *
 * They pin the concrete bugs that were fixed: TOTAL_REPS must sum repetitions,
 * LINEAR_LOAD must not punish a raised goal, a raised rep target must reset the
 * series instead of reading as fatigue, missing RPE must not move the metric,
 * planned failure is neutral, and several slots of one session count once.
 */
class TrainingTrendEngineRegressionTest {

    // 1) TOTAL_REPS: same top-set load, decreasing total repetitions.
    @Test
    fun totalRepsSchemeDetectsDecliningRepSumAtConstantLoad() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 1, dayOffset = 0, scheme = ProgressionScheme.TOTAL_REPS, sets = listOf(set(reps = 10, weightKg = 50.0), set(reps = 10, weightKg = 50.0), set(reps = 10, weightKg = 50.0))),
                unit(session = 2, dayOffset = 2, scheme = ProgressionScheme.TOTAL_REPS, sets = listOf(set(reps = 10, weightKg = 50.0), set(reps = 8, weightKg = 50.0), set(reps = 8, weightKg = 50.0))),
                unit(session = 3, dayOffset = 4, scheme = ProgressionScheme.TOTAL_REPS, sets = listOf(set(reps = 10, weightKg = 50.0), set(reps = 6, weightKg = 50.0), set(reps = 6, weightKg = 50.0))),
            ),
        )

        val exercise = trend.exercises.single()
        assertEquals(TrendMetricKind.TOTAL_REPS, exercise.metricKind)
        assertEquals(30.0, exercise.baselineMetricValue!!, absoluteTolerance = 1e-6)
        assertEquals(22.0, exercise.latestMetricValue!!, absoluteTolerance = 1e-6)
        assertEquals(ExerciseTrendStatus.DECLINING, exercise.status)
        assertContains(exercise.reasons.map { it.code }, ReadinessReasonCode.REPEATED_NOTABLE_DECLINE)
    }

    // 2) A raised rep target with unchanged performance must not read as fatigue.
    @Test
    fun raisedRepTargetResetsSeriesInsteadOfReportingFatigue() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 1, dayOffset = 0, scheme = ProgressionScheme.LINEAR_LOAD, targetRepsMin = 10, sets = listOf(set(reps = 10, weightKg = 60.0))),
                unit(session = 2, dayOffset = 2, scheme = ProgressionScheme.LINEAR_LOAD, targetRepsMin = 10, sets = listOf(set(reps = 10, weightKg = 60.0))),
                unit(session = 3, dayOffset = 4, scheme = ProgressionScheme.LINEAR_LOAD, targetRepsMin = 12, sets = listOf(set(reps = 10, weightKg = 60.0))),
            ),
        )

        val exercise = trend.exercises.single()
        assertFalse(exercise.status == ExerciseTrendStatus.DECLINING, "a raised target is not fatigue")
        assertEquals(ExerciseTrendStatus.INSUFFICIENT_DATA, exercise.status)
        assertContains(exercise.reasons.map { it.code }, ReadinessReasonCode.GOAL_CHANGED_RESET)
        assertContains(trend.dataQuality.notes, ReadinessDataQualityNote.GOAL_RESET_DETECTED)
    }

    // 3) LINEAR_LOAD: absolute load rises in explicit 1.5 kg steps.
    @Test
    fun linearLoadRecognisesExplicitOnePointFiveKgProgression() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 1, dayOffset = 0, scheme = ProgressionScheme.LINEAR_LOAD, targetRepsMin = 5, stepKg = 1.5, sets = listOf(set(reps = 5, weightKg = 30.0))),
                unit(session = 2, dayOffset = 2, scheme = ProgressionScheme.LINEAR_LOAD, targetRepsMin = 5, stepKg = 1.5, sets = listOf(set(reps = 5, weightKg = 31.5))),
                unit(session = 3, dayOffset = 4, scheme = ProgressionScheme.LINEAR_LOAD, targetRepsMin = 5, stepKg = 1.5, sets = listOf(set(reps = 5, weightKg = 33.0))),
            ),
        )

        val exercise = trend.exercises.single()
        assertEquals(ExerciseTrendStatus.IMPROVING, exercise.status)
        val changePercent = assertNotNull(exercise.changePercent)
        assertTrue(changePercent > 0.0)
        assertContains(exercise.reasons.map { it.code }, ReadinessReasonCode.IMPROVING_TREND)
    }

    // 4) Missing RPE must not move the performance metric.
    @Test
    fun missingRpeDoesNotChangeThePerformanceMetric() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 1, dayOffset = 0, targetRpe = 8.0, sets = listOf(set(reps = 5, weightKg = 80.0, rpe = 7.0))),
                unit(session = 2, dayOffset = 2, targetRpe = 8.0, sets = listOf(set(reps = 5, weightKg = 80.0, rpe = 7.0))),
                unit(session = 3, dayOffset = 4, targetRpe = 8.0, sets = listOf(set(reps = 5, weightKg = 80.0, rpe = null))),
            ),
        )

        val exercise = trend.exercises.single()
        assertEquals(ExerciseTrendStatus.STABLE, exercise.status)
        assertEquals(exercise.baselineMetricValue!!, exercise.latestMetricValue!!, absoluteTolerance = 1e-9)
        assertEquals(0.0, exercise.changePercent!!, absoluteTolerance = 1e-9)
        assertContains(trend.dataQuality.notes, ReadinessDataQualityNote.MISSING_RPE_TREATED_NEUTRAL)
    }

    // 5) Planned failure is neutral, never an unexpected deterioration.
    @Test
    fun plannedFailureUnitIsNeutralForTheDeclineTest() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 1, dayOffset = 0, sets = listOf(set(reps = 5, weightKg = 100.0, rpe = 7.0))),
                unit(session = 2, dayOffset = 2, sets = listOf(set(reps = 5, weightKg = 95.0, rpe = 8.0))),
                unit(
                    session = 3,
                    dayOffset = 4,
                    sets = listOf(set(reps = 5, weightKg = 50.0, rpe = 10.0, intention = SetIntentionCodes.PLANNED_FAILURE)),
                ),
            ),
        )

        val exercise = trend.exercises.single()
        assertEquals(ExerciseTrendStatus.STABLE, exercise.status)
        assertTrue(exercise.repeatedDeclineCount < 2)
        assertFalse(exercise.reasons.any { it.code == ReadinessReasonCode.REPEATED_NOTABLE_DECLINE })
        assertContains(exercise.reasons.map { it.code }, ReadinessReasonCode.PLANNED_FAILURE_UNIT_NEUTRAL)
    }

    // 6) Several slots of the same exercise in one session are one unit.
    @Test
    fun multipleSlotsInOneSessionCountAsOneComparableUnit() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 10, dayOffset = 0, slotIndex = 0, sets = listOf(set(reps = 5, weightKg = 60.0))),
                unit(session = 10, dayOffset = 0, slotIndex = 1, sets = listOf(set(reps = 5, weightKg = 60.0))),
                unit(session = 20, dayOffset = 2, sets = listOf(set(reps = 5, weightKg = 60.0), set(reps = 5, weightKg = 60.0))),
                unit(session = 30, dayOffset = 4, sets = listOf(set(reps = 5, weightKg = 60.0), set(reps = 5, weightKg = 60.0))),
            ),
        )

        val exercise = trend.exercises.single()
        assertEquals(3, exercise.comparableUnitCount, "two slots in one session must not count twice")
        assertContains(trend.dataQuality.notes, ReadinessDataQualityNote.MULTIPLE_SLOTS_IN_SESSION)
        assertContains(trend.reasons.map { it.code }, ReadinessReasonCode.MULTIPLE_SLOTS_IN_SESSION_MERGED)
    }

    // 7) No data means no index, not a plausible number.
    @Test
    fun noHistoryYieldsNullIndex() {
        val trend = TrainingTrendEngine.assess(ReadinessInput(nowEpochMillis = NOW))

        assertNull(trend.trainingIndex)
        assertEquals(TrainingTrendStatus.INSUFFICIENT_DATA, trend.status)
        assertEquals(EvidenceConfidence.NONE, trend.confidence)
    }

    // 8) High-rep fallback also separates on a changed real load.
    @Test
    fun highRepFallbackDoesNotReadHeavierLoadAndFewerRepsAsFatigue() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 1, dayOffset = 0, sets = listOf(set(reps = 15, weightKg = 10.0))),
                unit(session = 2, dayOffset = 2, sets = listOf(set(reps = 13, weightKg = 15.0))),
            ),
        )

        val exercise = trend.exercises.single()
        assertEquals(TrendMetricKind.TOTAL_REPS, exercise.metricKind)
        assertFalse(exercise.status == ExerciseTrendStatus.DECLINING, "heavier load with fewer reps is not fatigue")
        assertEquals(ExerciseTrendStatus.INSUFFICIENT_DATA, exercise.status)
        assertContains(exercise.reasons.map { it.code }, ReadinessReasonCode.GOAL_CHANGED_RESET)
    }

    // 9) A different set-type distribution is not the same comparable unit.
    @Test
    fun differentSetTypeDistributionStartsANewSeries() {
        val trend = TrainingTrendEngine.assess(
            input(
                unit(session = 1, dayOffset = 0, sets = listOf(set(reps = 5, weightKg = 80.0))),
                unit(session = 2, dayOffset = 2, sets = listOf(set(reps = 5, weightKg = 80.0))),
                unit(
                    session = 3,
                    dayOffset = 4,
                    sets = listOf(set(reps = 5, weightKg = 80.0, setType = SetType.FAILURE)),
                ),
            ),
        )

        val exercise = trend.exercises.single()
        assertFalse(exercise.status == ExerciseTrendStatus.DECLINING)
        assertContains(exercise.reasons.map { it.code }, ReadinessReasonCode.METRIC_KIND_CHANGED_RESET)
    }

    @Test
    fun conflictingSlotPrescriptionsDoNotFabricateComparableHistory() {
        val rows = (1L..3L).flatMap { session -> listOf(
            unit(session, session.toInt(), targetRepsMin = 5, sets = listOf(set(5, 50.0))),
            unit(session, session.toInt(), targetRepsMin = 12, slotIndex = 1, sets = listOf(set(12, 20.0))),
        ) }
        val trend = TrainingTrendEngine.assess(ReadinessInput(nowEpochMillis = NOW, sessions = rows))
        assertEquals(1, trend.exercises.single().comparableUnitCount)
        assertNull(trend.trainingIndex)
    }

    @Test
    fun plannedMusclesStayDistinctFromHistoricalMuscles() {
        val assessment = ReadinessEngine.assess(ReadinessInput(
            nowEpochMillis = NOW,
            todaySession = TodaySessionInput(sessionId = 42, startedAtEpochMillis = NOW, muscleGroups = listOf("BRUST")),
            muscleLoadHistory = listOf(MuscleLoadEntry(muscleGroup = "BEINE", sessionId = 1, completedAtEpochMillis = NOW - DAY_MS, completedSets = 3.0)),
        ))
        assertTrue(assessment.muscleGroups.single { it.muscleGroup == "BRUST" }.plannedToday)
        assertFalse(assessment.muscleGroups.single { it.muscleGroup == "BEINE" }.plannedToday)
    }

    @Test
    fun ancientAndFutureHistoryNeverProduceCurrentIndex() {
        val old = unit(session = 1, dayOffset = 0, sets = listOf(set(5, 50.0)))
        val trend = TrainingTrendEngine.assess(input(
            old.copy(completedAtEpochMillis = NOW - 29 * DAY_MS),
            old.copy(sessionId = 2, completedAtEpochMillis = NOW + DAY_MS),
        ))
        assertNull(trend.trainingIndex)
        assertEquals(0, trend.analyzedExerciseCount)
    }

    // -----------------------------------------------------------------------------
    // Builders
    // -----------------------------------------------------------------------------

    private fun input(vararg units: ExerciseSessionInput): ReadinessInput =
        ReadinessInput(nowEpochMillis = NOW, sessions = units.toList())

    private fun unit(
        session: Long,
        dayOffset: Int,
        exerciseId: Long = 1L,
        sets: List<TrendSetInput>,
        scheme: ProgressionScheme = ProgressionScheme.UNKNOWN,
        targetRepsMin: Int? = null,
        targetRepsMax: Int? = null,
        targetRpe: Double? = null,
        stepKg: Double? = null,
        slotIndex: Int = 0,
    ): ExerciseSessionInput = ExerciseSessionInput(
        sessionId = session,
        exerciseId = exerciseId,
        exerciseName = "Uebung $exerciseId",
        equipmentType = EquipmentType.BARBELL,
        completedAtEpochMillis = NOW - (10L - dayOffset) * DAY_MS,
        orderingToken = session,
        slotIndex = slotIndex,
        goal = ProgressionGoal(
            targetRepsMin = targetRepsMin,
            targetRepsMax = targetRepsMax,
            targetRpe = targetRpe,
            weightIncrementKg = stepKg,
            scheme = scheme,
        ),
        sets = sets,
    )

    private fun set(
        reps: Int,
        weightKg: Double,
        rpe: Double? = 7.0,
        setType: SetType = SetType.NORMAL,
        intention: String = SetIntentionCodes.UNKNOWN,
    ): TrendSetInput = TrendSetInput(
        setNumber = 1,
        reps = reps,
        weightKg = weightKg,
        rpe = rpe,
        setType = setType,
        intention = intention,
        completedAtEpochMillis = NOW,
        id = reps * 1000L + weightKg.toLong(),
    )

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val NOW = 100L * DAY_MS
    }
}
