package com.ironlog.shared.analytics

import com.ironlog.shared.backup.BackupExercise
import com.ironlog.shared.backup.BackupPlanExercise
import com.ironlog.shared.backup.BackupPayloadV1
import com.ironlog.shared.backup.BackupProgressionConfig
import com.ironlog.shared.backup.BackupWorkoutPlanTarget
import com.ironlog.shared.backup.BackupWorkoutSet
import com.ironlog.shared.model.MuscleGroup
import com.ironlog.shared.readiness.DailyCheckInInput
import com.ironlog.shared.readiness.DeloadState
import com.ironlog.shared.readiness.EquipmentType
import com.ironlog.shared.readiness.ExerciseSessionInput
import com.ironlog.shared.readiness.MuscleLoadEntry
import com.ironlog.shared.readiness.ProgressionGoal
import com.ironlog.shared.readiness.ProgressionScheme
import com.ironlog.shared.readiness.ReadinessAssessment
import com.ironlog.shared.readiness.ReadinessEngine
import com.ironlog.shared.readiness.ReadinessInput
import com.ironlog.shared.readiness.SetIntentionCodes
import com.ironlog.shared.readiness.SetType
import com.ironlog.shared.readiness.TodaySessionInput
import com.ironlog.shared.readiness.TrendSetInput
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Binds the neutral readiness core to the durable training graph.
 *
 * The adapter is the only place that translates persisted records into
 * [ReadinessInput]: calendar semantics ("today", the trailing volume window),
 * the device class of an exercise and the configured weight step all live here
 * and are handed to the core explicitly. Nothing is derived that the store does
 * not contain.
 *
 * Mapping rules that a reader should be able to audit:
 * * only sessions that ended at or before `now` and sets completed at or before
 *   `now` are used; a running session never feeds the comparison series;
 * * warmup sets are passed through and excluded by the core, never pre-filtered
 *   differently than the existing weekly-volume projection;
 * * set intention comes exclusively from `readinessData.setIntentions`; a set
 *   without a record stays [SetIntentionCodes.UNKNOWN];
 * * a session's progression goal comes exclusively from its immutable
 *   `workoutPlanTargets` snapshot; without one the goal stays unknown instead of
 *   applying today's plan row to history;
 * * a row is one trained slot, not one exercise: the same exercise may sit in
 *   several slots of one session (`orderIndex`), and each set names its slot through
 *   `planTargetSnapshotId`. The core merges the slots of one session into a single
 *   unit and flags that merge, instead of silently keeping one arbitrary slot;
 * * muscle load weights the primary group with 1.0 and every secondary group
 *   with 0.5, exactly like [SharedTrainingAnalytics]'s weekly volume;
 * * today's muscle context covers a running workout (planned exercises plus what
 *   was already logged) and, before a workout starts, the caller-selected plan;
 * * per-muscle soreness is mapped into the muscle context; the global
 *   `soreness` dimension of `DailyCheckInInput` deliberately stays `null`,
 *   because the stored check-in has no muscle-independent value. Because soreness
 *   belongs to the calendar day, it stays visible on a rest day.
 */
object SharedReadinessProjection {

    /**
     * Default trailing window for `setsInWindow` in days.
     *
     * This is a **rolling** window ending at `now` (`now - N days .. now`), deliberately
     * *not* the calendar week of the weekly volume card: that card bins by calendar week
     * aligned to the configured week start, while a readiness signal must not shrink to a
     * partial week every Monday. A caller that wants the calendar-week boundary passes it
     * explicitly and the UI labels the value accordingly ("letzte N Tage"), never "diese
     * Woche".
     */
    const val MUSCLE_WINDOW_DAYS = 7

    /**
     * Identifier used for a synthetic "today" context that has no persisted session yet
     * (a rest day with a check-in, or a caller-selected plan before the workout starts).
     * It can never collide with a real session id and never matches a history entry, so
     * `setsToday` stays 0.
     */
    private const val SYNTHETIC_TODAY_SESSION_ID = -1L

    /**
     * Slot key for a set that names no plan-target snapshot. Real target ids are positive,
     * so this sentinel can never collide with a persisted snapshot.
     */
    private const val NO_TARGET_SLOT = -1L

    private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

    private val muscleVocabulary: Set<String> = MuscleGroup.entries.map { it.name }.toSet()

