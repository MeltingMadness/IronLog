package com.ironlog.app.presentation.settings

import android.net.Uri
import com.ironlog.app.data.backup.BackupHashMismatchException
import com.ironlog.app.domain.model.IncidentReport
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.repository.BackupRepository
import com.ironlog.app.domain.repository.BackupContentCounts
import com.ironlog.app.domain.repository.BackupImportPreview
import com.ironlog.app.domain.repository.IncidentReportRepository
import com.ironlog.app.domain.repository.RecoveryBackup
import com.ironlog.app.domain.repository.ReminderScheduler
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.app.fakes.FakeAppPreferencesRepository
import com.ironlog.core.designsystem.R
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var preferencesRepository: FakeAppPreferencesRepository
    private lateinit var backupRepository: FakeBackupRepository
    private lateinit var reminderScheduler: FakeReminderScheduler
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferencesRepository = FakeAppPreferencesRepository()
        backupRepository = FakeBackupRepository()
        reminderScheduler = FakeReminderScheduler()
        viewModel = createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        appPreferencesRepository = preferencesRepository,
        backupRepository = backupRepository,
        reminderScheduler = reminderScheduler,
        incidentReportRepository = NoopIncidentReportRepository(),
        buildInfo = BuildInfo("1.0-test", 1)
    )

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

    @Test
    fun `import selection previews without importing and shows confirmation`() = runTest {
        startUiStateCollector(backgroundScope)
        val uri = mockk<Uri>()

        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()

        assertEquals(listOf(uri), backupRepository.previewedUris)
        assertTrue(backupRepository.importedCalls.isEmpty())
        assertTrue(viewModel.uiState.value.importConfirmationVisible)
        assertEquals("abc123", viewModel.uiState.value.importPreview?.sha256)
        assertFalse(viewModel.uiState.value.isBusy)
    }

    @Test
    fun `cancel import clears selection without mutation`() = runTest {
        startUiStateCollector(backgroundScope)
        val uri = mockk<Uri>()
        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()

        viewModel.cancelImport()
        advanceUntilIdle()

        assertTrue(backupRepository.importedCalls.isEmpty())
        assertFalse(viewModel.uiState.value.importConfirmationVisible)
        assertNull(viewModel.uiState.value.importPreview)

        viewModel.confirmImport()
        advanceUntilIdle()
        assertTrue(backupRepository.importedCalls.isEmpty())
    }

    @Test
    fun `second import pick while preview busy is ignored and first selection stays bound`() = runTest {
        startUiStateCollector(backgroundScope)
        val messages = startEventCollector(backgroundScope)
        val first = mockk<Uri>()
        val second = mockk<Uri>()
        val gate = CompletableDeferred<Unit>()
        backupRepository.previewGate = gate
        backupRepository.previewResultOverride = validPreview(sha256 = "first-hash")

        viewModel.onImportUriPicked(first)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isBusy)

        viewModel.onImportUriPicked(second)
        advanceUntilIdle()
        assertEquals(listOf(first), backupRepository.previewedUris)
        assertNull(viewModel.uiState.value.importPreview)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(first), backupRepository.previewedUris)
        assertTrue(viewModel.uiState.value.importConfirmationVisible)
        assertEquals("first-hash", viewModel.uiState.value.importPreview?.sha256)
        assertFalse(viewModel.uiState.value.isBusy)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `valid import confirmation calls importBackup once with preview hash`() = runTest {
        startUiStateCollector(backgroundScope)
        val messages = startEventCollector(backgroundScope)
        val uri = mockk<Uri>()
        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()

        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(listOf(uri to "abc123"), backupRepository.importedCalls)
        assertFalse(viewModel.uiState.value.importConfirmationVisible)
        assertNull(viewModel.uiState.value.importPreview)
        assertEquals(1, reminderScheduler.syncCalls)
        assertTrue(messages.contains(R.string.settings_msg_backup_imported))
        assertFalse(messages.contains(R.string.common_error_action_failed))
    }

    @Test
    fun `invalid preview cannot confirm import`() = runTest {
        startUiStateCollector(backgroundScope)
        backupRepository.previewResult = validPreview(
            validationErrors = listOf("Backup contains no exercises")
        )
        val uri = mockk<Uri>()

        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertTrue(backupRepository.importedCalls.isEmpty())
        assertTrue(viewModel.uiState.value.importConfirmationVisible)
        assertEquals(
            listOf("Backup contains no exercises"),
            viewModel.uiState.value.importPreview?.validationErrors
        )
    }

    @Test
    fun `import failure keeps confirmation retryable and recovery availability`() = runTest {
        val recovery = RecoveryBackup(timestampMillis = 1L, sha256 = "recovery", sizeBytes = 10)
        backupRepository.latestRecoveryResult = recovery
        viewModel = createViewModel()
        startUiStateCollector(backgroundScope)
        val messages = startEventCollector(backgroundScope)
        backupRepository.importError = IOException("disk full")
        val uri = mockk<Uri>()

        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(1, backupRepository.importedCalls.size)
        assertTrue(viewModel.uiState.value.importConfirmationVisible)
        assertNotNull(viewModel.uiState.value.importPreview)
        assertEquals(recovery, viewModel.uiState.value.recoveryBackup)
        assertTrue(messages.contains(R.string.common_error_action_failed))

        backupRepository.importError = null
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(2, backupRepository.importedCalls.size)
        assertEquals(uri to "abc123", backupRepository.importedCalls.last())
        assertFalse(viewModel.uiState.value.importConfirmationVisible)
        assertTrue(messages.contains(R.string.settings_msg_backup_imported))
    }

    @Test
    fun `hash mismatch closes confirmation and requires fresh preview before retry`() = runTest {
        startUiStateCollector(backgroundScope)
        val messages = startEventCollector(backgroundScope)
        val uri = mockk<Uri>()
        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()

        backupRepository.importError = BackupHashMismatchException("abc123", "def456")
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(1, backupRepository.importedCalls.size)
        assertEquals(uri to "abc123", backupRepository.importedCalls.single())
        assertFalse(viewModel.uiState.value.importConfirmationVisible)
        assertNull(viewModel.uiState.value.importPreview)
        assertTrue(messages.contains(R.string.settings_msg_backup_source_changed))
        assertFalse(messages.contains(R.string.common_error_action_failed))

        backupRepository.importError = null
        viewModel.confirmImport()
        advanceUntilIdle()
        assertEquals(1, backupRepository.importedCalls.size)

        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()
        viewModel.confirmImport()
        advanceUntilIdle()
        assertEquals(2, backupRepository.importedCalls.size)
        assertTrue(messages.contains(R.string.settings_msg_backup_imported))
    }

    @Test
    fun `recovery availability loads on init and restore requires confirmation`() = runTest {
        val recovery = RecoveryBackup(
            timestampMillis = 1_700_000_000_000L,
            sha256 = "recovery",
            sizeBytes = 42
        )
        backupRepository.latestRecoveryResult = recovery
        viewModel = createViewModel()
        startUiStateCollector(backgroundScope)
        val messages = startEventCollector(backgroundScope)
        backupRepository.restoreResult = recovery
        advanceUntilIdle()

        assertEquals(recovery, viewModel.uiState.value.recoveryBackup)

        viewModel.showRecoveryRestoreDialog()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showRecoveryRestoreDialog)

        viewModel.dismissRecoveryRestoreDialog()
        advanceUntilIdle()
        assertEquals(0, backupRepository.restoreCalls)

        viewModel.showRecoveryRestoreDialog()
        advanceUntilIdle()
        viewModel.restoreLatestRecovery()
        advanceUntilIdle()

        assertEquals(1, backupRepository.restoreCalls)
        assertEquals(1, reminderScheduler.syncCalls)
        assertEquals(recovery, viewModel.uiState.value.recoveryBackup)
        assertFalse(viewModel.uiState.value.showRecoveryRestoreDialog)
        assertTrue(messages.contains(R.string.settings_msg_recovery_restored))
    }

    @Test
    fun `reminder sync failure after import keeps success message distinct from failure`() = runTest {
        startUiStateCollector(backgroundScope)
        val messages = startEventCollector(backgroundScope)
        reminderScheduler.failSync = true
        val uri = mockk<Uri>()

        viewModel.onImportUriPicked(uri)
        advanceUntilIdle()
        viewModel.confirmImport()
        advanceUntilIdle()

        assertEquals(1, backupRepository.importedCalls.size)
        assertTrue(messages.contains(R.string.settings_msg_backup_imported))
        assertTrue(messages.contains(R.string.settings_msg_reminder_sync_failed))
        assertFalse(messages.contains(R.string.common_error_action_failed))
    }

    private fun startUiStateCollector(scope: CoroutineScope) {
        scope.launch { viewModel.uiState.collect { } }
    }

    private fun startEventCollector(scope: CoroutineScope): MutableList<Int> {
        val messages = mutableListOf<Int>()
        scope.launch(UnconfinedTestDispatcher(dispatcher.scheduler)) {
            viewModel.events.collect { event ->
                if (event is SettingsEvent.Message) messages += event.textRes
            }
        }
        return messages
    }
}

