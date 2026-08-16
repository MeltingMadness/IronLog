package com.ironlog.shared.backup

import kotlin.math.abs

data class BackupValidationResult(
    val isValid: Boolean,
    val errors: List<String>
)

object BackupPayloadValidator {
    fun validate(payload: BackupPayloadV1, currentSchemaVersion: Int): BackupValidationResult {
        val errors = mutableListOf<String>()

        if (payload.formatVersion != 1) {
            errors += "Unsupported backup format version: ${payload.formatVersion}"
        }
        if (payload.schemaVersion <= 0) {
            errors += "Backup schema version must be positive: ${payload.schemaVersion}"
        }
        if (payload.schemaVersion > currentSchemaVersion) {
            errors += "Backup schema version ${payload.schemaVersion} is newer than app schema $currentSchemaVersion"
        }

        if (payload.exercises.isEmpty()) {
            errors += "Backup contains no exercises; refusing import to avoid wiping the exercise catalog"
        }

        checkDuplicateIds(errors, payload.exercises.map { it.id }, "exercise")
        checkDuplicateIds(errors, payload.workoutSessions.map { it.id }, "workout session")
        checkDuplicateIds(errors, payload.workoutSets.map { it.id }, "workout set")
        checkDuplicateIds(errors, payload.trainingPlans.map { it.id }, "training plan")
        checkDuplicateIds(errors, payload.planExercises.map { it.id }, "plan exercise")
        checkDuplicateIds(errors, payload.personalRecords.map { it.id }, "personal record")
        checkDuplicateIds(errors, payload.metaTrainingPlans.map { it.id }, "meta training plan")
        checkDuplicateIds(errors, payload.metaPlanItems.map { it.id }, "meta plan item")
        checkDuplicateIds(errors, payload.metaPlanSkips.map { it.id }, "meta plan skip")
        checkDuplicateIds(errors, payload.workoutPlanTargets.map { it.id }, "workout plan target")
        checkDuplicateIds(errors, payload.progressionSuggestions.map { it.id }, "progression suggestion")
        checkPositiveIds(errors, payload.exercises.map { it.id }, "exercise")
        checkPositiveIds(errors, payload.workoutSessions.map { it.id }, "workout session")
        checkPositiveIds(errors, payload.workoutSets.map { it.id }, "workout set")
        checkPositiveIds(errors, payload.trainingPlans.map { it.id }, "training plan")
        checkPositiveIds(errors, payload.planExercises.map { it.id }, "plan exercise")
        checkPositiveIds(errors, payload.personalRecords.map { it.id }, "personal record")
        checkPositiveIds(errors, payload.metaTrainingPlans.map { it.id }, "meta training plan")
        checkPositiveIds(errors, payload.metaPlanItems.map { it.id }, "meta plan item")
        checkPositiveIds(errors, payload.metaPlanSkips.map { it.id }, "meta plan skip")
        checkPositiveIds(errors, payload.workoutPlanTargets.map { it.id }, "workout plan target")
        checkPositiveIds(errors, payload.progressionSuggestions.map { it.id }, "progression suggestion")

        if (payload.schemaVersion < PROGRESSION_SCHEMA_VERSION) {
            if (payload.workoutPlanTargets.isNotEmpty()) {
                errors += "Schema ${payload.schemaVersion} backup contains workout plan targets"
            }
            if (payload.progressionSuggestions.isNotEmpty()) {
                errors += "Schema ${payload.schemaVersion} backup contains progression suggestions"
            }
            payload.planExercises.forEach { planExercise ->
                if (planExercise.progression != BackupProgressionConfig()) {
                    errors += "Schema ${payload.schemaVersion} plan exercise ${planExercise.id} contains progression metadata"
                }
            }
            payload.workoutSets.forEach { set ->
                if (set.planTargetSnapshotId != null) {
                    errors += "Schema ${payload.schemaVersion} workout set ${set.id} contains a target snapshot link"
                }
            }
        }

        val exerciseIds = payload.exercises.map { it.id }.toSet()
        val sessionIds = payload.workoutSessions.map { it.id }.toSet()
        val planIds = payload.trainingPlans.map { it.id }.toSet()
        val metaPlanIds = payload.metaTrainingPlans.map { it.id }.toSet()
        val sessionsById = payload.workoutSessions.associateBy { it.id }
        val setsById = payload.workoutSets.associateBy { it.id }
        val targetsById = payload.workoutPlanTargets.associateBy { it.id }

        val activeSessions = payload.workoutSessions.count { it.endTime == null }
        if (activeSessions > 1) {
            errors += "Backup contains more than one active session"
        }

        payload.workoutSets.forEach { set ->
            if (set.sessionId !in sessionIds) {
                errors += "Workout set ${set.id} references missing session ${set.sessionId}"
            }
            if (set.exerciseId !in exerciseIds) {
                errors += "Workout set ${set.id} references missing exercise ${set.exerciseId}"
            }
            set.planTargetSnapshotId?.let { snapshotId ->
                val target = targetsById[snapshotId]
                if (target == null) {
                    errors += "Workout set ${set.id} references missing target snapshot $snapshotId"
                } else if (target.sessionId != set.sessionId || target.exerciseId != set.exerciseId) {
                    errors += "Workout set ${set.id} does not match target snapshot $snapshotId"
                }
            }
        }

        payload.workoutSessions.forEach { session ->
            if (session.planId != null && session.planId !in planIds) {
                errors += "Workout session ${session.id} references missing plan ${session.planId}"
            }
            if (session.metaPlanId != null && session.metaPlanId !in metaPlanIds) {
                errors += "Workout session ${session.id} references missing meta plan ${session.metaPlanId}"
            }
        }

        checkDuplicateKeys(
            errors = errors,
            values = payload.planExercises,
            key = { it.planId to it.orderIndex },
            label = "plan exercise plan/order"
        )
        payload.planExercises.forEach { planExercise ->
            if (planExercise.planId !in planIds) {
                errors += "Plan exercise ${planExercise.id} references missing plan ${planExercise.planId}"
            }
            if (planExercise.exerciseId !in exerciseIds) {
                errors += "Plan exercise ${planExercise.id} references missing exercise ${planExercise.exerciseId}"
            }
            validateTarget(
                errors = errors,
                target = BackupProgressionTarget(
                    sets = planExercise.targetSets,
                    reps = planExercise.targetReps,
                    weightKg = planExercise.targetWeightKg
                ),
                config = planExercise.progression,
                label = "Plan exercise ${planExercise.id}"
            )
        }

        payload.personalRecords.forEach { record ->
            if (record.exerciseId !in exerciseIds) {
                errors += "Personal record ${record.id} references missing exercise ${record.exerciseId}"
            }
        }

        checkDuplicateKeys(
            errors = errors,
            values = payload.metaPlanItems,
            key = { it.metaPlanId to it.orderIndex },
            label = "meta plan item meta plan/order"
        )
        payload.metaPlanItems.forEach { item ->
            if (item.metaPlanId !in metaPlanIds) {
                errors += "Meta plan item ${item.id} references missing meta plan ${item.metaPlanId}"
            }
            if (item.trainingPlanId !in planIds) {
                errors += "Meta plan item ${item.id} references missing training plan ${item.trainingPlanId}"
            }
        }

        // Historical skips may remain valid after rotation editing, so they only
        // need their parent plan IDs; they must not match a current item.
        payload.metaPlanSkips.forEach { skip ->
            if (skip.metaPlanId !in metaPlanIds) {
                errors += "Meta plan skip ${skip.id} references missing meta plan ${skip.metaPlanId}"
            }
            if (skip.trainingPlanId !in planIds) {
                errors += "Meta plan skip ${skip.id} references missing training plan ${skip.trainingPlanId}"
            }
        }

        checkDuplicateKeys(
            errors = errors,
            values = payload.workoutPlanTargets,
            key = { it.sessionId to it.orderIndex },
            label = "workout plan target session/order"
        )
        payload.workoutPlanTargets.forEach { target ->
            if (target.orderIndex < 0) {
                errors += "Workout plan target ${target.id} has a negative order index"
            }
            val session = sessionsById[target.sessionId]
            if (session == null) {
                errors += "Workout plan target ${target.id} references missing session ${target.sessionId}"
            } else if (session.planId == null || session.planId != target.planId) {
                errors += "Workout plan target ${target.id} does not match session plan ${session.planId}"
            }
            if (target.planId !in planIds) {
                errors += "Workout plan target ${target.id} references missing plan ${target.planId}"
            }
            if (target.exerciseId !in exerciseIds) {
                errors += "Workout plan target ${target.id} references missing exercise ${target.exerciseId}"
            }
            validateTarget(
                errors = errors,
                target = target.target,
                config = target.progression,
                label = "Workout plan target ${target.id}"
            )
        }

        checkDuplicateKeys(
            errors = errors,
            values = payload.progressionSuggestions,
            key = { it.sourceTargetSnapshotId to it.sourceProgression.ruleRevision },
            label = "progression suggestion target/revision"
        )
        payload.progressionSuggestions.forEach { suggestion ->
            validateSuggestion(
                errors = errors,
                suggestion = suggestion,
                sessionsById = sessionsById,
                setsById = setsById,
                targetsById = targetsById,
                planIds = planIds,
                exerciseIds = exerciseIds
            )
        }

        return BackupValidationResult(
            isValid = errors.isEmpty(),
            errors = errors
        )
    }

