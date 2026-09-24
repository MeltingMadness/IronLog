package com.ironlog.shared.analytics

import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupPersonalRecord
import com.ironlog.shared.backup.BackupWorkoutSet
import com.ironlog.shared.backup.DROP_SET_TYPE
import com.ironlog.shared.backup.FAILURE_SET_TYPE
import com.ironlog.shared.backup.NORMAL_SET_TYPE
import com.ironlog.shared.backup.WARMUP_SET_TYPE
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.round

/**
 * Computes the bounded, read-only training projection consumed by iOS.
 *
 * No persistence or Android model dependency is involved. A completed
 * session is a backup session with a non-null end time. Set-based metrics only
 * use sets belonging to those sessions and ignore timestamps after `now`.
 */
object SharedTrainingAnalytics {

    private const val RECENT_RECORD_LIMIT = 5
    private const val DELOAD_WINDOW_WEEKS = 4
    private const val DELOAD_MIN_SESSIONS = 3
    private const val MIN_WEEKLY_E1RM_POINTS = 2
    private const val STAGNATION_MAX_GROWTH_PERCENT = 1.0
    private const val DROP_THRESHOLD_PERCENT = -2.5
    private const val RPE_CREEP_THRESHOLD = 0.5
    private const val FAILURE_RATE_THRESHOLD = 0.15
    private const val RECOMMEND_THRESHOLD = 60

    private const val SCORE_E1RM_DROP = 60
    private const val SCORE_E1RM_STAGNATION = 25
    private const val SCORE_RPE_CREEP = 25
    private const val SCORE_FAILURE_RATE = 25

    private data class MuscleThresholds(val mev: Double, val mav: Double, val mrv: Double)

    private data class LocalSession(
        val id: Long,
        val startDate: LocalDate,
        val startEpochMillis: Long,
        val metaPlanId: Long?,
        val planId: Long?,
    )

    private data class CompoundTrend(val exerciseId: Long, val changePercent: Double)

    private data class RpeStats(
        val creep: Double,
        val averageRpe: Double = 0.0,
        val sessionCount: Int = 0,
    )

    private val muscleOrder = listOf(
        "BRUST",
        "RUECKEN",
        "BEINE",
        "SCHULTERN",
        "BIZEPS",
        "TRIZEPS",
        "GESAESS",
        "CORE",
        "UNTERARME",
        "WADEN",
    )

    // Matches core/common MuscleVolumeCalculator.DEFAULT_THRESHOLDS exactly.
    private val muscleThresholds = mapOf(
        "BRUST" to MuscleThresholds(10.0, 16.0, 22.0),
        "RUECKEN" to MuscleThresholds(10.0, 16.0, 22.0),
        "BEINE" to MuscleThresholds(10.0, 16.0, 22.0),
        "SCHULTERN" to MuscleThresholds(8.0, 12.0, 18.0),
        "GESAESS" to MuscleThresholds(8.0, 12.0, 18.0),
        "BIZEPS" to MuscleThresholds(6.0, 10.0, 16.0),
        "TRIZEPS" to MuscleThresholds(6.0, 10.0, 16.0),
        "WADEN" to MuscleThresholds(6.0, 10.0, 16.0),
        "UNTERARME" to MuscleThresholds(6.0, 10.0, 16.0),
        "CORE" to MuscleThresholds(6.0, 10.0, 16.0),
    )

