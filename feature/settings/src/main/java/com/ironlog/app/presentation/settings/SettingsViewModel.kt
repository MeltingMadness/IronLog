package com.ironlog.app.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IncidentReport
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.ThemeScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.BackupRepository
import com.ironlog.app.domain.repository.IncidentReportRepository
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
    private val _events = MutableSharedFlow<SettingsEvent>()

    val events = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesController.state,
        isBusy,
        showResetDialog
    ) { preferencesState, busy, resetDialog ->
        SettingsUiState(
            preferences = preferencesState.preferences.toApp(),
            isBusy = busy,
            showResetDialog = resetDialog,
            buildInfo = buildInfo
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

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            runBusyAction(
                action = {
                    backupRepository.importBackup(uri)
                    reminderScheduler.sync(uiState.value.preferences.reminderConfig)
                },
                successMessageRes = com.ironlog.core.designsystem.R.string.settings_msg_backup_imported
            )
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
        isBusy.value = true
        val result = runCatching { action() }
        isBusy.value = false

        result
            .onSuccess {
                _events.emit(SettingsEvent.Message(successMessageRes))
            }
            .onFailure { error ->
                _events.emit(
                    SettingsEvent.Message(
                        com.ironlog.core.designsystem.R.string.common_error_action_failed,
                        listOf(error.message ?: "unbekannter Fehler")
                    )
                )
            }
    }
}
