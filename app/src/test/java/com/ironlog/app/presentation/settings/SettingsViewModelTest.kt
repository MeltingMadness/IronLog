package com.ironlog.app.presentation.settings

import android.net.Uri
import com.ironlog.app.domain.model.IncidentReport
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.repository.BackupRepository
import com.ironlog.app.domain.repository.IncidentReportRepository
import com.ironlog.app.domain.repository.ReminderScheduler
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var preferencesRepository: FakeAppPreferencesRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferencesRepository = FakeAppPreferencesRepository()
        viewModel = SettingsViewModel(
            appPreferencesRepository = preferencesRepository,
            backupRepository = NoopBackupRepository(),
            reminderScheduler = NoopReminderScheduler(),
            incidentReportRepository = NoopIncidentReportRepository(),
            buildInfo = BuildInfo("1.0-test", 1)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateThemeMode persists selected mode`() = runTest {
        viewModel.updateThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, preferencesRepository.current.themeMode)
    }

    @Test
    fun `updateUseDynamicColor persists flag`() = runTest {
        viewModel.updateUseDynamicColor(true)
        advanceUntilIdle()

        assertEquals(true, preferencesRepository.current.useDynamicColor)
    }

    @Test
    fun `updateReducedMotion persists flag`() = runTest {
        viewModel.updateReducedMotion(true)
        advanceUntilIdle()

        assertEquals(true, preferencesRepository.current.reducedMotion)
    }

    @Test
    fun `default reduced motion remains disabled`() = runTest {
        advanceUntilIdle()
        assertFalse(preferencesRepository.current.reducedMotion)
    }
}

private class NoopBackupRepository : BackupRepository {
    override suspend fun exportBackup(uri: Uri) = Unit
    override suspend fun importBackup(uri: Uri) = Unit
    override suspend fun resetUserData() = Unit
}

private class NoopReminderScheduler : ReminderScheduler {
    override suspend fun sync(config: com.ironlog.app.domain.model.ReminderConfig) = Unit
    override suspend fun cancel() = Unit
}

private class NoopIncidentReportRepository : IncidentReportRepository {
    override suspend fun createIncidentReport(
        summary: String,
        details: String,
        currentScreen: String,
        includeDiagnostics: Boolean,
        throwable: Throwable?
    ): IncidentReport {
        return IncidentReport(
            id = "test",
            createdAtEpochMillis = 0,
            fileName = "incident.json",
            uri = Uri.parse("file://incident.json")
        )
    }
}
