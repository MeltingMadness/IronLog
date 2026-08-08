package com.ironlog.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class UnitSystem {
    METRIC,
    IMPERIAL
}

@Serializable
enum class Weekday {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

@Serializable
enum class WeekStart {
    MONDAY,
    SUNDAY
}

@Serializable
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
enum class ThemeScheme {
    AMBER,
    DEEP_CYAN,
    NEON_RED
}

@Serializable
enum class IntensitySystem {
    OFF,
    RPE,
    RIR
}

@Serializable
data class ReminderConfig(
    val enabled: Boolean = false,
    val hour: Int = 19,
    val minute: Int = 0,
    val daysOfWeek: Set<Weekday> = setOf(
        Weekday.MONDAY,
        Weekday.WEDNESDAY,
        Weekday.FRIDAY
    )
)

@Serializable
data class AppPreferences(
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val weekStart: WeekStart = WeekStart.MONDAY,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val themeScheme: ThemeScheme = ThemeScheme.AMBER,
    val useDynamicColor: Boolean = false,
    val reducedMotion: Boolean = false,
    val defaultWarmupFlag: Boolean = false,
    val timerKeepScreenOn: Boolean = false,
    val betaDiagnosticsOptIn: Boolean = false,
    val reminderConfig: ReminderConfig = ReminderConfig(),
    val intensitySystem: IntensitySystem = IntensitySystem.RPE,
    val shareWeightHistoryAcrossContexts: Boolean = false
)

@Serializable
data class BuildInfo(
    val versionName: String,
    val versionCode: Int
)