    fun assess(
        payload: BackupPayloadV1,
        nowEpochMillis: Long,
        timeZoneId: String,
        muscleWindowDays: Int = MUSCLE_WINDOW_DAYS,
        selectedPlanId: Long? = null,
    ): ReadinessAssessment {
        val timeZone = TimeZone.of(timeZoneId)
        val nowDate = localDate(nowEpochMillis, timeZone)

        val exerciseById = payload.exercises.associateBy { it.id }
        val intentionsBySetId = payload.readinessData.setIntentions.associate { it.setId to it.intention.name }
        val checkInsByDate = payload.readinessData.checkIns.associateBy { it.localDate.toString() }

        // Only a session that ended before "now" is history. A still-running session
        // (or one whose end lies in the future) must never enter the series.
        val completedSessions = payload.workoutSessions
            .filter {
                it.endTime != null &&
                    it.endTime <= nowEpochMillis &&
                    it.startTime <= nowEpochMillis
            }
            .sortedWith(compareBy({ it.startTime }, { it.id }))
        val completedSessionIds = completedSessions.map { it.id }.toSet()
        val eligibleSets = payload.workoutSets
            .filter { it.sessionId in completedSessionIds && it.completedAt <= nowEpochMillis }
        val setsBySession = eligibleSets
            .groupBy { it.sessionId }
            .mapValues { (_, sets) -> sets.groupBy { it.exerciseId } }

        val planExerciseByPlanAndExercise = payload.planExercises.associateBy { it.planId to it.exerciseId }
        val targetsBySessionAndExercise = payload.workoutPlanTargets.groupBy { it.sessionId to it.exerciseId }

        val sessions = buildSessionRows(
            completedSessions = completedSessions,
            setsBySession = setsBySession,
            exerciseById = exerciseById,
            planExerciseByPlanAndExercise = planExerciseByPlanAndExercise,
            targetsBySessionAndExercise = targetsBySessionAndExercise,
            intentionsBySetId = intentionsBySetId,
        )

        val muscleLoadHistory = muscleLoadHistory(
            completedSessions = completedSessions,
            setsBySession = setsBySession,
            exerciseById = exerciseById,
            checkInsByDate = checkInsByDate,
            timeZone = timeZone,
        )

        // "Today" is the running workout if one exists, otherwise the workout that
        // already ended today. Before any workout starts the caller may name the plan
        // the athlete is about to train; without either signal the day has no planned
        // load and only a check-in (if any) applies.
        val activeToday = payload.workoutSessions
            .filter {
                it.endTime == null &&
                    it.startTime <= nowEpochMillis &&
                    localDate(it.startTime, timeZone) == nowDate
            }
            .maxByOrNull { it.startTime }
        val completedToday = completedSessions.lastOrNull { localDate(it.startTime, timeZone) == nowDate }
        val todaySource = activeToday ?: completedToday

        val plannedMuscleGroups = when {
            activeToday != null ->
                planMuscleGroups(activeToday.planId ?: selectedPlanId, payload, exerciseById) +
                    sessionMuscleGroups(activeToday.id, setsBySession, exerciseById)

            completedToday != null -> sessionMuscleGroups(completedToday.id, setsBySession, exerciseById)
            else -> planMuscleGroups(selectedPlanId, payload, exerciseById)
        }.distinct()

        // Self-reported soreness belongs to the calendar day, not to a workout: it stays
        // visible on a rest day, where there is no session to attach it to.
        val todaySoreness = checkInsByDate[nowDate.toString()]
            ?.muscleSoreness
            ?.mapKeys { it.key.name }
            ?.filterKeys { it in muscleVocabulary }
            .orEmpty()

        val todayMuscleGroups = (plannedMuscleGroups + todaySoreness.keys).distinct()
        val hasTodayContext = todaySource != null ||
            plannedMuscleGroups.isNotEmpty() ||
            todaySoreness.isNotEmpty()

        val todaySession = if (hasTodayContext) {
            TodaySessionInput(
                // A planned-only day has no durable session yet, so it gets a synthetic
                // id that cannot match history and keeps `setsToday` at 0.
                sessionId = todaySource?.id ?: SYNTHETIC_TODAY_SESSION_ID,
                startedAtEpochMillis = todaySource?.startTime ?: nowEpochMillis,
                completedAtEpochMillis = todaySource?.endTime,
                muscleGroups = todayMuscleGroups,
                sorenessByMuscle = todaySoreness,
            )
        } else {
            null
        }

        val todayCheckIn = checkInsByDate[nowDate.toString()]?.let { checkIn ->
            DailyCheckInInput(
                reportedAtEpochMillis = checkIn.recordedAtEpochMillis
                    ?: checkIn.updatedAtEpochMillis
                    ?: nowEpochMillis,
                sleepQuality = checkIn.sleepQuality,
                energy = checkIn.energy,
                stress = checkIn.stress,
                // Soreness is stored per muscle group and is mapped into the muscle
                // context; flattening it here would invent an unrecorded value.
                soreness = null,
            )
        }

        return ReadinessEngine.assess(
            ReadinessInput(
                nowEpochMillis = nowEpochMillis,
                sessions = sessions,
                todaySession = todaySession,
                muscleLoadHistory = muscleLoadHistory,
                muscleWindowStartEpochMillis = nowEpochMillis - muscleWindowDays.toLong() * DAY_MILLIS,
                checkIn = todayCheckIn,
            ),
        )
    }

