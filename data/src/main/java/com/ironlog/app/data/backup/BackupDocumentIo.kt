package com.ironlog.app.data.backup

import android.net.Uri

/**
 * Boundary for reading and writing backup documents through the content
 * provider layer. Production writes verify the artifact by reading it back and
 * comparing SHA-256 before returning; read operations are size-bounded.
 */
interface BackupDocumentIo {
    suspend fun writeVerified(uri: Uri, bytes: ByteArray)

    suspend fun readBytes(uri: Uri): ByteArray
}
