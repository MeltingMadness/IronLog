package com.ironlog.app.data.preferences

import android.content.Context
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.UnitSystem
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.nio.file.Files
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesDataStoreTest {

    @Test
    fun `missing key falls back to default reminder days`() {
        val days = parseReminderDays(null)
        assertEquals(ReminderConfig().daysOfWeek, days)
    }

    @Test
    fun `explicitly empty selection stays empty instead of snapping back to default`() {
        val encoded = encodeReminderDays(emptySet())
        val decoded = parseReminderDays(encoded)

        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `legacy blank value keeps historical default decode`() {
        // Older builds encoded empty as "" but then decoded blank back to Mon/Wed/Fri.
        // Keep that decode so already-stored blank values do not suddenly become "no days".
        // New explicit empty selections use the "none" sentinel (covered above).
        val decoded = parseReminderDays("")
        assertEquals(ReminderConfig().daysOfWeek, decoded)
    }

    @Test
    fun `non-empty selection encodes and decodes round trip`() {
        val days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        val encoded = encodeReminderDays(days)
        val decoded = parseReminderDays(encoded)

        assertEquals(days, decoded)
    }

    @Test
    fun `invalid tokens are dropped without discarding valid days`() {
        val decoded = parseReminderDays("MONDAY,NOT_A_DAY,FRIDAY")
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), decoded)
    }

    @Test
    fun `share weight history across contexts defaults to false and persists enabled flag`() = runTest {
        val repository = AppPreferencesRepositoryImpl(createContextWithTempDataStore())

        assertFalse(repository.preferences.first().shareWeightHistoryAcrossContexts)
        repository.updateShareWeightHistoryAcrossContexts(true)

        assertTrue(repository.preferences.first().shareWeightHistoryAcrossContexts)
    }

    @Test
    fun `corrupted preferences file falls back to defaults instead of crashing`() = runTest {
        val context = createContextWithTempDataStore()
        val dataStoreDir = File(context.filesDir, "datastore")
        dataStoreDir.mkdirs()
        File(dataStoreDir, "app_preferences.preferences_pb").writeBytes(
            "definitely-not-a-valid-preferences-file".encodeToByteArray()
        )

        val repository = AppPreferencesRepositoryImpl(context)

        // A corrupt preferences file must never crash app start; the flow emits defaults.
        val prefs = repository.preferences.first()

        assertEquals(UnitSystem.METRIC, prefs.unitSystem)
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertFalse(prefs.reminderConfig.enabled)
    }

    private fun createContextWithTempDataStore(): Context {
        val dataStoreDir = Files.createTempDirectory("ironlog-datastore").toFile()
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns dataStoreDir
        return context
    }
}