    /**
     * Summarize one immutable backup snapshot.
     *
     * [timeZoneId] is resolved by kotlinx.datetime on each platform, so DST
     * and local calendar boundaries follow the user's actual zone. Invalid zone
     * IDs fail explicitly rather than silently changing the meaning of a week.
     */
    fun summarize(
        payload: BackupPayloadV1,
        nowEpochMillis: Long,
        timeZoneId: String,
        weekStartsSunday: Boolean = false,
    ): TrainingAnalytics {
        val timeZone = TimeZone.of(timeZoneId)
        val nowDate = localDate(nowEpochMillis, timeZone)
        val completedSessions = payload.workoutSessions
            .asSequence()
            .filter { it.endTime != null && it.startTime <= nowEpochMillis }
            .map { session ->
                LocalSession(
                    id = session.id,
                    startDate = localDate(session.startTime, timeZone),
                    startEpochMillis = session.startTime,
                    metaPlanId = session.metaPlanId,
                    planId = session.planId,
                )
            }
            .sortedWith(compareBy<LocalSession> { it.startEpochMillis }.thenBy { it.id })
            .toList()

        val currentWeekStart = weekStartFor(nowDate, weekStartsSunday)
        val nextWeekStart = shiftWeeks(currentWeekStart, 1)
        val monthStart = LocalDate(nowDate.year, nowDate.month, 1)

        val sessionsById = completedSessions.associateBy { it.id }
        val completedSessionIds = sessionsById.keys
        val eligibleSets = payload.workoutSets.asSequence()
            .filter { it.sessionId in completedSessionIds }
            .filter { it.completedAt <= nowEpochMillis }
            .toList()

        val exerciseById = payload.exercises.associateBy { it.id }

        val muscleVolumes = weightedMuscleVolumes(
            sets = eligibleSets,
            exercises = exerciseById,
            timeZone = timeZone,
            weekStart = currentWeekStart,
            weekEndExclusive = nextWeekStart,
        )

        val weeklyVolume = volumeBins(
            sessions = completedSessions,
            sets = eligibleSets,
            timeZone = timeZone,
            currentWeekStart = currentWeekStart,
        )

        val exerciseStatistics = exerciseStatistics(
            sets = eligibleSets,
            sessionsById = sessionsById,
            exercises = exerciseById,
            timeZone = timeZone,
        )

        val readiness = assessReadiness(
            sessions = completedSessions,
            sets = eligibleSets,
            exercises = exerciseById,
            today = nowDate,
            timeZone = timeZone,
        )

        return TrainingAnalytics(
            generatedAtEpochMillis = nowEpochMillis,
            timeZoneId = timeZoneId,
            weekStartsSunday = weekStartsSunday,
            currentWeekStart = currentWeekStart.toString(),
            workoutsThisWeek = completedSessions.count { it.startDate >= currentWeekStart && it.startDate < nextWeekStart },
            workoutsThisMonth = completedSessions.count { it.startDate >= monthStart && it.startDate <= nowDate },
            lastSessionDate = completedSessions.maxByOrNull { it.startEpochMillis }?.startDate?.toString(),
            recentRecords = payload.personalRecords
                .asSequence()
                .sortedWith(compareByDescending<BackupPersonalRecord> { it.achievedAt }.thenByDescending { it.id })
                .take(RECENT_RECORD_LIMIT)
                .map { record ->
                    TrainingAnalyticsRecord(
                        id = record.id,
                        exerciseId = record.exerciseId,
                        exerciseName = exerciseById[record.exerciseId]?.name,
                        type = record.type,
                        value = record.value,
                        achievedAtEpochMillis = record.achievedAt,
                        achievedAt = localDate(record.achievedAt, timeZone).toString(),
                    )
                }
                .toList(),
            muscleVolumes = muscleVolumes,
            weeklyVolume = weeklyVolume,
            exerciseStatistics = exerciseStatistics,
            readiness = readiness,
            metaRotationSuggestions = metaRotationSuggestions(payload, completedSessions, nowEpochMillis),
        )
    }

