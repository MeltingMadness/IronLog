package com.ironlog.app.presentation.settings

import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.ThemeScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ReminderScheduler
import com.ironlog.shared.model.Weekday
import com.ironlog.shared.settings.SharedAppPreferencesRepository
import com.ironlog.shared.settings.SharedReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek

internal class AndroidSharedAppPreferencesRepository(
    private val delegate: AppPreferencesRepository
) : SharedAppPreferencesRepository {
    override val preferences: Flow<com.ironlog.shared.model.AppPreferences> =
        delegate.preferences.map(AppPreferences::toShared)

    override suspend fun updateUnitSystem(unitSystem: com.ironlog.shared.model.UnitSystem) {
        delegate.updateUnitSystem(unitSystem.toApp())
    }

    override suspend fun updateWeekStart(weekStart: com.ironlog.shared.model.WeekStart) {
        delegate.updateWeekStart(weekStart.toApp())
    }

    override suspend fun updateThemeMode(themeMode: com.ironlog.shared.model.ThemeMode) {
        delegate.updateThemeMode(themeMode.toApp())
    }

    override suspend fun updateThemeScheme(themeScheme: com.ironlog.shared.model.ThemeScheme) {
        delegate.updateThemeScheme(themeScheme.toApp())
    }

    override suspend fun updateUseDynamicColor(enabled: Boolean) {
        delegate.updateUseDynamicColor(enabled)
    }

    override suspend fun updateReducedMotion(enabled: Boolean) {
        delegate.updateReducedMotion(enabled)
    }

    override suspend fun updateDefaultWarmupFlag(enabled: Boolean) {
        delegate.updateDefaultWarmupFlag(enabled)
    }

    override suspend fun updateTimerKeepScreenOn(enabled: Boolean) {
        delegate.updateTimerKeepScreenOn(enabled)
    }

    override suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean) {
        delegate.updateBetaDiagnosticsOptIn(enabled)
    }

    override suspend fun updateReminderConfig(config: com.ironlog.shared.model.ReminderConfig) {
        delegate.updateReminderConfig(config.toApp())
    }

    override suspend fun updateIntensitySystem(intensitySystem: com.ironlog.shared.model.IntensitySystem) {
        delegate.updateIntensitySystem(intensitySystem.toApp())
    }
}

internal class AndroidSharedReminderScheduler(
    private val delegate: ReminderScheduler
) : SharedReminderScheduler {
    override suspend fun sync(config: com.ironlog.shared.model.ReminderConfig) {
        delegate.sync(config.toApp())
    }

    override suspend fun cancel() {
        delegate.cancel()
    }
}

internal fun AppPreferences.toShared(): com.ironlog.shared.model.AppPreferences =
    com.ironlog.shared.model.AppPreferences(
        unitSystem = unitSystem.toShared(),
        weekStart = weekStart.toShared(),
        themeMode = themeMode.toShared(),
        themeScheme = themeScheme.toShared(),
        useDynamicColor = useDynamicColor,
        reducedMotion = reducedMotion,
        defaultWarmupFlag = defaultWarmupFlag,
        timerKeepScreenOn = timerKeepScreenOn,
        betaDiagnosticsOptIn = betaDiagnosticsOptIn,
        reminderConfig = reminderConfig.toShared(),
        intensitySystem = intensitySystem.toShared()
    )

internal fun com.ironlog.shared.model.AppPreferences.toApp(): AppPreferences =
    AppPreferences(
        unitSystem = unitSystem.toApp(),
        weekStart = weekStart.toApp(),
        themeMode = themeMode.toApp(),
        themeScheme = themeScheme.toApp(),
        useDynamicColor = useDynamicColor,
        reducedMotion = reducedMotion,
        defaultWarmupFlag = defaultWarmupFlag,
        timerKeepScreenOn = timerKeepScreenOn,
        betaDiagnosticsOptIn = betaDiagnosticsOptIn,
        reminderConfig = reminderConfig.toApp(),
        intensitySystem = intensitySystem.toApp()
    )

internal fun ReminderConfig.toShared(): com.ironlog.shared.model.ReminderConfig =
    com.ironlog.shared.model.ReminderConfig(
        enabled = enabled,
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek.map(DayOfWeek::toShared).toSet()
    )

internal fun com.ironlog.shared.model.ReminderConfig.toApp(): ReminderConfig =
    ReminderConfig(
        enabled = enabled,
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek.map(Weekday::toApp).toSet()
    )

internal fun UnitSystem.toShared(): com.ironlog.shared.model.UnitSystem =
    com.ironlog.shared.model.UnitSystem.valueOf(name)

internal fun com.ironlog.shared.model.UnitSystem.toApp(): UnitSystem = UnitSystem.valueOf(name)

internal fun WeekStart.toShared(): com.ironlog.shared.model.WeekStart =
    com.ironlog.shared.model.WeekStart.valueOf(name)

internal fun com.ironlog.shared.model.WeekStart.toApp(): WeekStart = WeekStart.valueOf(name)

internal fun ThemeMode.toShared(): com.ironlog.shared.model.ThemeMode =
    com.ironlog.shared.model.ThemeMode.valueOf(name)

internal fun com.ironlog.shared.model.ThemeMode.toApp(): ThemeMode = ThemeMode.valueOf(name)

internal fun ThemeScheme.toShared(): com.ironlog.shared.model.ThemeScheme =
    com.ironlog.shared.model.ThemeScheme.valueOf(name)

internal fun com.ironlog.shared.model.ThemeScheme.toApp(): ThemeScheme = ThemeScheme.valueOf(name)

internal fun IntensitySystem.toShared(): com.ironlog.shared.model.IntensitySystem =
    com.ironlog.shared.model.IntensitySystem.valueOf(name)

internal fun com.ironlog.shared.model.IntensitySystem.toApp(): IntensitySystem =
    IntensitySystem.valueOf(name)

private fun DayOfWeek.toShared(): Weekday = when (this) {
    DayOfWeek.MONDAY -> Weekday.MONDAY
    DayOfWeek.TUESDAY -> Weekday.TUESDAY
    DayOfWeek.WEDNESDAY -> Weekday.WEDNESDAY
    DayOfWeek.THURSDAY -> Weekday.THURSDAY
    DayOfWeek.FRIDAY -> Weekday.FRIDAY
    DayOfWeek.SATURDAY -> Weekday.SATURDAY
    DayOfWeek.SUNDAY -> Weekday.SUNDAY
}

private fun Weekday.toApp(): DayOfWeek = when (this) {
    Weekday.MONDAY -> DayOfWeek.MONDAY
    Weekday.TUESDAY -> DayOfWeek.TUESDAY
    Weekday.WEDNESDAY -> DayOfWeek.WEDNESDAY
    Weekday.THURSDAY -> DayOfWeek.THURSDAY
    Weekday.FRIDAY -> DayOfWeek.FRIDAY
    Weekday.SATURDAY -> DayOfWeek.SATURDAY
    Weekday.SUNDAY -> DayOfWeek.SUNDAY
}