private class FakeBackupRepository : BackupRepository {
    val previewedUris = mutableListOf<Uri>()
    val importedCalls = mutableListOf<Pair<Uri, String>>()
    val exportedUris = mutableListOf<Uri>()
    var resetCalls = 0
    var latestRecoveryCalls = 0
    var restoreCalls = 0

    var previewResult = validPreview()
    var previewError: Throwable? = null
    var previewGate: CompletableDeferred<Unit>? = null
    var previewResultOverride: BackupImportPreview? = null
    var importError: Throwable? = null
    var latestRecoveryResult: RecoveryBackup? = null
    var latestRecoveryError: Throwable? = null
    var restoreResult: RecoveryBackup? = null
    var restoreError: Throwable? = null

    override suspend fun exportBackup(uri: Uri) {
        exportedUris += uri
    }

    override suspend fun previewImport(uri: Uri): BackupImportPreview {
        previewedUris += uri
        previewError?.let { throw it }
        previewGate?.await()
        return previewResultOverride ?: previewResult
    }

    override suspend fun importBackup(uri: Uri, expectedSha256: String) {
        importedCalls += uri to expectedSha256
        importError?.let { throw it }
    }

    override suspend fun latestRecovery(): RecoveryBackup? {
        latestRecoveryCalls += 1
        latestRecoveryError?.let { throw it }
        return latestRecoveryResult
    }

    override suspend fun restoreLatestRecovery(): RecoveryBackup? {
        restoreCalls += 1
        restoreError?.let { throw it }
        return restoreResult ?: latestRecoveryResult
    }

    override suspend fun resetUserData() {
        resetCalls += 1
    }
}

private fun validPreview(
    sha256: String = "abc123",
    validationErrors: List<String> = emptyList()
): BackupImportPreview = BackupImportPreview(
    sha256 = sha256,
    schemaVersion = 9,
    appVersion = "1.2.3",
    exportedAtEpochMillis = 1_700_000_000_000L,
    counts = BackupContentCounts(
        exercises = 1,
        workoutSessions = 2,
        workoutSets = 3,
        trainingPlans = 4,
        planExercises = 5,
        personalRecords = 6,
        metaTrainingPlans = 7,
        metaPlanItems = 8
    ),
    validationErrors = validationErrors
)

private class FakeReminderScheduler : ReminderScheduler {
    var syncCalls = 0
    var failSync = false

    override suspend fun sync(config: com.ironlog.app.domain.model.ReminderConfig) {
        syncCalls += 1
        if (failSync) throw IOException("scheduler unavailable")
    }

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
            uri = mockk<Uri>()
        )
    }
}