    /**
     * Computes the muscle-volume card for one navigable calendar week.
     *
     * [weekStart] is an ISO local date. It is aligned to the requested week
     * anchor and clamped to [currentWeekStart], matching Android's previous /
     * next week navigation. Only completed sessions and work sets completed by
     * [nowEpochMillis] participate; warmups remain excluded and every muscle
     * group remains visible with a zero row when the week is empty.
     */
    fun forWeek(
        payload: BackupPayloadV1,
        weekStart: String,
        nowEpochMillis: Long,
        timeZoneId: String,
        weekStartsSunday: Boolean = false,
    ): TrainingWeeklyMuscleVolume {
        val timeZone = TimeZone.of(timeZoneId)
        val nowDate = localDate(nowEpochMillis, timeZone)
        val currentWeekStart = weekStartFor(nowDate, weekStartsSunday)
        val requestedWeekStart = weekStartFor(LocalDate.parse(weekStart), weekStartsSunday)
        val selectedWeekStart = if (requestedWeekStart > currentWeekStart) {
            currentWeekStart
        } else {
            requestedWeekStart
        }
        val selectedWeekEnd = shiftWeeks(selectedWeekStart, 1)

        val completedSessionIds = payload.workoutSessions.asSequence()
            .filter { it.endTime != null && it.startTime <= nowEpochMillis }
            .map { it.id }
            .toSet()
        val eligibleSets = payload.workoutSets.asSequence()
            .filter { it.sessionId in completedSessionIds }
            .filter { it.completedAt <= nowEpochMillis }
            .filter(::isCountedSet)
            .filter {
                val date = localDate(it.completedAt, timeZone)
                date >= selectedWeekStart && date < selectedWeekEnd
            }
            .toList()

        return TrainingWeeklyMuscleVolume(
            weekStart = selectedWeekStart.toString(),
            currentWeekStart = currentWeekStart.toString(),
            weekStartsSunday = weekStartsSunday,
            timeZoneId = timeZoneId,
            completedWorkoutCount = eligibleSets.asSequence()
                .map { it.sessionId }
                .distinct()
                .count(),
            volumes = weightedMuscleVolumes(
                sets = eligibleSets,
                exercises = payload.exercises.associateBy { it.id },
                timeZone = timeZone,
                weekStart = selectedWeekStart,
                weekEndExclusive = selectedWeekEnd,
            ),
        )
    }

    private fun weightedMuscleVolumes(
        sets: List<BackupWorkoutSet>,
        exercises: Map<Long, BackupExercise>,
        timeZone: TimeZone,
        weekStart: LocalDate,
        weekEndExclusive: LocalDate,
    ): List<TrainingMuscleVolume> {
        val volume = mutableMapOf<String, Double>()
        for (set in sets) {
            if (!isCountedSet(set)) continue
            val date = localDate(set.completedAt, timeZone)
            if (date < weekStart || date >= weekEndExclusive) continue
            val exercise = exercises[set.exerciseId] ?: continue
            val primary = canonicalMuscle(exercise.primaryMuscleGroup)
            if (primary != null) volume[primary] = (volume[primary] ?: 0.0) + 1.0
            parseMuscles(exercise.secondaryMuscleGroups)
                .forEach { secondary -> volume[secondary] = (volume[secondary] ?: 0.0) + 0.5 }
        }

        return muscleOrder.map { muscle ->
            val thresholds = muscleThresholds.getValue(muscle)
            val weeklySets = volume[muscle] ?: 0.0
            TrainingMuscleVolume(
                muscleGroup = muscle,
                weeklySets = weeklySets,
                mev = thresholds.mev,
                mav = thresholds.mav,
                mrv = thresholds.mrv,
                status = when {
                    weeklySets < thresholds.mev -> TrainingVolumeStatus.LOW
                    weeklySets > thresholds.mrv -> TrainingVolumeStatus.HIGH
                    else -> TrainingVolumeStatus.OPTIMAL
                },
            )
        }
    }

    private fun volumeBins(
        sessions: List<LocalSession>,
        sets: List<BackupWorkoutSet>,
        timeZone: TimeZone,
        currentWeekStart: LocalDate,
    ): List<TrainingVolumeBin> {
        val sessionDates = sessions.associate { it.id to it.startDate }
        val windowStart = shiftWeeks(currentWeekStart, -7)
        return (0 until 8).map { index ->
            val start = shiftWeeks(windowStart, index)
            val end = shiftWeeks(start, 1)
            val sessionIds = sessionDates
                .filterValues { date -> date >= start && date < end }
                .keys
            val volume = sets.asSequence()
                .filter { isCountedSet(it) }
                .filter {
                    val date = localDate(it.completedAt, timeZone)
                    date >= start && date < end
                }
                .fold(0.0) { total, set ->
                    val setVolume = set.weightKg * set.reps.toDouble()
                    val next = total + setVolume
                    if (set.reps > 0 && set.weightKg.isFinite() && set.weightKg >= 0.0 &&
                        setVolume.isFinite() && next.isFinite()
                    ) next else total
                }
            TrainingVolumeBin(
                weekStart = start.toString(),
                volumeKg = volume,
                workoutCount = sessionIds.size,
            )
        }
    }

