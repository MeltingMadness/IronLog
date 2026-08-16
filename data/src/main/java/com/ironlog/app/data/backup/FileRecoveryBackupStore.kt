package com.ironlog.app.data.backup

import android.content.Context
import com.ironlog.app.domain.repository.RecoveryBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

class FileRecoveryBackupStore(
    private val backupDir: File,
    private val maxRetained: Int = DEFAULT_MAX_RETAINED,
    private val nowMillis: () -> Long = System::currentTimeMillis
) : RecoveryBackupStore {

    constructor(
        context: Context,
        maxRetained: Int = DEFAULT_MAX_RETAINED,
        nowMillis: () -> Long = System::currentTimeMillis
    ) : this(
        backupDir = File(context.filesDir, BACKUP_DIR_NAME),
        maxRetained = maxRetained,
        nowMillis = nowMillis
    )

    private val mutex = Mutex()

    override suspend fun latest(): RecoveryBackup? {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val latest = listStored().deterministicLatest() ?: return@withContext null
                val actualHash = latest.file.readBytes().sha256Hex()
                if (actualHash != latest.sha256) {
                    throw IOException(
                        "Recovery backup readback hash mismatch: expected ${latest.sha256}, got $actualHash"
                    )
                }
                latest.metadata()
            }
        }
    }

    override suspend fun save(bytes: ByteArray): RecoveryBackup {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val sha256 = bytes.sha256Hex()
                ensureDir()

                // A monotonic timestamp derived from the existing maximum keeps
                // saves unique even when the clock moves backwards or repeats.
                val existingMax = listStored().maxOfOrNull { it.timestampMillis }
                val wallClock = nowMillis()
                val timestamp = existingMax
                    ?.let { max -> if (max >= wallClock) max.plusSaturated() else wallClock }
                    ?: wallClock
                val temp = File(backupDir, fileName(timestamp, sha256, TEMP_SUFFIX))
                val target = File(backupDir, fileName(timestamp, sha256, FINAL_SUFFIX))
                try {
                    RandomAccessFile(temp, "rw").use { output ->
                        output.write(bytes)
                        output.fd.sync()
                    }
                    if (!temp.renameTo(target)) {
                        throw IOException("Could not move recovery backup into place")
                    }

                    val readBackHash = target.readBytes().sha256Hex()
                    if (readBackHash != sha256) {
                        target.delete()
                        throw IOException(
                            "Recovery backup readback hash mismatch: expected $sha256, got $readBackHash"
                        )
                    }

                    prune(protected = target)
                    if (!target.exists() || target.readBytes().sha256Hex() != sha256) {
                        throw IOException("Recovery backup was lost or corrupted after pruning")
                    }
                    RecoveryBackup(
                        timestampMillis = timestamp,
                        sha256 = sha256,
                        sizeBytes = bytes.size.toLong()
                    )
                } catch (error: Throwable) {
                    runCatching { temp.delete() }
                    throw error
                }
            }
        }
    }

    override suspend fun loadLatestBytes(): ByteArray? {
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                val stored = listStored().deterministicLatest()
                    ?: return@withContext null
                val bytes = stored.file.readBytes()
                val actualHash = bytes.sha256Hex()
                if (actualHash != stored.sha256) {
                    throw IOException(
                        "Recovery backup readback hash mismatch: expected ${stored.sha256}, got $actualHash"
                    )
                }
                bytes
            }
        }
    }

    override suspend fun delete(backup: RecoveryBackup) {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                File(backupDir, fileName(backup.timestampMillis, backup.sha256, FINAL_SUFFIX)).delete()
            }
        }
    }

    private fun ensureDir() {
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw IOException("Could not create recovery backup directory")
        }
    }

    private fun prune(protected: File) {
        val retained = maxRetained.coerceAtLeast(1)
        val stored = listStored()
        if (stored.size <= retained) return
        stored.filter { it.file != protected }
            .take(stored.size - retained)
            .forEach { it.file.delete() }
    }

    private fun listStored(): List<StoredRecovery> {
        if (!backupDir.isDirectory) return emptyList()
        val files = backupDir.listFiles() ?: return emptyList()

        // A crash between writing the temp file and renameTo leaves an orphaned
        // recovery-*.tmp behind. Every operation runs under the store mutex, so
        // no save can be mid-write here; any leftover temp file is stale and is
        // removed eagerly instead of accumulating forever.
        files.filter { TEMP_FILE_NAME_REGEX.matches(it.name) }.forEach { it.delete() }

        return files.mapNotNull { it.toStored() }
            .sortedBy { it.timestampMillis }
    }

    private fun File.toStored(): StoredRecovery? {
        val match = FILE_NAME_REGEX.matchEntire(name) ?: return null
        val timestamp = match.groupValues[1].toLongOrNull() ?: return null
        return StoredRecovery(
            file = this,
            timestampMillis = timestamp,
            sha256 = match.groupValues[2]
        )
    }

    private fun List<StoredRecovery>.deterministicLatest(): StoredRecovery? =
        maxWithOrNull(
            compareBy<StoredRecovery> { it.timestampMillis }.thenBy { it.sha256 }
        )

    private fun StoredRecovery.metadata(): RecoveryBackup = RecoveryBackup(
        timestampMillis = timestampMillis,
        sha256 = sha256,
        sizeBytes = file.length()
    )

    private data class StoredRecovery(
        val file: File,
        val timestampMillis: Long,
        val sha256: String
    )

    private fun Long.plusSaturated(): Long = if (this == Long.MAX_VALUE) this else this + 1

    private companion object {
        const val DEFAULT_MAX_RETAINED = 3
        const val BACKUP_DIR_NAME = "recovery_backups"
        const val TEMP_SUFFIX = ".tmp"
        const val FINAL_SUFFIX = ".json"
        val FILE_NAME_REGEX = Regex("^recovery-(\\d+)-([0-9a-f]{64})\\.json$")
        val TEMP_FILE_NAME_REGEX = Regex("^recovery-\\d+-[0-9a-f]{64}\\.tmp$")

        fun fileName(timestamp: Long, sha256: String, suffix: String): String =
            "recovery-$timestamp-$sha256$suffix"
    }
}
