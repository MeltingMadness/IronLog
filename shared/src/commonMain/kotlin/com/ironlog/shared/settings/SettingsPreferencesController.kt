package com.ironlog.shared.settings

import com.ironlog.shared.model.AppPreferences
import com.ironlog.shared.model.IntensitySystem
import com.ironlog.shared.model.ReminderConfig
import com.ironlog.shared.model.ThemeMode
import com.ironlog.shared.model.ThemeScheme
import com.ironlog.shared.model.UnitSystem
import com.ironlog.shared.model.WeekStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

interface SharedAppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun updateUnitSystem(unitSystem: UnitSystem)
    suspend fun updateWeekStart(weekStart: WeekStart)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateThemeScheme(themeScheme: ThemeScheme)
    suspend fun updateUseDynamicColor(enabled: Boolean)
    suspend fun updateReducedMotion(enabled: Boolean)
    suspend fun updateDefaultWarmupFlag(enabled: Boolean)
    suspend fun updateTimerKeepScreenOn(enabled: Boolean)
    suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean)
    suspend fun updateReminderConfig(config: ReminderConfig)
    suspend fun updateIntensitySystem(intensitySystem: IntensitySystem)
    suspend fun updateShareWeightHistoryAcrossContexts(enabled: Boolean)
}

interface SharedReminderScheduler {
    suspend fun sync(config: ReminderConfig)
    suspend fun cancel()
}

data class SettingsPreferencesState(
    val preferences: AppPreferences = AppPreferences()
)

class SettingsPreferencesController(
    scope: CoroutineScope,
    private val appPreferencesRepository: SharedAppPreferencesRepository,
    private val reminderScheduler: SharedReminderScheduler
) {
    val state: StateFlow<SettingsPreferencesState> =
        appPreferencesRepository.preferences
            .map(::SettingsPreferencesState)
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsPreferencesState()
            )

    private val controllerScope = scope

    fun updateUnitSystem(unitSystem: UnitSystem) {
        controllerScope.launch { appPreferencesRepository.updateUnitSystem(unitSystem) }
    }

    fun updateWeekStart(weekStart: WeekStart) {
        controllerScope.launch { appPreferencesRepository.updateWeekStart(weekStart) }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        controllerScope.launch { appPreferencesRepository.updateThemeMode(themeMode) }
    }

    fun updateThemeScheme(themeScheme: ThemeScheme) {
        controllerScope.launch { appPreferencesRepository.updateThemeScheme(themeScheme) }
    }

    fun updateUseDynamicColor(enabled: Boolean) {
        controllerScope.launch { appPreferencesRepository.updateUseDynamicColor(enabled) }
    }

    fun updateReducedMotion(enabled: Boolean) {
        controllerScope.launch { appPreferencesRepository.updateReducedMotion(enabled) }
    }

    fun updateDefaultWarmupFlag(enabled: Boolean) {
        controllerScope.launch { appPreferencesRepository.updateDefaultWarmupFlag(enabled) }
    }

    fun updateTimerKeepScreenOn(enabled: Boolean) {
        controllerScope.launch { appPreferencesRepository.updateTimerKeepScreenOn(enabled) }
    }

    fun updateBetaDiagnosticsOptIn(enabled: Boolean) {
        controllerScope.launch { appPreferencesRepository.updateBetaDiagnosticsOptIn(enabled) }
    }

    fun updateReminderConfig(config: ReminderConfig) {
        controllerScope.launch {
            appPreferencesRepository.updateReminderConfig(config)
            // Scheduler implementations (e.g. iOS notification scheduling) can fail; a
            // reminder-sync error must never become an uncaught crash in the caller's scope.
            runCatching { reminderScheduler.sync(config) }
        }
    }

    fun updateIntensitySystem(intensitySystem: IntensitySystem) {
        controllerScope.launch { appPreferencesRepository.updateIntensitySystem(intensitySystem) }
    }

    fun updateShareWeightHistoryAcrossContexts(enabled: Boolean) {
        controllerScope.launch { appPreferencesRepository.updateShareWeightHistoryAcrossContexts(enabled) }
    }
}
