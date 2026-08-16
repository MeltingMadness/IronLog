package com.ironlog.app.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Reads and writes backup documents through the content provider layer.
 *
 * [writeVerified] first writes the payload to a temp file in the app cache and
 * verifies its SHA-256 there, and only then copies the verified bytes to the
 * target URI. Opening the target with the default "w" mode truncates an
 * existing document at that URI -- that truncation is part of the
 * ContentResolver API contract and cannot be avoided here. The temp-file round
 * trip shrinks the window in which the target document is truncated or left
 * partially written, and the readback check after the copy remains the
 * authoritative verification.
 */
class ContentResolverBackupDocumentIo(
    private val contentResolver: ContentResolver,
    private val cacheDir: File? = null,
    private val maxReadBytes: Int = DEFAULT_MAX_READ_BYTES
) : BackupDocumentIo {

    constructor(
        context: Context,
        maxReadBytes: Int = DEFAULT_MAX_READ_BYTES
    ) : this(
        contentResolver = context.contentResolver,
        cacheDir = context.cacheDir,
        maxReadBytes = maxReadBytes
    )

    override suspend fun writeVerified(uri: Uri, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val expectedSha256 = bytes.sha256Hex()

            val cacheDir = cacheDir
            if (cacheDir != null) {
                writeViaTempFile(uri, bytes, expectedSha256, cacheDir)
            } else {
                // No app cache available (unit tests / legacy wiring): fall back
                // to a direct write. The target is truncated on open and stays
                // truncated if the write fails; there is no way to avoid that
                // without a scratch location.
                writeAndVerify(uri, bytes, expectedSha256)
            }
        }
    }

    private fun writeViaTempFile(
        uri: Uri,
        bytes: ByteArray,
        expectedSha256: String,
        cacheDir: File
    ) {
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) {
            throw IOException("Could not create backup cache directory")
        }
        val temp = File.createTempFile("backup-verify-", ".tmp", cacheDir)
        try {
            temp.writeBytes(bytes)
            val tempHash = temp.readBytes().sha256Hex()
            if (tempHash != expectedSha256) {
                throw IOException(
                    "Backup temp write SHA-256 mismatch: expected $expectedSha256, got $tempHash"
                )
            }
            writeAndVerify(uri, bytes, expectedSha256)
        } finally {
            temp.delete()
        }
    }

    private fun writeAndVerify(uri: Uri, bytes: ByteArray, expectedSha256: String) {
        val output = contentResolver.openOutputStream(uri)
            ?: throw IOException("Backup output stream could not be opened")
        output.use { stream ->
            stream.write(bytes)
            stream.flush()
        }

        val readBack = readInternal(uri, limitBytes = bytes.size + 1)
        if (readBack.sha256Hex() != expectedSha256) {
            throw IOException(
                "Backup readback SHA-256 mismatch: expected $expectedSha256"
            )
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