    private fun checkDuplicateIds(
        errors: MutableList<String>,
        ids: List<Long>,
        label: String
    ) {
        ids.groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
            .forEach { id ->
                errors += "Backup contains duplicate $label id: $id"
            }
    }

    private fun checkPositiveIds(
        errors: MutableList<String>,
        ids: List<Long>,
        label: String
    ) {
        ids.filter { it <= 0L }.sorted().forEach { id ->
            errors += "$label id must be positive: $id"
        }
    }

    private fun <T, K> checkDuplicateKeys(
        errors: MutableList<String>,
        values: List<T>,
        key: (T) -> K,
        label: String
    ) {
        values.groupingBy(key)
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .forEach { duplicate ->
                errors += "Backup contains duplicate $label: $duplicate"
            }
    }

    private fun validateSuggestion(
        errors: MutableList<String>,
        suggestion: BackupProgressionSuggestion,
        sessionsById: Map<Long, BackupWorkoutSession>,
        setsById: Map<Long, BackupWorkoutSet>,
        targetsById: Map<Long, BackupWorkoutPlanTarget>,
        planIds: Set<Long>,
        exerciseIds: Set<Long>
    ) {
        if (suggestion.orderIndex < 0) {
            errors += "Progression suggestion ${suggestion.id} has a negative order index"
        }
        val session = sessionsById[suggestion.sourceSessionId]
        if (session == null) {
            errors += "Progression suggestion ${suggestion.id} references missing source session ${suggestion.sourceSessionId}"
        } else if (session.endTime == null) {
            errors += "Progression suggestion ${suggestion.id} references an active source session"
        }
        if (suggestion.planId !in planIds) {
            errors += "Progression suggestion ${suggestion.id} references missing plan ${suggestion.planId}"
        }
        if (suggestion.exerciseId !in exerciseIds) {
            errors += "Progression suggestion ${suggestion.id} references missing exercise ${suggestion.exerciseId}"
        }

        val sourceTarget = targetsById[suggestion.sourceTargetSnapshotId]
        if (sourceTarget == null) {
            errors += "Progression suggestion ${suggestion.id} references missing source target ${suggestion.sourceTargetSnapshotId}"
        } else if (
            suggestion.sourceSessionId != sourceTarget.sessionId ||
            suggestion.planId != sourceTarget.planId ||
            suggestion.exerciseId != sourceTarget.exerciseId ||
            suggestion.orderIndex != sourceTarget.orderIndex ||
            suggestion.supersetGroupId != sourceTarget.supersetGroupId ||
            suggestion.sourceTarget != sourceTarget.target ||
            suggestion.sourceProgression != sourceTarget.progression
        ) {
            errors += "Progression suggestion ${suggestion.id} does not match source target ${sourceTarget.id}"
        }

        validateTarget(
            errors = errors,
            target = suggestion.sourceTarget,
            config = suggestion.sourceProgression,
            label = "Progression suggestion ${suggestion.id} source"
        )
        suggestion.suggestedTarget?.let {
            validateTargetOnly(errors, it, "Progression suggestion ${suggestion.id} suggested target")
        }
        suggestion.finalTarget?.let {
            validateTargetOnly(errors, it, "Progression suggestion ${suggestion.id} final target")
        }
        if (suggestion.sourceProgression.scheme == "MANUAL") {
            errors += "Progression suggestion ${suggestion.id} stores an outcome for a manual target"
        }

        val countedIds = suggestion.countedSetIds
        if (countedIds.any { it <= 0L }) {
            errors += "Progression suggestion ${suggestion.id} has non-positive evidence ids"
        }
        if (countedIds.distinct().size != countedIds.size) {
            errors += "Progression suggestion ${suggestion.id} has duplicate evidence ids"
        }
        countedIds.forEach { setId ->
            val set = setsById[setId]
            when {
                set == null -> {
                    errors += "Progression suggestion ${suggestion.id} references missing workout set $setId"
                }
                set.isWarmup -> {
                    errors += "Progression suggestion ${suggestion.id} references warmup set $setId"
                }
                set.planTargetSnapshotId != suggestion.sourceTargetSnapshotId ||
                    set.sessionId != suggestion.sourceSessionId ||
                    set.exerciseId != suggestion.exerciseId -> {
                    errors += "Workout set $setId does not belong to progression suggestion ${suggestion.id}"
                }
            }
        }

        if (suggestion.outcomeType !in OUTCOME_TYPES) {
            errors += "Unknown progression outcome type: ${suggestion.outcomeType}"
        }
        if (suggestion.reasonCode !in REASON_CODES) {
            errors += "Unknown progression reason code: ${suggestion.reasonCode}"
        }
        if (suggestion.streakEffect !in STREAK_EFFECTS) {
            errors += "Unknown progression streak effect: ${suggestion.streakEffect}"
        }
        if (suggestion.status !in STATUSES) {
            errors += "Unknown progression suggestion status: ${suggestion.status}"
        }
        suggestion.reasonArguments.forEach { (key, value) ->
            if (key !in REASON_ARGUMENT_KEYS) {
                errors += "Unknown progression reason argument: $key"
            }
            if (!value.isFinite()) {
                errors += "Progression reason argument $key must be finite"
            }
        }

        when (suggestion.outcomeType) {
            "PROPOSE_CHANGE" -> {
                if (suggestion.suggestedTarget == null) {
                    errors += "Proposed progression suggestion ${suggestion.id} has no suggested target"
                }
                if (suggestion.status !in DECISION_STATUSES) {
                    errors += "Proposed progression suggestion ${suggestion.id} has invalid status ${suggestion.status}"
                }
                if (countedIds.size != suggestion.sourceTarget.sets) {
                    errors += "Proposed progression suggestion ${suggestion.id} has incomplete evidence"
                }
            }
            "KEEP_TARGET" -> {
                if (suggestion.suggestedTarget != null) {
                    errors += "Kept progression suggestion ${suggestion.id} has a suggested target"
                }
                if (suggestion.status != "INFORMATIONAL") {
                    errors += "Kept progression suggestion ${suggestion.id} must be informational"
                }
                if (countedIds.size != suggestion.sourceTarget.sets) {
                    errors += "Kept progression suggestion ${suggestion.id} has incomplete evidence"
                }
            }
            "INSUFFICIENT_DATA" -> {
                if (suggestion.suggestedTarget != null) {
                    errors += "Insufficient-data suggestion ${suggestion.id} has a suggested target"
                }
                if (suggestion.status != "INFORMATIONAL") {
                    errors += "Insufficient-data suggestion ${suggestion.id} must be informational"
                }
            }
        }

        validateDecisionState(errors, suggestion)
    }

