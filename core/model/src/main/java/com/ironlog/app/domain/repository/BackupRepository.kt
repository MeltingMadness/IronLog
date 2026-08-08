package com.ironlog.app.domain.repository

import android.net.Uri

data class BackupContentCounts(
    val exercises: Int,
    val workoutSessions: Int,
    val workoutSets: Int,
    val trainingPlans: Int,
    val planExercises: Int,
    val personalRecords: Int,
    val metaTrainingPlans: Int,
    val metaPlanItems: Int,
    val metaPlanSkips: Int
)

data class BackupImportPreview(
    val sha256: String,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAtEpochMillis: Long,
    val counts: BackupContentCounts,
    val validationErrors: List<String> = emptyList()
) {
    val isValid: Boolean get() = validationErrors.isEmpty()
}

data class RecoveryBackup(
    val timestampMillis: Long,
    val sha256: String,
    val sizeBytes: Long
)

/**
 * Two-phase backup contract:
 *
 * 1. [previewImport] reads and validates the document and returns the SHA-256
 *    of the exact bytes it read.
 * 2. [importBackup] re-reads the document, rejects it when the hash changed,
 *    saves a verified recovery snapshot of the current state, and only then
 *    replaces all nine workout-domain tables in one transaction.
 */
interface BackupRepository {
    suspend fun exportBackup(uri: Uri)

    suspend fun previewImport(uri: Uri): BackupImportPreview

    suspend fun importBackup(uri: Uri, expectedSha256: String)

    suspend fun latestRecovery(): RecoveryBackup?

    suspend fun restoreLatestRecovery(): RecoveryBackup?

    suspend fun resetUserData()

    /**
     * Compatibility bridge for callers that have not migrated to the two-phase
     * flow yet. It previews first and passes the previewed hash to the real
     * import, so the re-read/hash guard still applies.
     */
    @Deprecated("Use previewImport(uri) followed by importBackup(uri, preview.sha256)")
    suspend fun importBackup(uri: Uri) {
        val preview = previewImport(uri)
        importBackup(uri, preview.sha256)
    }
}