    /**
     * One row per trained slot: (session, exercise, plan-target snapshot).
     *
     * The same exercise may legitimately sit in several slots of one session at
     * different `orderIndex` values. Every slot keeps its own goal, order index and set
     * list, so the core can merge the slots of one session into a single unit and flag
     * that merge (`MULTIPLE_SLOTS_IN_SESSION_MERGED`) instead of silently reading one
     * arbitrarily chosen slot.
     *
     * A set without a snapshot link is attributed to the exercise's target when that is
     * unambiguous (exactly one target, or no multi-slot session). If a session trains the
     * exercise in several slots and a set names none of them, the slot is not provable:
     * that row is handed over as an explicit neutral reset rather than compared against a
     * guessed snapshot.
     */
    private fun buildSessionRows(
        completedSessions: List<com.ironlog.shared.backup.BackupWorkoutSession>,
        setsBySession: Map<Long, Map<Long, List<BackupWorkoutSet>>>,
        exerciseById: Map<Long, BackupExercise>,
        planExerciseByPlanAndExercise: Map<Pair<Long, Long>, BackupPlanExercise>,
        targetsBySessionAndExercise: Map<Pair<Long, Long>, List<BackupWorkoutPlanTarget>>,
        intentionsBySetId: Map<Long, String>,
    ): List<ExerciseSessionInput> {
        val previousSlotsByExercise = mutableMapOf<Long, Set<Int>>()
        val rows = mutableListOf<ExerciseSessionInput>()

        for (session in completedSessions) {
            val byExercise = setsBySession[session.id].orEmpty()
            if (byExercise.isEmpty()) continue

            val slotsByExercise = mutableMapOf<Long, MutableSet<Int>>()
            val pending = mutableListOf<Pair<Long, ExerciseSessionInput>>()

            // `entries.sortedBy` instead of `toSortedMap()`: the latter is JVM-only and does not
            // exist in Kotlin/Native, while the ordering guarantee for the projection is the same.
            for ((exerciseId, sets) in byExercise.entries.sortedBy { it.key }) {
                val exercise = exerciseById[exerciseId]
                val targets = targetsBySessionAndExercise[session.id to exerciseId].orEmpty()
                val onlyTarget = targets.singleOrNull()
                val targetsById = targets.associateBy { it.id }
                val planExercise = session.planId
                    ?.let { planExerciseByPlanAndExercise[it to exerciseId] }
                val bySlot = sets.groupBy { set ->
                    set.planTargetSnapshotId?.takeIf { it in targetsById } ?: NO_TARGET_SLOT
                }
                // Several slots exist but some sets name none of them: the slot cannot be
                // proven, so that row must not be compared against a guessed snapshot.
                val ambiguousUnbound = bySlot.containsKey(NO_TARGET_SLOT) && targets.size > 1

                for ((slot, slotSets) in bySlot.entries.sortedBy { it.key }) {
                    val bound = slot != NO_TARGET_SLOT
                    val target = if (bound) targetsById[slot] else onlyTarget
                    val slotIndex = target?.orderIndex ?: planExercise?.orderIndex ?: 0
                    slotsByExercise.getOrPut(exerciseId) { mutableSetOf() }.add(slotIndex)
                    pending += exerciseId to ExerciseSessionInput(
                        sessionId = session.id,
                        exerciseId = exerciseId,
                        exerciseName = exercise?.name.orEmpty(),
                        equipmentType = equipmentType(exercise?.category),
                        completedAtEpochMillis = session.endTime ?: session.startTime,
                        orderingToken = session.id,
                        slotIndex = slotIndex,
                        slotChangedSincePrevious = !bound && ambiguousUnbound,
                        deloadState = deloadState(session.isDeload),
                        goal = goal(target),
                        sets = slotSets
                            .sortedWith(compareBy({ it.setNumber }, { it.id }))
                            .map { set -> trendSet(set, intentionsBySetId) },
                    )
                }
            }

            // A slot change is a change between sessions, not between the several slots of
            // one session: the whole slot set of one exercise is compared at once, so
            // training the same exercise twice in a session never fabricates a reset.
            for ((exerciseId, row) in pending) {
                val current = slotsByExercise[exerciseId].orEmpty()
                val previous = previousSlotsByExercise[exerciseId]
                rows += if (previous != null && previous != current) {
                    row.copy(slotChangedSincePrevious = true)
                } else {
                    row
                }
            }
            slotsByExercise.forEach { (exerciseId, slotIndexes) ->
                previousSlotsByExercise[exerciseId] = slotIndexes
            }
        }

        return rows
    }