    private fun exerciseStatistics(
        sets: List<BackupWorkoutSet>,
        sessionsById: Map<Long, LocalSession>,
        exercises: Map<Long, BackupExercise>,
        timeZone: TimeZone,
    ): List<TrainingExerciseStatistics> {
        return sets
            .asSequence()
            .filter(::isCountedSet)
            .filter { it.exerciseId in exercises && it.sessionId in sessionsById }
            .groupBy { it.exerciseId }
            .map { (exerciseId, exerciseSets) ->
                val sessions = exerciseSets
                    .groupBy { it.sessionId }
                    .mapNotNull { (sessionId, sessionSets) ->
                        val validSets = sessionSets.filter {
                            it.reps > 0 && it.weightKg.isFinite() && it.weightKg >= 0.0
                        }
                        if (validSets.isEmpty()) return@mapNotNull null
                        val latest = validSets.maxWithOrNull(compareBy<BackupWorkoutSet> { it.completedAt }.thenBy { it.id })
                            ?: return@mapNotNull null
                        val volume = validSets.fold(0.0) { total, set ->
                            val setVolume = set.weightKg * set.reps.toDouble()
                            val next = total + setVolume
                            if (setVolume.isFinite() && next.isFinite()) next else total
                        }
                        TrainingExerciseSessionStatistics(
                            sessionId = sessionId,
                            date = localDate(latest.completedAt, timeZone).toString(),
                            completedAtEpochMillis = latest.completedAt,
                            maxWeightKg = validSets.maxOf { it.weightKg },
                            maxReps = validSets.maxOf { it.reps },
                            maxE1rmKg = validSets.maxOf { calculateE1rm(it.weightKg, it.reps) },
                            volumeKg = volume,
                        )
                    }
                    .sortedWith(compareBy<TrainingExerciseSessionStatistics> { it.completedAtEpochMillis }.thenBy { it.sessionId })
                val firstE1rm = sessions.firstOrNull()?.maxE1rmKg ?: 0.0
                val latestE1rm = sessions.lastOrNull()?.maxE1rmKg ?: 0.0
                val bestE1rm = sessions.maxOfOrNull { it.maxE1rmKg } ?: 0.0
                val lastWorkoutComparison = if (sessions.size >= 2) {
                    val previous = sessions[sessions.lastIndex - 1]
                    val latest = sessions.last()
                    TrainingExerciseWorkoutComparison(
                        previous = previous,
                        latest = latest,
                        weightDeltaKg = latest.maxWeightKg - previous.maxWeightKg,
                        repsDelta = latest.maxReps - previous.maxReps,
                        e1rmDeltaKg = latest.maxE1rmKg - previous.maxE1rmKg,
                        e1rmDeltaPercent = relativeChangePercent(
                            first = previous.maxE1rmKg,
                            latest = latest.maxE1rmKg,
                        ),
                        volumeDeltaKg = latest.volumeKg - previous.volumeKg,
                    )
                } else {
                    null
                }
                val recentSets = exerciseSets
                    .asSequence()
                    .filter { it.reps > 0 && it.weightKg.isFinite() && it.weightKg >= 0.0 }
                    .sortedWith(compareByDescending<BackupWorkoutSet> { it.completedAt }.thenByDescending { it.id })
                    .take(50)
                    .map { set ->
                        TrainingExerciseSetStatistics(
                            id = set.id,
                            sessionId = set.sessionId,
                            date = localDate(set.completedAt, timeZone).toString(),
                            completedAtEpochMillis = set.completedAt,
                            setType = resolvedSetType(set),
                            weightKg = set.weightKg,
                            reps = set.reps,
                            e1rmKg = calculateE1rm(set.weightKg, set.reps),
                            volumeKg = set.weightKg * set.reps.toDouble(),
                        )
                    }
                    .toList()
                TrainingExerciseStatistics(
                    exerciseId = exerciseId,
                    exerciseName = exercises[exerciseId]?.name,
                    sessions = sessions,
                    firstE1rmKg = firstE1rm,
                    latestE1rmKg = latestE1rm,
                    bestE1rmKg = bestE1rm,
                    e1rmDeltaKg = latestE1rm - firstE1rm,
                    e1rmDeltaPercent = relativeChangePercent(firstE1rm, latestE1rm),
                    lastWorkoutComparison = lastWorkoutComparison,
                    recentSets = recentSets,
                )
            }
            .filter { it.sessions.isNotEmpty() }
            .sortedBy { it.exerciseId }
            .toList()
    }

