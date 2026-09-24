package com.ironlog.shared.analytics

import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupMetaPlanItem
import com.ironlog.shared.backup.BackupMetaPlanSkip
import com.ironlog.shared.backup.BackupMetaTrainingPlan
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupPersonalRecord
import com.ironlog.shared.backup.BackupTrainingPlan
import com.ironlog.shared.backup.BackupWorkoutSession
import com.ironlog.shared.backup.BackupWorkoutSet
import com.ironlog.shared.backup.DROP_SET_TYPE
import com.ironlog.shared.backup.FAILURE_SET_TYPE
import com.ironlog.shared.backup.NORMAL_SET_TYPE
import com.ironlog.shared.backup.WARMUP_SET_TYPE
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedTrainingAnalyticsTest {

    @Test
    fun `calendar boundaries use requested timezone`() {
        val sessionStart = epoch("2026-09-01T00:30:00Z")
        val payload = payload(
            exercises = listOf(exercise()),
            sessions = listOf(session(id = 1L, start = sessionStart)),
            sets = listOf(
                set(
                    id = 1L,
                    sessionId = 1L,
                    setType = NORMAL_SET_TYPE,
                    completedAt = sessionStart,
                ),
            ),
        )

        val utc = summarize(payload, "UTC")
        val losAngeles = summarize(payload, "America/Los_Angeles")

        // September 1 is in the previous Monday-based week when `now` is
        // September 9. The month boundary is the behavior under test here.
        assertEquals(0, utc.workoutsThisWeek)
        assertEquals(1, utc.workoutsThisMonth)
        assertEquals("2026-09-01", utc.lastSessionDate)
        assertEquals(0, losAngeles.workoutsThisWeek)
        assertEquals(0, losAngeles.workoutsThisMonth)
        assertEquals("2026-08-31", losAngeles.lastSessionDate)

        // At the UTC Monday boundary the same instant belongs to Sunday in
        // Los Angeles. The current-week count must follow that local date.
        val weekBoundaryPayload = payload(
            exercises = listOf(exercise()),
            sessions = listOf(
                session(id = 2L, start = epoch("2026-09-06T23:30:00Z")),
            ),
            sets = listOf(
                set(
                    id = 2L,
                    sessionId = 2L,
                    setType = NORMAL_SET_TYPE,
                    completedAt = epoch("2026-09-06T23:31:00Z"),
                ),
            ),
        )
        val utcWeekBoundary = summarize(
            weekBoundaryPayload,
            "UTC",
            nowEpochMillis = epoch("2026-09-07T00:30:00Z"),
        )
        val losAngelesWeekBoundary = summarize(
            weekBoundaryPayload,
            "America/Los_Angeles",
            nowEpochMillis = epoch("2026-09-07T00:30:00Z"),
        )
        assertEquals(0, utcWeekBoundary.workoutsThisWeek)
        assertEquals(1, losAngelesWeekBoundary.workoutsThisWeek)
    }

    @Test
    fun `weighted muscle volume counts work set types and excludes warmup and active sessions`() {
        val now = epoch("2026-09-09T12:00:00Z")
        val payload = payload(
            exercises = listOf(exercise(primary = "BRUST", secondary = "TRIZEPS,SCHULTERN")),
            sessions = listOf(
                session(id = 1L, start = epoch("2026-09-08T10:00:00Z")),
                session(id = 2L, start = epoch("2026-09-08T11:00:00Z"), end = null),
            ),
            sets = listOf(
                set(1L, 1L, NORMAL_SET_TYPE, completedAt = epoch("2026-09-08T10:01:00Z")),
                set(2L, 1L, DROP_SET_TYPE, completedAt = epoch("2026-09-08T10:02:00Z")),
                set(3L, 1L, FAILURE_SET_TYPE, completedAt = epoch("2026-09-08T10:03:00Z")),
                set(4L, 1L, WARMUP_SET_TYPE, completedAt = epoch("2026-09-08T10:04:00Z")),
                set(5L, 2L, NORMAL_SET_TYPE, completedAt = epoch("2026-09-08T11:01:00Z")),
            ),
        )

        val result = SharedTrainingAnalytics.summarize(payload, now, "UTC")
        val chest = result.muscleVolumes.first { it.muscleGroup == "BRUST" }
        val triceps = result.muscleVolumes.first { it.muscleGroup == "TRIZEPS" }
        val shoulders = result.muscleVolumes.first { it.muscleGroup == "SCHULTERN" }

        assertEquals(3.0, chest.weeklySets)
        assertEquals(1.5, triceps.weeklySets)
        assertEquals(1.5, shoulders.weeklySets)
        assertEquals(1, result.weeklyVolume.last().workoutCount)
        assertEquals(3 * 100.0 * 8.0, result.weeklyVolume.last().volumeKg)

        val history = result.exerciseStatistics.single()
        val session = history.sessions.single()
        assertEquals(100.0, session.maxWeightKg)
        assertEquals(8, session.maxReps)
        assertEquals(100.0 * (1.0 + 8.0 / 30.0), session.maxE1rmKg)
        assertEquals(3 * 100.0 * 8.0, session.volumeKg)
    }

    @Test
    fun `exercise statistics expose progression comparison and bounded recent sets`() {
        val firstSessionStart = epoch("2026-09-01T10:00:00Z")
        val latestSessionStart = epoch("2026-09-08T10:00:00Z")
        val payload = payload(
            exercises = listOf(exercise()),
            sessions = listOf(
                session(id = 1L, start = firstSessionStart),
                session(id = 2L, start = latestSessionStart),
            ),
            sets = listOf(
                set(
                    id = 1L,
                    sessionId = 1L,
                    setType = NORMAL_SET_TYPE,
                    completedAt = firstSessionStart,
                    weightKg = 80.0,
                    reps = 5,
                ),
                set(
                    id = 2L,
                    sessionId = 2L,
                    setType = NORMAL_SET_TYPE,
                    completedAt = latestSessionStart,
                    weightKg = 90.0,
                    reps = 3,
                ),
                set(
                    id = 3L,
                    sessionId = 2L,
                    setType = FAILURE_SET_TYPE,
                    completedAt = latestSessionStart + 60_000L,
                    weightKg = 100.0,
                    reps = 1,
                ),
                set(
                    id = 4L,
                    sessionId = 2L,
                    setType = WARMUP_SET_TYPE,
                    completedAt = latestSessionStart + 120_000L,
                    weightKg = 200.0,
                    reps = 10,
                ),
            ) + (1..51).map { index ->
                set(
                    id = 100L + index,
                    sessionId = 1L,
                    setType = NORMAL_SET_TYPE,
                    completedAt = firstSessionStart + index * 60_000L,
                    weightKg = 1.0,
                    reps = 1,
                )
            },
        )

        val stats = SharedTrainingAnalytics.summarize(
            payload = payload,
            nowEpochMillis = epoch("2026-09-09T12:00:00Z"),
            timeZoneId = "UTC",
        ).exerciseStatistics.single()

        assertEquals(93.3333, stats.firstE1rmKg, 0.0001)
        assertEquals(100.0, stats.latestE1rmKg, 0.0001)
        assertEquals(100.0, stats.bestE1rmKg, 0.0001)
        assertEquals(6.6667, stats.e1rmDeltaKg, 0.0001)
        assertEquals(7.1429, stats.e1rmDeltaPercent, 0.0001)

        val comparison = stats.lastWorkoutComparison
        requireNotNull(comparison)
        assertEquals(20.0, comparison.weightDeltaKg, 0.0001)
        assertEquals(-2, comparison.repsDelta)
        assertEquals(6.6667, comparison.e1rmDeltaKg, 0.0001)
        assertEquals(7.1429, comparison.e1rmDeltaPercent, 0.0001)
        assertEquals(370.0 - 451.0, comparison.volumeDeltaKg, 0.0001)

        assertEquals(50, stats.recentSets.size)
        assertEquals(3L, stats.recentSets.first().id)
        assertEquals(100L + 4L, stats.recentSets.last().id)
        assertEquals(FAILURE_SET_TYPE, stats.recentSets.first().setType)
        assertTrue(stats.recentSets.all { it.setType != WARMUP_SET_TYPE })
        assertTrue(stats.recentSets.zipWithNext().all { (left, right) ->
            left.completedAtEpochMillis >= right.completedAtEpochMillis
        })
    }

    @Test
    fun `historical muscle volume follows local week boundary`() {
        val now = epoch("2026-09-07T00:30:00Z")
        val payload = payload(
            exercises = listOf(exercise()),
            sessions = listOf(session(id = 1L, start = epoch("2026-09-06T23:30:00Z"))),
            sets = listOf(
                set(
                    id = 1L,
                    sessionId = 1L,
                    setType = NORMAL_SET_TYPE,
                    completedAt = epoch("2026-09-06T23:31:00Z"),
                ),
            ),
        )

        val utc = SharedTrainingAnalytics.forWeek(
            payload = payload,
            weekStart = "2026-09-07",
            nowEpochMillis = now,
            timeZoneId = "UTC",
        )
        val losAngeles = SharedTrainingAnalytics.forWeek(
            payload = payload,
            weekStart = "2026-09-07",
            nowEpochMillis = now,
            timeZoneId = "America/Los_Angeles",
        )

        assertEquals("2026-09-07", utc.weekStart)
        assertEquals("2026-09-07", utc.currentWeekStart)
        assertEquals(0, utc.completedWorkoutCount)
        assertEquals(0.0, utc.volumes.first { it.muscleGroup == "BRUST" }.weeklySets)

        // The same instant is Sunday in Los Angeles. There, the current week
        // starts on August 31 and includes the completed work set.
        assertEquals("2026-08-31", losAngeles.weekStart)
        assertEquals("2026-08-31", losAngeles.currentWeekStart)
        assertEquals(1, losAngeles.completedWorkoutCount)
        assertEquals(1.0, losAngeles.volumes.first { it.muscleGroup == "BRUST" }.weeklySets)
    }

    @Test
    fun `historical muscle volume clamps future requests to current week`() {
        val now = epoch("2026-09-09T12:00:00Z")
        val payload = payload(
            exercises = listOf(exercise()),
            sessions = listOf(session(id = 1L, start = epoch("2026-09-08T10:00:00Z"))),
            sets = listOf(
                set(
                    id = 1L,
                    sessionId = 1L,
                    setType = NORMAL_SET_TYPE,
                    completedAt = epoch("2026-09-08T10:01:00Z"),
                ),
            ),
        )

        val result = SharedTrainingAnalytics.forWeek(
            payload = payload,
            weekStart = "2026-09-20",
            nowEpochMillis = now,
            timeZoneId = "UTC",
        )

        assertEquals("2026-09-07", result.weekStart)
        assertEquals("2026-09-07", result.currentWeekStart)
        assertEquals(1, result.completedWorkoutCount)
        assertEquals(10, result.volumes.size)
        assertEquals(1.0, result.volumes.first { it.muscleGroup == "BRUST" }.weeklySets)
    }

    @Test
    fun `weekly volume keeps eight chronological empty bins`() {
        val now = epoch("2026-09-09T12:00:00Z")
        val result = SharedTrainingAnalytics.summarize(
            payload(),
            now,
            "UTC",
            weekStartsSunday = true,
        )

        assertEquals(8, result.weeklyVolume.size)
        assertEquals("2026-07-19", result.weeklyVolume.first().weekStart)
        assertEquals("2026-09-06", result.weeklyVolume.last().weekStart)
        assertEquals(List(8) { 0.0 }, result.weeklyVolume.map { it.volumeKg })
        assertEquals(List(8) { 0 }, result.weeklyVolume.map { it.workoutCount })
    }

    @Test
    fun `readiness does not invent scores with insufficient data`() {
        val start = epoch("2026-09-08T10:00:00Z")
        val result = SharedTrainingAnalytics.summarize(
            payload(
                exercises = listOf(exercise()),
                sessions = listOf(session(id = 1L, start = start)),
                sets = listOf(set(1L, 1L, NORMAL_SET_TYPE, completedAt = start)),
            ),
            nowEpochMillis = epoch("2026-09-09T12:00:00Z"),
            timeZoneId = "UTC",
        )

        assertEquals(TrainingReadinessStatus.INSUFFICIENT_DATA, result.readiness.status)
        assertNull(result.readiness.fatigueScore)
        assertNull(result.readiness.readinessScore)
        assertEquals(false, result.readiness.deloadRecommended)
    }

    @Test
    fun `meta rotation chooses oldest event and exposes next plan`() {
        val p1 = BackupTrainingPlan(10L, "Push", 0L)
        val p2 = BackupTrainingPlan(20L, "Pull", 0L)
        val sessionStart = epoch("2026-09-01T10:00:00Z")
        val result = SharedTrainingAnalytics.summarize(
            payload(
                sessions = listOf(
                    session(id = 1L, start = sessionStart, planId = 10L, metaPlanId = 50L),
                ),
                trainingPlans = listOf(p1, p2),
                metaPlans = listOf(BackupMetaTrainingPlan(50L, "Upper", 0L)),
                metaItems = listOf(
                    BackupMetaPlanItem(1L, 50L, 10L, 0),
                    BackupMetaPlanItem(2L, 50L, 20L, 1),
                ),
                metaSkips = listOf(BackupMetaPlanSkip(1L, 50L, 20L, epoch("2026-09-08T10:00:00Z"))),
            ),
            nowEpochMillis = epoch("2026-09-09T12:00:00Z"),
            timeZoneId = "UTC",
        )

        val suggestion = result.metaRotationSuggestions.single()
        assertEquals(10L, suggestion.nextTrainingPlanId)
        assertEquals(listOf(10L, 20L), suggestion.orderedTrainingPlanIds)
        assertEquals(true, suggestion.canSkip)
    }

    private fun summarize(
        payload: BackupPayloadV1,
        timeZoneId: String,
        nowEpochMillis: Long = epoch("2026-09-09T12:00:00Z"),
    ): TrainingAnalytics =
        SharedTrainingAnalytics.summarize(
            payload = payload,
            nowEpochMillis = nowEpochMillis,
            timeZoneId = timeZoneId,
        )

    private fun payload(
        exercises: List<BackupExercise> = emptyList(),
        sessions: List<BackupWorkoutSession> = emptyList(),
        sets: List<BackupWorkoutSet> = emptyList(),
        trainingPlans: List<BackupTrainingPlan> = emptyList(),
        metaPlans: List<BackupMetaTrainingPlan> = emptyList(),
        metaItems: List<BackupMetaPlanItem> = emptyList(),
        metaSkips: List<BackupMetaPlanSkip> = emptyList(),
        records: List<BackupPersonalRecord> = emptyList(),
    ) = BackupPayloadV1(
        formatVersion = 1,
        schemaVersion = 12,
        appVersion = "test",
        exportedAtEpochMillis = 0L,
        exercises = exercises,
        workoutSessions = sessions,
        workoutSets = sets,
        trainingPlans = trainingPlans,
        planExercises = emptyList(),
        personalRecords = records,
        metaTrainingPlans = metaPlans,
        metaPlanItems = metaItems,
        metaPlanSkips = metaSkips,
    )

    private fun exercise(
        primary: String = "BRUST",
        secondary: String = "",
    ) = BackupExercise(
        id = 1L,
        name = "Bench",
        primaryMuscleGroup = primary,
        secondaryMuscleGroups = secondary,
        category = "LANGHANTEL",
        isCustom = false,
    )

    private fun session(
        id: Long,
        start: Long,
        end: Long? = start + 3_600_000L,
        planId: Long? = null,
        metaPlanId: Long? = null,
    ) = BackupWorkoutSession(
        id = id,
        startTime = start,
        endTime = end,
        durationSeconds = 3_600L,
        name = "Workout",
        notes = "",
        planId = planId,
        metaPlanId = metaPlanId,
    )

    private fun set(
        id: Long,
        sessionId: Long,
        setType: String,
        completedAt: Long,
        weightKg: Double = 100.0,
        reps: Int = 8,
    ) = BackupWorkoutSet(
        id = id,
        sessionId = sessionId,
        exerciseId = 1L,
        setNumber = id.toInt(),
        reps = reps,
        weightKg = weightKg,
        setType = setType,
        completedAt = completedAt,
    )

    private fun epoch(value: String): Long = Instant.parse(value).toEpochMilliseconds()
}