    private fun validateDecisionState(
        errors: MutableList<String>,
        suggestion: BackupProgressionSuggestion
    ) {
        if (suggestion.createdAtEpochMillis < 0L) {
            errors += "Progression suggestion ${suggestion.id} has a negative creation time"
        }
        val decidedAt = suggestion.decidedAtEpochMillis
        if (decidedAt != null && decidedAt < 0L) {
            errors += "Progression suggestion ${suggestion.id} has a negative decision time"
        }
        if (decidedAt != null && decidedAt < suggestion.createdAtEpochMillis) {
            errors += "Progression suggestion ${suggestion.id} was decided before creation"
        }

        when (suggestion.status) {
            "PENDING", "INFORMATIONAL" -> {
                if (decidedAt != null || suggestion.finalTarget != null) {
                    errors += "Undecided progression suggestion ${suggestion.id} contains decision data"
                }
            }
            "ACCEPTED" -> {
                if (decidedAt == null || suggestion.finalTarget == null) {
                    errors += "Accepted progression suggestion ${suggestion.id} lacks decision data"
                }
                if (suggestion.finalTarget != null &&
                    suggestion.wasEdited != (suggestion.finalTarget != suggestion.suggestedTarget)
                ) {
                    errors += "Accepted progression suggestion ${suggestion.id} has inconsistent edit state"
                }
            }
            "REJECTED", "STALE" -> {
                if (decidedAt == null || suggestion.finalTarget != null) {
                    errors += "Rejected or stale progression suggestion ${suggestion.id} has invalid decision data"
                }
            }
        }
        if (suggestion.status != "ACCEPTED" && suggestion.wasEdited) {
            errors += "Non-accepted progression suggestion ${suggestion.id} is marked edited"
        }
    }

