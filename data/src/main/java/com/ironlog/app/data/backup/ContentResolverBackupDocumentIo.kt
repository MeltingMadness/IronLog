package com.ironlog.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

class ContentResolverBackupDocumentIo(
    private val contentResolver: ContentResolver,
    private val maxReadBytes: Int = DEFAULT_MAX_READ_BYTES
) : BackupDocumentIo {

    override suspend fun writeVerified(uri: Uri, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val expectedSha256 = bytes.sha256Hex()
            val output = contentResolver.openOutputStream(uri)
                ?: throw IOException("Backup output stream could not be opened")
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }

            // Readback verification only: the URI may have pointed at an
            // existing good document before this write, so a failed or partial
            // write must never delete/truncate it here.
            val readBack = readInternal(uri, limitBytes = bytes.size + 1)
            if (readBack.sha256Hex() != expectedSha256) {
                throw IOException(
                    "Backup readback SHA-256 mismatch: expected $expectedSha256"
                )
            }
        }
    }

    override suspend fun readBytes(uri: Uri): ByteArray {
        return withContext(Dispatchers.IO) { readInternal(uri, limitBytes = maxReadBytes) }
    }

    private fun readInternal(uri: Uri, limitBytes: Int): ByteArray {
        val input = contentResolver.openInputStream(uri)
            ?: throw IOException("Backup input stream could not be opened")

        return input.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(READ_CHUNK_SIZE)
            var total = 0
            while (true) {
                val read = stream.read(chunk)
                if (read == -1) break
                total += read
                if (total > limitBytes) {
                    throw IOException(
                        "Backup exceeds the $limitBytes byte read limit"
                    )
                }
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }
    }

    private companion object {
        const val DEFAULT_MAX_READ_BYTES = 25 * 1024 * 1024
        const val READ_CHUNK_SIZE = 64 * 1024
    }
}
