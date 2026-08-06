package com.ironlog.app.data.backup

import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileRecoveryBackupStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val snapshotBytes = "snapshot-content".encodeToByteArray()
    private val snapshotHash = snapshotBytes.sha256Hex()

    @Test
    fun `same wall clock millisecond gets a unique monotonic timestamp and survives prune`() =
        runBlocking {
            val dir = tempFolder.root
            val legacyName = "recovery-1000-${"a".repeat(64)}.json"
            seed(dir, legacyName, "legacy")
            var clock = 1000L
            val store = FileRecoveryBackupStore(
                backupDir = dir,
                maxRetained = 1,
                nowMillis = { clock }
            )

            val saved = store.save(snapshotBytes)

            assertEquals(1001L, saved.timestampMillis)
            assertTrue(File(dir, "recovery-1001-$snapshotHash.json").exists())
            assertFalse(File(dir, legacyName).exists())
            assertEquals(1001L, store.latest()?.timestampMillis)
            assertEquals(snapshotHash, store.latest()?.sha256)
        }

    @Test
    fun `backward clock never overwrites or prunes the newest snapshot`() = runBlocking {
        val dir = tempFolder.root
        val existingName = "recovery-5000-${"b".repeat(64)}.json"
        seed(dir, existingName, "existing")
        var clock = 100L
        val store = FileRecoveryBackupStore(
            backupDir = dir,
            maxRetained = 2,
            nowMillis = { clock }
        )

        val saved = store.save(snapshotBytes)

        assertEquals(5001L, saved.timestampMillis)
        assertTrue(File(dir, "recovery-5001-$snapshotHash.json").exists())
        assertTrue(File(dir, existingName).exists())
        assertEquals(5001L, store.latest()?.timestampMillis)
    }

    @Test
    fun `retention keeps only the bounded newest snapshots and always the saved file`() =
        runBlocking {
            var clock = 100L
            val store = FileRecoveryBackupStore(
                backupDir = tempFolder.root,
                maxRetained = 2,
                nowMillis = { clock }
            )
            val first = store.save("first".encodeToByteArray())
            clock = 200L
            val second = store.save("second".encodeToByteArray())
            clock = 300L
            val third = store.save("third".encodeToByteArray())

            val files = tempFolder.root.listFiles()!!.filter { it.name.endsWith(".json") }
            assertEquals(2, files.size)
            assertTrue(
                "newest snapshot must survive retention",
                files.any { it.name == "recovery-300-${third.sha256}.json" }
            )
            assertFalse(
                "oldest snapshot must be pruned",
                files.any { it.name == "recovery-100-${first.sha256}.json" }
            )
            assertTrue(
                "second snapshot must remain",
                files.any { it.name == "recovery-200-${second.sha256}.json" }
            )
            assertEquals(300L, store.latest()?.timestampMillis)
        }

    @Test
    fun `latest resolves equal timestamps deterministically`() = runBlocking {
        val dir = tempFolder.root
        val lowerHash = "lower".encodeToByteArray().sha256Hex()
        val higherHash = "higher".encodeToByteArray().sha256Hex()
        val lower = "recovery-1000-$lowerHash.json"
        val higher = "recovery-1000-$higherHash.json"
        seed(dir, lower, "lower")
        seed(dir, higher, "higher")

        val store = FileRecoveryBackupStore(backupDir = dir)

        assertEquals(higherHash, store.latest()?.sha256)
    }

    @Test
    fun `latest verifies content hash and fails closed on a corrupted newest snapshot`(): Unit =
        runBlocking {
            val store = FileRecoveryBackupStore(backupDir = tempFolder.root)
            val saved = store.save(snapshotBytes)

            assertEquals(saved.sha256, store.latest()?.sha256)

            seed(
                tempFolder.root,
                "recovery-${saved.timestampMillis}-${saved.sha256}.json",
                "corrupted"
            )

            assertThrows(IOException::class.java) {
                runBlocking { store.latest() }
            }
        }

    @Test
    fun `loadLatestBytes verifies hash and rejects a corrupted snapshot`(): Unit = runBlocking {
        val store = FileRecoveryBackupStore(backupDir = tempFolder.root)
        val saved = store.save(snapshotBytes)

        assertTrue(
            snapshotBytes.contentEquals(store.loadLatestBytes() ?: ByteArray(0))
        )

        seed(
            tempFolder.root,
            "recovery-${saved.timestampMillis}-${saved.sha256}.json",
            "corrupted"
        )

        assertThrows(IOException::class.java) {
            runBlocking { store.loadLatestBytes() }
        }
    }

    private fun seed(dir: File, name: String, content: String) {
        dir.mkdirs()
        dir.resolve(name).writeBytes(content.encodeToByteArray())
    }
}