    private fun validateTarget(
        errors: MutableList<String>,
        target: BackupProgressionTarget,
        config: BackupProgressionConfig,
        label: String
    ) {
        validateTargetOnly(errors, target, label)
        validateConfig(errors, target, config, label)
    }

    private fun validateTargetOnly(
        errors: MutableList<String>,
        target: BackupProgressionTarget,
        label: String
    ) {
        if (target.sets <= 0) errors += "$label target sets must be positive"
        if (target.reps <= 0) errors += "$label target reps must be positive"
        if (!target.weightKg.isFinite() || target.weightKg < 0.0) {
            errors += "$label target weight must be finite and non-negative"
        }
    }

    private fun validateConfig(
        errors: MutableList<String>,
        target: BackupProgressionTarget,
        config: BackupProgressionConfig,
        label: String
    ) {
        if (config.scheme !in SCHEMES) {
            errors += "$label has unknown progression scheme ${config.scheme}"
            return
        }
        if (config.ruleRevision <= 0) {
            errors += "$label progression rule revision must be positive"
        }

        val incrementFieldsPresent = config.incrementValue != null &&
            config.incrementUnit != null &&
            config.incrementKg != null
        val incrementFieldsAllNull = config.incrementValue == null &&
            config.incrementUnit == null &&
            config.incrementKg == null
        val minMaxAllNull = config.minReps == null && config.maxReps == null
        val rpeAllNull = config.targetRpe == null && config.rpeTolerance == null

        if (config.scheme == "MANUAL") {
            if (config.ruleRevision != DEFAULT_RULE_REVISION) {
                errors += "$label manual progression must use rule revision $DEFAULT_RULE_REVISION"
            }
            if (!incrementFieldsAllNull || !minMaxAllNull ||
                config.targetTotalReps != null || !rpeAllNull
            ) {
                errors += "$label manual progression contains active fields"
            }
            if (config.stallThreshold != DEFAULT_STALL_THRESHOLD ||
                config.backoffPercent != DEFAULT_BACKOFF_PERCENT
            ) {
                errors += "$label manual progression does not use default failure policy"
            }
            return
        }

        if (!incrementFieldsPresent) {
            errors += "$label active progression lacks a complete increment"
        } else {
            val incrementValue = requireNotNull(config.incrementValue)
            val incrementUnit = requireNotNull(config.incrementUnit)
            val incrementKg = requireNotNull(config.incrementKg)
            if (!incrementValue.isFinite() || incrementValue <= 0.0) {
                errors += "$label increment value must be finite and positive"
            }
            if (incrementUnit !in UNITS) {
                errors += "$label has unknown increment unit $incrementUnit"
            }
            if (!incrementKg.isFinite() || incrementKg <= 0.0) {
                errors += "$label increment kilograms must be finite and positive"
            }
            if (incrementValue.isFinite() && incrementKg.isFinite() && incrementUnit in UNITS) {
                val converted = if (incrementUnit == "IMPERIAL") {
                    incrementValue / KG_TO_LB
                } else {
                    incrementValue
                }
                if (!converted.isFinite() || abs(converted - incrementKg) > STEP_STORAGE_TOLERANCE_KG) {
                    errors += "$label increment kilograms do not match the original unit"
                }
            }
            if (target.weightKg.isFinite() && incrementKg.isFinite() &&
                target.weightKg + incrementKg <= target.weightKg
            ) {
                errors += "$label increment does not increase target weight"
            }
        }
        if (config.stallThreshold !in 1..6) {
            errors += "$label stall threshold is outside 1..6"
        }
        if (!config.backoffPercent.isFinite() || config.backoffPercent !in 1.0..30.0) {
            errors += "$label backoff percent is outside 1..30"
        }

        when (config.scheme) {
            "LINEAR" -> {
                if (!minMaxAllNull || config.targetTotalReps != null || !rpeAllNull) {
                    errors += "$label linear progression contains fields for another scheme"
                }
            }
            "DOUBLE" -> {
                val minReps = config.minReps
                val maxReps = config.maxReps
                if (minReps == null || maxReps == null ||
                    minReps < 1 || minReps > target.reps ||
                    maxReps < target.reps || maxReps < minReps ||
                    config.targetTotalReps != null || !rpeAllNull
                ) {
                    errors += "$label has invalid double-progression rep bounds"
                }
            }
            "TOTAL_REPS" -> {
                if (config.targetTotalReps == null || config.targetTotalReps <= 0L ||
                    !minMaxAllNull || !rpeAllNull
                ) {
                    errors += "$label has invalid total-reps progression fields"
                }
            }
            "RPE_RIR" -> {
                val targetRpe = config.targetRpe
                val tolerance = config.rpeTolerance
                if (targetRpe == null || !targetRpe.isFinite() || targetRpe !in 1.0..10.0 ||
                    tolerance == null || !tolerance.isFinite() || tolerance !in 0.0..2.0 ||
                    !minMaxAllNull || config.targetTotalReps != null
                ) {
                    errors += "$label has invalid RPE/RIR progression fields"
                }
            }
        }
    }

