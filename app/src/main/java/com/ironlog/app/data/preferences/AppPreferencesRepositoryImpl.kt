package com.ironlog.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferencesRepositoryImpl(
    private val context: Context
) : AppPreferencesRepository {

    override val preferences: Flow<AppPreferences> = context.appPreferencesDataStore.data.map { prefs ->
        val unitSystem = prefs[AppPreferenceKeys.UNIT_SYSTEM]
            ?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
            ?: UnitSystem.METRIC

        val weekStart = prefs[AppPreferenceKeys.WEEK_START]
            ?.let { runCatching { WeekStart.valueOf(it) }.getOrNull() }
            ?: WeekStart.MONDAY

        val reminderConfig = ReminderConfig(
            enabled = prefs[AppPreferenceKeys.REMINDER_ENABLED] ?: false,
            hour = (prefs[AppPreferenceKeys.REMINDER_HOUR] ?: ReminderConfig().hour).coerceIn(0, 23),
            minute = (prefs[AppPreferenceKeys.REMINDER_MINUTE] ?: ReminderConfig().minute).coerceIn(0, 59),
            daysOfWeek = parseReminderDays(prefs.stringOrNull(AppPreferenceKeys.REMINDER_DAYS))
        )

        AppPreferences(
            unitSystem = unitSystem,
            weekStart = weekStart,
            defaultWarmupFlag = prefs[AppPreferenceKeys.DEFAULT_WARMUP_FLAG] ?: false,
            timerKeepScreenOn = prefs[AppPreferenceKeys.TIMER_KEEP_SCREEN_ON] ?: false,
            betaDiagnosticsOptIn = prefs[AppPreferenceKeys.BETA_DIAGNOSTICS_OPT_IN] ?: false,
            reminderConfig = reminderConfig
        )
    }

    override suspend fun updateUnitSystem(unitSystem: UnitSystem) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferenceKeys.UNIT_SYSTEM] = unitSystem.name
        }
    }

    override suspend fun updateWeekStart(weekStart: WeekStart) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferenceKeys.WEEK_START] = weekStart.name
        }
    }

    override suspend fun updateDefaultWarmupFlag(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferenceKeys.DEFAULT_WARMUP_FLAG] = enabled
        }
    }

    override suspend fun updateTimerKeepScreenOn(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferenceKeys.TIMER_KEEP_SCREEN_ON] = enabled
        }
    }

    override suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferenceKeys.BETA_DIAGNOSTICS_OPT_IN] = enabled
        }
    }

    override suspend fun updateReminderConfig(config: ReminderConfig) {
        context.appPreferencesDataStore.edit { prefs ->
            prefs[AppPreferenceKeys.REMINDER_ENABLED] = config.enabled
            prefs[AppPreferenceKeys.REMINDER_HOUR] = config.hour.coerceIn(0, 23)
            prefs[AppPreferenceKeys.REMINDER_MINUTE] = config.minute.coerceIn(0, 59)
            prefs[AppPreferenceKeys.REMINDER_DAYS] = encodeReminderDays(config.daysOfWeek)
        }
    }
}
