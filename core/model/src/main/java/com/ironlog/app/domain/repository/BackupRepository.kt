package com.ironlog.app.domain.repository

import android.net.Uri

interface BackupRepository {
    suspend fun exportBackup(uri: Uri)
    suspend fun importBackup(uri: Uri)
    suspend fun resetUserData()
}
