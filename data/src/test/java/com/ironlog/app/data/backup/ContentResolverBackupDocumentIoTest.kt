package com.ironlog.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ContentResolverBackupDocumentIoTest {

    @Test
    fun `write open failure propagates without deleting or truncating the URI`() =
        runBlocking {
            val resolver = mockk<ContentResolver>()
            val uri = mockk<Uri>()
            every { resolver.openOutputStream(uri) } returns null
            val io = ContentResolverBackupDocumentIo(resolver)

            assertThrows(IOException::class.java) {
                runBlocking { io.writeVerified(uri, ByteArray(10)) }
            }

            verify(exactly = 0) { resolver.delete(any(), any(), any()) }
            verify(exactly = 0) { resolver.openOutputStream(any(), any<String>()) }
        }

    @Test
    fun `readback hash mismatch propagates without deleting or truncating the URI`() =
        runBlocking {
            val resolver = mockk<ContentResolver>()
            val uri = mockk<Uri>()
            val output = ByteArrayOutputStream()
            every { resolver.openOutputStream(uri) } returns output
            every { resolver.openInputStream(uri) } returns ByteArrayInputStream(ByteArray(0))
            val io = ContentResolverBackupDocumentIo(resolver)

            assertThrows(IOException::class.java) {
                runBlocking { io.writeVerified(uri, "payload".encodeToByteArray()) }
            }

            assertEquals("payload".length, output.size())
            verify(exactly = 0) { resolver.delete(any(), any(), any()) }
            verify(exactly = 0) { resolver.openOutputStream(any(), any<String>()) }
        }
}