    private val SCHEMES = setOf("MANUAL", "LINEAR", "DOUBLE", "TOTAL_REPS", "RPE_RIR")
    private val UNITS = setOf("METRIC", "IMPERIAL")
    private val OUTCOME_TYPES = setOf("PROPOSE_CHANGE", "KEEP_TARGET", "INSUFFICIENT_DATA")
    private val REASON_CODES = setOf(
        "REP_TARGET_ADVANCED",
        "LOAD_ADVANCED",
        "TOTAL_REPS_COMPLETED",
        "RPE_WITHIN_TARGET",
        "REPEAT_TARGET",
        "STALL_BACKOFF",
        "BACKOFF_FLOOR_REACHED",
        "MANUAL_WEIGHT_DEVIATION",
        "TOO_FEW_WORK_SETS",
        "RPE_MISSING",
        "RPE_INVALID",
        "CONFIG_INVALID",
        "RULE_REVISION_UNSUPPORTED",
        "SET_NUMBER_INVALID",
        "SET_VALUE_INVALID"
    )
    private val STREAK_EFFECTS = setOf("INCREMENT", "RESET", "IGNORE")
    private val STATUSES = setOf("PENDING", "ACCEPTED", "REJECTED", "STALE", "INFORMATIONAL")
    private val DECISION_STATUSES = setOf("PENDING", "ACCEPTED", "REJECTED", "STALE")
    private val REASON_ARGUMENT_KEYS = setOf(
        "expectedWeightKg",
        "actualWeightKg",
        "targetSets",
        "actualWorkSets",
        "targetReps",
        "actualReps",
        "achievedTotalReps",
        "targetTotalReps",
        "highestRpe",
        "targetRpe",
        "tolerance",
        "stepOriginalValue",
        "backoffPercent"
    )

    private const val PROGRESSION_SCHEMA_VERSION = 11
    private const val DEFAULT_STALL_THRESHOLD = 2
    private const val DEFAULT_BACKOFF_PERCENT = 10.0
    private const val DEFAULT_RULE_REVISION = 1
    private const val KG_TO_LB = 2.2046226218
    private const val STEP_STORAGE_TOLERANCE_KG = 0.000001
}