    private fun muscleLoadHistory(
        completedSessions: List<com.ironlog.shared.backup.BackupWorkoutSession>,
        setsBySession: Map<Long, Map<Long, List<BackupWorkoutSet>>>,
        exerciseById: Map<Long, BackupExercise>,
        checkInsByDate: Map<String, com.ironlog.shared.readinessdata.ReadinessCheckIn>,
        timeZone: TimeZone,
    ): List<MuscleLoadEntry> {
        val entries = mutableListOf<MuscleLoadEntry>()
        for (session in completedSessions) {
            val exerciseSets = setsBySession[session.id].orEmpty()
            val checkIn = checkInsByDate[localDate(session.startTime, timeZone).toString()]
            val weightedSets = mutableMapOf<String, Double>()
            for ((exerciseId, sets) in exerciseSets) {
                val exercise = exerciseById[exerciseId] ?: continue
                val countedSets = sets.count(::isCountedSet).toDouble()
                if (countedSets <= 0.0) continue
                canonicalMuscle(exercise.primaryMuscleGroup)?.let { muscle ->
                    weightedSets[muscle] = (weightedSets[muscle] ?: 0.0) + countedSets
                }
                parseMuscles(exercise.secondaryMuscleGroups).forEach { muscle ->
                    weightedSets[muscle] = (weightedSets[muscle] ?: 0.0) + countedSets * 0.5
                }
            }
            for ((muscle, completedSets) in weightedSets) {
                entries += MuscleLoadEntry(
                    muscleGroup = muscle,
                    sessionId = session.id,
                    completedAtEpochMillis = session.endTime ?: session.startTime,
                    completedSets = completedSets,
                    soreness = checkIn?.muscleSoreness?.entries
                        ?.firstOrNull { it.key.name == muscle }
                        ?.value,
                )
            }
        }
        return entries
    }

    private fun trendSet(
        set: BackupWorkoutSet,
        intentionsBySetId: Map<Long, String>,
    ): TrendSetInput = TrendSetInput(
        setNumber = set.setNumber,
        reps = set.reps,
        weightKg = set.weightKg,
        rpe = set.rpe,
        setType = engineSetType(set),
        intention = SetIntentionCodes.normalize(intentionsBySetId[set.id]),
        completedAtEpochMillis = set.completedAt,
        orderingToken = set.id,
        id = set.id,
    )

    /** Primary muscle groups of a plan's exercises, in plan order. */
    private fun planMuscleGroups(
        planId: Long?,
        payload: BackupPayloadV1,
        exerciseById: Map<Long, BackupExercise>,
    ): List<String> {
        if (planId == null) return emptyList()
        return payload.planExercises
            .filter { it.planId == planId }
            .sortedBy { it.orderIndex }
            .mapNotNull { exerciseById[it.exerciseId]?.primaryMuscleGroup }
            .mapNotNull(::canonicalMuscle)
    }

