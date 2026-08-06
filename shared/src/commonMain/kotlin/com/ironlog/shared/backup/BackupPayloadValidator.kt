package com.ironlog.shared.backup

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

        val exerciseIds = payload.exercises.map { it.id }.toSet()
        val sessionIds = payload.workoutSessions.map { it.id }.toSet()
        val planIds = payload.trainingPlans.map { it.id }.toSet()
        val metaPlanIds = payload.metaTrainingPlans.map { it.id }.toSet()

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
        }

        payload.workoutSessions.forEach { session ->
            if (session.planId != null && session.planId !in planIds) {
                errors += "Workout session ${session.id} references missing plan ${session.planId}"
            }
            if (session.metaPlanId != null && session.metaPlanId !in metaPlanIds) {
                errors += "Workout session ${session.id} references missing meta plan ${session.metaPlanId}"
            }
        }

        payload.planExercises.forEach { planExercise ->
            if (planExercise.planId !in planIds) {
                errors += "Plan exercise ${planExercise.id} references missing plan ${planExercise.planId}"
            }
            if (planExercise.exerciseId !in exerciseIds) {
                errors += "Plan exercise ${planExercise.id} references missing exercise ${planExercise.exerciseId}"
            }
        }

        payload.personalRecords.forEach { record ->
            if (record.exerciseId !in exerciseIds) {
                errors += "Personal record ${record.id} references missing exercise ${record.exerciseId}"
            }
        }

        payload.metaPlanItems.forEach { item ->
            if (item.metaPlanId !in metaPlanIds) {
                errors += "Meta plan item ${item.id} references missing meta plan ${item.metaPlanId}"
            }
            if (item.trainingPlanId !in planIds) {
                errors += "Meta plan item ${item.id} references missing training plan ${item.trainingPlanId}"
            }
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
}