    private fun assessReadiness(
        sessions: List<LocalSession>,
        sets: List<BackupWorkoutSet>,
        exercises: Map<Long, BackupExercise>,
        today: LocalDate,
        timeZone: TimeZone,
    ): TrainingReadiness {
        val windowStart = shiftWeeks(today, -DELOAD_WINDOW_WEEKS)
        val sessionSets = sets.groupBy { it.sessionId }
            .mapValues { (_, grouped) -> grouped.filter { localDate(it.completedAt, timeZone) >= windowStart } }
        val trainingSessions = sessions
            .filter { it.startDate >= windowStart && sessionSets[it.id].orEmpty().isNotEmpty() }
            .sortedBy { it.startEpochMillis }
        if (trainingSessions.size < DELOAD_MIN_SESSIONS) {
            return insufficientReadiness(windowStart, today, trainingSessions.size)
        }

        val compoundExerciseIds = exercises.values
            .filter { canonicalCategory(it.category) == "LANGHANTEL" }
            .map { it.id }
            .toSet()
        val compoundTrends = compoundE1rmTrends(trainingSessions, sessionSets, compoundExerciseIds, timeZone)
        val signals = mutableListOf<TrainingReadinessSignal>()
        var score = 0
        val worst = compoundTrends.minByOrNull { it.changePercent }
        when {
            worst != null && worst.changePercent <= DROP_THRESHOLD_PERCENT -> {
                signals += TrainingReadinessSignal.E1RM_DROP
                score += SCORE_E1RM_DROP
            }
            worst != null && worst.changePercent <= STAGNATION_MAX_GROWTH_PERCENT -> {
                signals += TrainingReadinessSignal.E1RM_STAGNATION
                score += SCORE_E1RM_STAGNATION
            }
        }

        val rpeStats = rpeCreep(trainingSessions, sessionSets)
        if (rpeStats.creep >= RPE_CREEP_THRESHOLD) {
            signals += TrainingReadinessSignal.RPE_CREEP
            score += SCORE_RPE_CREEP
        }
        val failureRate = failureRate(trainingSessions, sessionSets)
        if (failureRate >= FAILURE_RATE_THRESHOLD) {
            signals += TrainingReadinessSignal.FAILURE_FREQUENCY
            score += (SCORE_FAILURE_RATE * (failureRate / FAILURE_RATE_THRESHOLD)
                .coerceAtMost(1.0)).toInt()
        }

        val fatigueScore = score.coerceIn(0, 100)
        val recommended = signals.isNotEmpty() && fatigueScore >= RECOMMEND_THRESHOLD
        return TrainingReadiness(
            status = if (signals.isEmpty()) {
                TrainingReadinessStatus.NO_NOTABLE_STRAIN
            } else {
                TrainingReadinessStatus.SIGNALS_PRESENT
            },
            fatigueScore = fatigueScore,
            readinessScore = 100 - fatigueScore,
            deloadRecommended = recommended,
            signals = signals,
            windowStart = windowStart.toString(),
            windowEnd = today.toString(),
            sessionCount = trainingSessions.size,
            minimumSessionCount = DELOAD_MIN_SESSIONS,
            analysisWindowWeeks = DELOAD_WINDOW_WEEKS,
            strongestExerciseId = worst?.exerciseId,
            strongestExerciseChangePercent = worst?.changePercent,
            averageRpe = if (rpeStats.sessionCount > 0) round1(rpeStats.averageRpe / rpeStats.sessionCount) else null,
            failureRate = failureRate,
            analyzedCompoundCount = compoundTrends.size,
        )
    }

