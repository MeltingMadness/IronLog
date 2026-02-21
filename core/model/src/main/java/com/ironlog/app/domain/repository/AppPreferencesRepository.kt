package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun updateUnitSystem(unitSystem: UnitSystem)
    suspend fun updateWeekStart(weekStart: WeekStart)
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateUseDynamicColor(enabled: Boolean)
    suspend fun updateReducedMotion(enabled: Boolean)
    suspend fun updateDefaultWarmupFlag(enabled: Boolean)
    suspend fun updateTimerKeepScreenOn(enabled: Boolean)
    suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean)
    suspend fun updateReminderConfig(config: ReminderConfig)
}
