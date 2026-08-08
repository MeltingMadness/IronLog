package com.ironlog.app.presentation.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.ironlog.app.data.backup.BackupHashMismatchException
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IncidentReport
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.ThemeScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.BackupImportPreview
import com.ironlog.app.domain.repository.BackupRepository
import com.ironlog.app.domain.repository.IncidentReportRepository
import com.ironlog.app.domain.repository.RecoveryBackup
import com.ironlog.app.domain.repository.ReminderScheduler
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.shared.settings.SettingsPreferencesController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val isBusy: Boolean = false,
    val showResetDialog: Boolean = false,
    val importConfirmationVisible: Boolean = false,
    val importPreview: BackupImportPreview? = null,
    val recoveryBackup: RecoveryBackup? = null,
    val showRecoveryRestoreDialog: Boolean = false,
    val buildInfo: BuildInfo = BuildInfo("", 0)
)

sealed interface SettingsEvent {
    data class Message(val textRes: Int, val args: List<Any> = emptyList()) : SettingsEvent
    data class ShareIncident(val report: IncidentReport) : SettingsEvent
}

class SettingsViewModel(
    private val appPreferencesRepository: AppPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val reminderScheduler: ReminderScheduler,
    private val incidentReportRepository: IncidentReportRepository,
    private val buildInfo: BuildInfo
) : ViewModel() {
    private val preferencesController = SettingsPreferencesController(
        scope = viewModelScope,
        appPreferencesRepository = AndroidSharedAppPreferencesRepository(appPreferencesRepository),
        reminderScheduler = AndroidSharedReminderScheduler(reminderScheduler)
    )

    private val isBusy = MutableStateFlow(false)
    private val showResetDialog = MutableStateFlow(false)
    private val importConfirmationVisible = MutableStateFlow(false)
    private val importPreview = MutableStateFlow<BackupImportPreview?>(null)
    private val recoveryBackup = MutableStateFlow<RecoveryBackup?>(null)
    private val showRecoveryRestoreDialog = MutableStateFlow(false)
    private val _events = MutableSharedFlow<SettingsEvent>()
    private var selectedImportUri: Uri? = null

    init {
        viewModelScope.launch { refreshRecoveryAvailability() }
    }

    val events = _events.asSharedFlow()

    private val baseState = combine(
        preferencesController.state,
        isBusy,
        showResetDialog,
        importConfirmationVisible,
        importPreview
    ) { preferencesState, busy, resetDialog, importVisible, preview ->
        SettingsUiState(
            preferences = preferencesState.preferences.toApp(),
            isBusy = busy,
            showResetDialog = resetDialog,
            importConfirmationVisible = importVisible,
            importPreview = preview,
            buildInfo = buildInfo
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        baseState,
        recoveryBackup,
        showRecoveryRestoreDialog
    ) { base, recovery, restoreDialog ->
        base.copy(
            recoveryBackup = recovery,
            showRecoveryRestoreDialog = restoreDialog
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun updateUnitSystem(unitSystem: UnitSystem) {
        preferencesController.updateUnitSystem(unitSystem.toShared())
    }

    fun updateWeekStart(weekStart: WeekStart) {
        preferencesController.updateWeekStart(weekStart.toShared())
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        preferencesController.updateThemeMode(themeMode.toShared())
    }

    fun updateThemeScheme(themeScheme: ThemeScheme) {
        preferencesController.updateThemeScheme(themeScheme.toShared())
    }

    fun updateUseDynamicColor(enabled: Boolean) {
        preferencesController.updateUseDynamicColor(enabled)
    }

    fun updateReducedMotion(enabled: Boolean) {
        preferencesController.updateReducedMotion(enabled)
    }

    fun updateDefaultWarmupFlag(enabled: Boolean) {
        preferencesController.updateDefaultWarmupFlag(enabled)
    }

    fun updateTimerKeepScreenOn(enabled: Boolean) {
        preferencesController.updateTimerKeepScreenOn(enabled)
    }

    fun updateBetaDiagnosticsOptIn(enabled: Boolean) {
        preferencesController.updateBetaDiagnosticsOptIn(enabled)
    }

    fun updateIntensitySystem(system: IntensitySystem) {
        preferencesController.updateIntensitySystem(system.toShared())
    }

    fun updateShareWeightHistoryAcrossContexts(enabled: Boolean) {
        preferencesController.updateShareWeightHistoryAcrossContexts(enabled)
    }

    fun updateReminderConfig(config: ReminderConfig) {
        preferencesController.updateReminderConfig(config.toShared())
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runBusyAction(
                action = { backupRepository.exportBackup(uri) },
                successMessageRes = com.ironlog.core.designsystem.R.string.settings_msg_backup_exported
            )
        }
    }

    fun onImportUriPicked(uri: Uri) {
        if (isBusy.value) return
        viewModelScope.launch {
            if (isBusy.value) return@launch
            runBusy { backupRepository.previewImport(uri) }
                .onSuccess { preview ->
                    // Fail closed again: if another action claimed busy or a selection
                    // was bound while this preview was in flight, do not overwrite it.
                    if (isBusy.value || selectedImportUri != null) return@onSuccess
                    selectedImportUri = uri
                    importPreview.value = preview
                    importConfirmationVisible.value = true
                }
                .onFailure { error ->
                    clearImportSelection()
                    emitError(error)
                }
        }
    }

    fun cancelImport() {
        clearImportSelection()
    }

    fun confirmImport() {
        val preview = importPreview.value
        val uri = selectedImportUri
        if (isBusy.value || uri == null || preview == null || !preview.isValid) return

        viewModelScope.launch {
            runBusy { backupRepository.importBackup(uri, preview.sha256) }
                .onSuccess {
                    clearImportSelection()
                    _events.emit(
                        SettingsEvent.Message(
                            com.ironlog.core.designsystem.R.string.settings_msg_backup_imported
                        )
                    )
                    syncReminderAndWarn()
                    refreshRecoveryAvailability()
                }
                .onFailure { error ->
                    if (error is BackupHashMismatchException) {
                        clearImportSelection()
                        _events.emit(
                            SettingsEvent.Message(
                                com.ironlog.core.designsystem.R.string.settings_msg_backup_source_changed
                            )
                        )
                    } else {
                        // Keep the preview and URI so the same document stays retryable.
                        emitError(error)
                    }
                }
        }
    }

    fun showRecoveryRestoreDialog() {
        showRecoveryRestoreDialog.value = true
    }

    fun dismissRecoveryRestoreDialog() {
        showRecoveryRestoreDialog.value = false
    }

    fun restoreLatestRecovery() {
        if (isBusy.value || recoveryBackup.value == null) return

        viewModelScope.launch {
            showRecoveryRestoreDialog.value = false
            runBusy { backupRepository.restoreLatestRecovery() }
                .onSuccess {
                    _events.emit(
                        SettingsEvent.Message(
                            com.ironlog.core.designsystem.R.string.settings_msg_recovery_restored
                        )
                    )
                    syncReminderAndWarn()
                    refreshRecoveryAvailability()
                }
                .onFailure { error -> emitError(error) }
        }
    }

    fun showResetDialog() {
        showResetDialog.value = true
    }

    fun dismissResetDialog() {
        showResetDialog.value = false
    }

    fun resetUserData() {
        viewModelScope.launch {
            showResetDialog.value = false
            runBusyAction(
                action = { backupRepository.resetUserData() },
                successMessageRes = com.ironlog.core.designsystem.R.string.settings_msg_data_reset
            )
        }
    }

    fun createIncidentReport(summary: String, details: String, currentScreen: String) {
        viewModelScope.launch {
            runBusyAction(
                action = {
                    val report = incidentReportRepository.createIncidentReport(
                        summary = summary,
                        details = details,
                        currentScreen = currentScreen,
                        includeDiagnostics = uiState.value.preferences.betaDiagnosticsOptIn
                    )
                    _events.emit(SettingsEvent.ShareIncident(report))
                },
                successMessageRes = com.ironlog.core.designsystem.R.string.settings_msg_incident_created
            )
        }
    }

    fun onNotificationPermissionDenied() {
        viewModelScope.launch {
            _events.emit(SettingsEvent.Message(com.ironlog.core.designsystem.R.string.settings_msg_notification_denied))
        }
    }

    private suspend fun runBusyAction(
        action: suspend () -> Unit,
        @StringRes successMessageRes: Int
    ) {
        runBusy { action() }
            .onSuccess {
                _events.emit(SettingsEvent.Message(successMessageRes))
            }
            .onFailure { error -> emitError(error) }
    }

    private suspend fun <T> runBusy(block: suspend () -> T): Result<T> {
        isBusy.value = true
        val result = runCatching { block() }
        isBusy.value = false
        return result
    }

    private fun clearImportSelection() {
        selectedImportUri = null
        importPreview.value = null
        importConfirmationVisible.value = false
    }

    private suspend fun emitError(error: Throwable) {
        _events.emit(
            SettingsEvent.Message(
                com.ironlog.core.designsystem.R.string.common_error_action_failed,
                listOf(error.message ?: "unbekannter Fehler")
            )
        )
    }

    private suspend fun syncReminderAndWarn() {
        runCatching { reminderScheduler.sync(uiState.value.preferences.reminderConfig) }
            .onFailure {
                _events.emit(
                    SettingsEvent.Message(
                        com.ironlog.core.designsystem.R.string.settings_msg_reminder_sync_failed
                    )
                )
            }
    }

    private suspend fun refreshRecoveryAvailability() {
        runCatching { backupRepository.latestRecovery() }
            .onSuccess { recoveryBackup.value = it }
            .onFailure {
                Log.w(TAG, "Could not refresh recovery backup availability", it)
            }
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