    private fun insufficientReadiness(
        windowStart: LocalDate,
        today: LocalDate,
        sessionCount: Int,
    ): TrainingReadiness = TrainingReadiness(
        status = TrainingReadinessStatus.INSUFFICIENT_DATA,
        fatigueScore = null,
        readinessScore = null,
        deloadRecommended = false,
        signals = emptyList(),
        windowStart = windowStart.toString(),
        windowEnd = today.toString(),
        sessionCount = sessionCount,
        minimumSessionCount = DELOAD_MIN_SESSIONS,
        analysisWindowWeeks = DELOAD_WINDOW_WEEKS,
        strongestExerciseId = null,
        strongestExerciseChangePercent = null,
        averageRpe = null,
        failureRate = 0.0,
        analyzedCompoundCount = 0,
    )

    private fun compoundE1rmTrends(
        sessions: List<LocalSession>,
        sessionSets: Map<Long, List<BackupWorkoutSet>>,
        compoundExerciseIds: Set<Long>,
        timeZone: TimeZone,
    ): List<CompoundTrend> {
        val weeklyBest = mutableMapOf<Pair<Long, LocalDate>, Double>()
        sessions.forEach { session ->
            sessionSets[session.id].orEmpty().forEach { set ->
                if (set.exerciseId !in compoundExerciseIds || resolvedSetType(set) != NORMAL_SET_TYPE) return@forEach
                if (set.reps < 1 || !set.weightKg.isFinite() || set.weightKg <= 0.0) return@forEach
                val weekAnchor = mondayWeekStart(localDate(set.completedAt, timeZone))
                val key = set.exerciseId to weekAnchor
                val e1rm = calculateE1rm(set.weightKg, set.reps)
                weeklyBest[key] = maxOf(weeklyBest[key] ?: 0.0, e1rm)
            }
        }
        return compoundExerciseIds.mapNotNull { exerciseId ->
            val weeks = weeklyBest
                .filterKeys { it.first == exerciseId }
                .entries
                .sortedBy { it.key.second }
                .map { it.value }
            if (weeks.size < MIN_WEEKLY_E1RM_POINTS) return@mapNotNull null
            val half = weeks.size / 2
            val earlier = weeks.subList(0, half)
            val later = weeks.subList(half, weeks.size)
            val earlierAverage = average(earlier)
            val laterAverage = average(later)
            if (earlierAverage <= 0.0) null
            else CompoundTrend(exerciseId, (laterAverage - earlierAverage) / earlierAverage * 100.0)
        }
    }

    private fun rpeCreep(
        sessions: List<LocalSession>,
        sessionSets: Map<Long, List<BackupWorkoutSet>>,
    ): RpeStats {
        val sessionAverages = sessions.mapNotNull { session ->
            val rpes = sessionSets[session.id].orEmpty()
                .filter { resolvedSetType(it) == NORMAL_SET_TYPE }
                .mapNotNull { it.rpe?.takeIf { value -> value.isFinite() } }
            if (rpes.isEmpty()) null else average(rpes)
        }
        if (sessionAverages.size < 2) return RpeStats(creep = 0.0)
        val half = sessionAverages.size / 2
        return RpeStats(
            creep = average(sessionAverages.subList(half, sessionAverages.size)) - average(sessionAverages.subList(0, half)),
            averageRpe = sessionAverages.sum(),
            sessionCount = sessionAverages.size,
        )
    }

    private fun failureRate(
        sessions: List<LocalSession>,
        sessionSets: Map<Long, List<BackupWorkoutSet>>,
    ): Double {
        var workSets = 0
        var failures = 0
        sessions.forEach { session ->
            sessionSets[session.id].orEmpty().forEach { set ->
                when (resolvedSetType(set)) {
                    NORMAL_SET_TYPE -> workSets += 1
                    FAILURE_SET_TYPE -> {
                        workSets += 1
                        failures += 1
                    }
                    WARMUP_SET_TYPE, DROP_SET_TYPE -> Unit
                }
            }
        }
        return if (workSets == 0) 0.0 else failures.toDouble() / workSets
    }

