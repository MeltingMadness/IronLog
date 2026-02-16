package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun updateUnitSystem(unitSystem: UnitSystem)
    suspend fun updateWeekStart(weekStart: WeekStart)
    suspend fun updateDefaultWarmupFlag(enabled: Boolean)
    suspend fun updateTimerKeepScreenOn(enabled: Boolean)
    suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean)
    suspend fun updateReminderConfig(config: ReminderConfig)
}
