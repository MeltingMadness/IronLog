package com.ironlog.app.data.backup

internal data class BackupSnapshot(
    val exercises: List<BackupExercise>,
    val workoutSessions: List<BackupWorkoutSession>,
    val workoutSets: List<BackupWorkoutSet>,
    val trainingPlans: List<BackupTrainingPlan>,
    val planExercises: List<BackupPlanExercise>,
    val personalRecords: List<BackupPersonalRecord>,
    val metaTrainingPlans: List<BackupMetaTrainingPlan>,
    val metaPlanItems: List<BackupMetaPlanItem>
) {
    fun canonicalPayload(schemaVersion: Int): BackupPayloadV1 = BackupPayloadV1(
        formatVersion = FORMAT_VERSION,
        schemaVersion = schemaVersion,
        appVersion = "",
        exportedAtEpochMillis = 0L,
        exercises = exercises,
        workoutSessions = workoutSessions,
        workoutSets = workoutSets,
        trainingPlans = trainingPlans,
        planExercises = planExercises,
        personalRecords = personalRecords,
        metaTrainingPlans = metaTrainingPlans,
        metaPlanItems = metaPlanItems
    )

    fun toExportPayload(
        schemaVersion: Int,
        appVersion: String,
        exportedAtEpochMillis: Long
    ): BackupPayloadV1 = BackupPayloadV1(
        formatVersion = FORMAT_VERSION,
        schemaVersion = schemaVersion,
        appVersion = appVersion,
        exportedAtEpochMillis = exportedAtEpochMillis,
        exercises = exercises,
        workoutSessions = workoutSessions,
        workoutSets = workoutSets,
        trainingPlans = trainingPlans,
        planExercises = planExercises,
        personalRecords = personalRecords,
        metaTrainingPlans = metaTrainingPlans,
        metaPlanItems = metaPlanItems
    )

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