    private fun metaRotationSuggestions(
        payload: BackupPayloadV1,
        sessions: List<LocalSession>,
        nowEpochMillis: Long,
    ): List<MetaRotationSuggestion> {
        val plansById = payload.trainingPlans.associateBy { it.id }
        val lastEventByPair = mutableMapOf<Pair<Long, Long>, Long>()
        sessions.forEach { session ->
            val metaPlanId = session.metaPlanId ?: return@forEach
            val planId = session.planId ?: return@forEach
            lastEventByPair[metaPlanId to planId] = maxOf(
                lastEventByPair[metaPlanId to planId] ?: Long.MIN_VALUE,
                session.startEpochMillis,
            )
        }
        payload.metaPlanSkips
            .filter { it.skippedAt <= nowEpochMillis }
            .forEach { skip ->
                val key = skip.metaPlanId to skip.trainingPlanId
                lastEventByPair[key] = maxOf(lastEventByPair[key] ?: Long.MIN_VALUE, skip.skippedAt)
            }

        return payload.metaTrainingPlans.mapNotNull { metaPlan ->
            val orderedPlans = payload.metaPlanItems
                .asSequence()
                .filter { it.metaPlanId == metaPlan.id }
                .sortedBy { it.orderIndex }
                .mapNotNull { plansById[it.trainingPlanId] }
                .distinctBy { it.id }
                .toList()
            if (orderedPlans.isEmpty()) return@mapNotNull null
            val rotated = orderedPlans
                .withIndex()
                .sortedWith(
                    compareBy<IndexedValue<com.ironlog.shared.backup.BackupTrainingPlan>> {
                        lastEventByPair[metaPlan.id to it.value.id] != null
                    }
                        .thenBy { lastEventByPair[metaPlan.id to it.value.id] ?: Long.MIN_VALUE }
                        .thenBy { it.index },
                )
                .map { it.value }
            val next = rotated.firstOrNull() ?: return@mapNotNull null
            MetaRotationSuggestion(
                metaPlanId = metaPlan.id,
                metaPlanName = metaPlan.name,
                nextTrainingPlanId = next.id,
                nextTrainingPlanName = next.name,
                orderedTrainingPlanIds = rotated.map { it.id },
                canSkip = rotated.size > 1,
            )
        }
    }

    private fun isCountedSet(set: BackupWorkoutSet): Boolean = when (resolvedSetType(set)) {
        NORMAL_SET_TYPE, DROP_SET_TYPE, FAILURE_SET_TYPE -> true
        WARMUP_SET_TYPE -> false
        else -> false
    }

    private fun resolvedSetType(set: BackupWorkoutSet): String = set.resolvedSetType()

    private fun canonicalMuscle(raw: String): String? = raw.trim().uppercase().takeIf { it in muscleThresholds }

    private fun parseMuscles(raw: String): List<String> = raw
        .split(',')
        .mapNotNull(::canonicalMuscle)
        .distinct()

    private fun canonicalCategory(raw: String): String = raw.trim().uppercase()

    private fun localDate(epochMillis: Long, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date

    private fun weekStartFor(date: LocalDate, sunday: Boolean): LocalDate {
        val anchor = if (sunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY
        val dayOffset = (date.dayOfWeek.ordinal - anchor.ordinal + 7) % 7
        return date.minus(dayOffset, DateTimeUnit.DAY)
    }

    private fun mondayWeekStart(date: LocalDate): LocalDate = weekStartFor(date, sunday = false)

    private fun shiftWeeks(date: LocalDate, weeks: Int): LocalDate =
        date.plus(weeks * 7, DateTimeUnit.DAY)

    private fun calculateE1rm(weightKg: Double, reps: Int): Double =
        if (reps <= 1) weightKg else weightKg * (1.0 + reps / 30.0)

    private fun relativeChangePercent(first: Double, latest: Double): Double =
        if (first > 0.0) (latest - first) / first * 100.0 else 0.0

    private fun average(values: List<Double>): Double = if (values.isEmpty()) 0.0 else values.sum() / values.size

    private fun round1(value: Double): Double = round(value * 10.0) / 10.0
}