    /** Primary muscle groups of the exercises that already carry sets in a session. */
    private fun sessionMuscleGroups(
        sessionId: Long,
        setsBySession: Map<Long, Map<Long, List<BackupWorkoutSet>>>,
        exerciseById: Map<Long, BackupExercise>,
    ): List<String> =
        setsBySession[sessionId].orEmpty().keys
            .mapNotNull { exerciseById[it]?.primaryMuscleGroup }
            .mapNotNull(::canonicalMuscle)

    /**
     * The progression goal is read from the immutable per-session target snapshot only.
     *
     * Without a snapshot the goal stays unknown. The plan row describes the plan *today*;
     * applying it to an older session would invent a comparison the athlete never
     * performed and could turn a plan edit into a false performance change.
     */
    private fun goal(target: BackupWorkoutPlanTarget?): ProgressionGoal {
        val progression = target?.progression
        val plannedReps = target?.target?.reps
        val plannedWeight = target?.target?.weightKg
        return ProgressionGoal(
            targetWeightKg = plannedWeight,
            targetRepsMin = progression?.minReps ?: plannedReps,
            targetRepsMax = progression?.maxReps ?: plannedReps,
            targetRpe = progression?.targetRpe,
            weightIncrementKg = explicitWeightStepKg(progression),
            scheme = progressionScheme(progression?.scheme),
        )
    }

    /**
     * The configured smallest load step in kilograms, or `null` when the store
     * does not carry one. A non-metric increment is intentionally not converted,
     * because the adapter must not guess a step.
     */
    private fun explicitWeightStepKg(config: BackupProgressionConfig?): Double? {
        val kilograms = config?.incrementKg
        if (kilograms != null && kilograms.isFinite() && kilograms > 0.0) return kilograms
        val value = config?.incrementValue ?: return null
        val unit = config.incrementUnit?.trim()?.uppercase()
        return if ((unit == null || unit == "METRIC") && value.isFinite() && value > 0.0) value else null
    }

    /** Maps the persisted Android scheme names onto the portable vocabulary. */
    private fun progressionScheme(raw: String?): ProgressionScheme =
        when (raw?.trim()?.uppercase()) {
            "MANUAL" -> ProgressionScheme.MANUAL
            "LINEAR", "LINEAR_LOAD" -> ProgressionScheme.LINEAR_LOAD
            "DOUBLE", "DOUBLE_PROGRESSION" -> ProgressionScheme.DOUBLE_PROGRESSION
            "TOTAL_REPS" -> ProgressionScheme.TOTAL_REPS
            "RPE_RIR", "RPE_TARGET" -> ProgressionScheme.RPE_TARGET
            else -> ProgressionScheme.UNKNOWN
        }

    private fun equipmentType(category: String?): EquipmentType =
        when (category?.trim()?.uppercase()) {
            "LANGHANTEL" -> EquipmentType.BARBELL
            "KURZHANTEL" -> EquipmentType.DUMBBELL
            "MASCHINE" -> EquipmentType.MACHINE
            "KABEL" -> EquipmentType.CABLE
            "EIGENGEWICHT" -> EquipmentType.BODYWEIGHT
            else -> EquipmentType.UNKNOWN
        }

    /**
     * Only an explicitly recorded flag takes a session out of the comparison
     * series. A legacy session (`null`) stays `UNKNOWN`, so today's deload mode is
     * never applied retroactively to history.
     */
    private fun deloadState(isDeload: Boolean?): DeloadState = when (isDeload) {
        true -> DeloadState.PLANNED_DELOAD
        false -> DeloadState.NONE
        null -> DeloadState.UNKNOWN
    }

    private fun engineSetType(set: BackupWorkoutSet): SetType =
        when (set.resolvedSetType().trim().uppercase()) {
            "NORMAL" -> SetType.NORMAL
            "WARMUP" -> SetType.WARMUP
            "FAILURE" -> SetType.FAILURE
            "DROP_SET" -> SetType.DROP_SET
            else -> SetType.UNKNOWN
        }

    private fun isCountedSet(set: BackupWorkoutSet): Boolean =
        when (set.resolvedSetType().trim().uppercase()) {
            "NORMAL", "DROP_SET", "FAILURE" -> true
            else -> false
        }

    private fun canonicalMuscle(raw: String): String? =
        raw.trim().uppercase().takeIf { it in muscleVocabulary }

    private fun parseMuscles(raw: String): List<String> =
        raw.split(',').mapNotNull(::canonicalMuscle).distinct()

    private fun localDate(epochMillis: Long, timeZone: TimeZone): LocalDate =
        Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date
}
