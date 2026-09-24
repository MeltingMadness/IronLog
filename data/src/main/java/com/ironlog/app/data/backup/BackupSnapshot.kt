package com.ironlog.app.data.backup

import com.ironlog.shared.readinessdata.ReadinessData

internal data class BackupSnapshot(
    val exercises: List<BackupExercise>,
    val workoutSessions: List<BackupWorkoutSession>,
    val workoutSets: List<BackupWorkoutSet>,
    val trainingPlans: List<BackupTrainingPlan>,
    val planExercises: List<BackupPlanExercise>,
    val personalRecords: List<BackupPersonalRecord>,
    val metaTrainingPlans: List<BackupMetaTrainingPlan>,
    val metaPlanItems: List<BackupMetaPlanItem>,
    val metaPlanSkips: List<BackupMetaPlanSkip>,
    val workoutPlanTargets: List<BackupWorkoutPlanTarget> = emptyList(),
    val progressionSuggestions: List<BackupProgressionSuggestion> = emptyList(),
    /**
     * Readiness side channel. Already pruned against the snapshot's workout-set ids, so an
     * export can never carry an intention that points at a set the same document does not contain.
     */
    val readinessData: ReadinessData = ReadinessData()
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
        metaPlanItems = metaPlanItems,
        metaPlanSkips = metaPlanSkips,
        workoutPlanTargets = workoutPlanTargets,
        progressionSuggestions = progressionSuggestions,
        readinessData = readinessData
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
        metaPlanItems = metaPlanItems,
        metaPlanSkips = metaPlanSkips,
        workoutPlanTargets = workoutPlanTargets,
        progressionSuggestions = progressionSuggestions,
        readinessData = readinessData
    )

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
