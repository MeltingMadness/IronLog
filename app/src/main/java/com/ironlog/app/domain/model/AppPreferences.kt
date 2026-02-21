package com.ironlog.app.domain.model

import java.time.DayOfWeek

enum class UnitSystem {
    METRIC,
    IMPERIAL
}

enum class WeekStart {
    MONDAY,
    SUNDAY
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class ReminderConfig(
    val enabled: Boolean = false,
    val hour: Int = 19,
    val minute: Int = 0,
    val daysOfWeek: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.FRIDAY
    )
)

data class AppPreferences(
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val reducedMotion: Boolean = false,
    val defaultWarmupFlag: Boolean = false,
    val timerKeepScreenOn: Boolean = false,
    val betaDiagnosticsOptIn: Boolean = false,
    val reminderConfig: ReminderConfig = ReminderConfig()
)
