package com.ironlog.app.data.backup

import com.ironlog.app.domain.repository.RecoveryBackup

/**
 * App-private store for verified pre-import state snapshots. Implementations
 * must persist bytes durably and verify them by readback/hash before exposing
 * them as available, and must retain only a small bounded number of snapshots.
 */
interface RecoveryBackupStore {
    suspend fun latest(): RecoveryBackup?

    suspend fun save(bytes: ByteArray): RecoveryBackup

    suspend fun loadLatestBytes(): ByteArray?

    suspend fun delete(backup: RecoveryBackup)
}
