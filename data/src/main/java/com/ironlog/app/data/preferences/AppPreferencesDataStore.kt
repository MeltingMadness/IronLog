package com.ironlog.app.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ironlog.app.domain.model.ReminderConfig
import java.time.DayOfWeek

val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")

internal object AppPreferenceKeys {
    val UNIT_SYSTEM = stringPreferencesKey("unit_system")
    val WEEK_START = stringPreferencesKey("week_start")
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val THEME_SCHEME = stringPreferencesKey("theme_scheme")
    val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
    val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    val DEFAULT_WARMUP_FLAG = booleanPreferencesKey("default_warmup_flag")
    val TIMER_KEEP_SCREEN_ON = booleanPreferencesKey("timer_keep_screen_on")
    val BETA_DIAGNOSTICS_OPT_IN = booleanPreferencesKey("beta_diagnostics_opt_in")
    val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val REMINDER_DAYS = stringPreferencesKey("reminder_days")
    val INTENSITY_SYSTEM = stringPreferencesKey("intensity_system")
}

// Sentinel stored when the user explicitly deselects every reminder day. This is required to
// distinguish "user chose no days" from "preference was never written" (raw == null), since both
// would otherwise be indistinguishable blank/empty strings and incorrectly snap back to the
// Mon/Wed/Fri default every time the app reloads preferences.
private const val REMINDER_DAYS_NONE_SENTINEL = "none"

internal fun parseReminderDays(raw: String?): Set<DayOfWeek> {
    if (raw == null) {
        // Key has never been written — fall back to the default for first-time users.
        return ReminderConfig().daysOfWeek
    }

    if (raw == REMINDER_DAYS_NONE_SENTINEL) {
        // Explicitly persisted empty selection — respect it instead of resetting to the default.
        return emptySet()
    }

    if (raw.isBlank()) {
        // Legacy encode used "" for empty, which historically decoded to the default.
        // Keep that behavior for already-stored blank values.
        return ReminderConfig().daysOfWeek
    }

    return raw.split(',')
        .mapNotNull { token ->
            runCatching { DayOfWeek.valueOf(token.trim()) }.getOrNull()
        }
        .toSet()
}

internal fun encodeReminderDays(days: Set<DayOfWeek>): String {
    if (days.isEmpty()) return REMINDER_DAYS_NONE_SENTINEL
    return days.sortedBy { it.value }.joinToString(",") { it.name }
}

internal fun Preferences.stringOrNull(key: Preferences.Key<String>): String? = this[key]
