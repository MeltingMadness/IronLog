package com.ironlog.shared.settings

import com.ironlog.shared.model.AppPreferences
import com.ironlog.shared.model.IntensitySystem
import com.ironlog.shared.model.ReminderConfig
import com.ironlog.shared.model.ThemeMode
import com.ironlog.shared.model.ThemeScheme
import com.ironlog.shared.model.UnitSystem
import com.ironlog.shared.model.WeekStart
import com.ironlog.shared.model.Weekday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPreferencesControllerTest {

    @Test
    fun updateThemeMode_persistsSelectedMode() = runTest {
        val repository = FakeSharedAppPreferencesRepository()
        val reminderScheduler = FakeSharedReminderScheduler()
        val controller = SettingsPreferencesController(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            appPreferencesRepository = repository,
            reminderScheduler = reminderScheduler
        )

        controller.updateThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, repository.current.themeMode)
    }

    @Test
    fun updateReminderConfig_persistsAndSyncsReminderScheduler() = runTest {
        val repository = FakeSharedAppPreferencesRepository()
        val reminderScheduler = FakeSharedReminderScheduler()
        val controller = SettingsPreferencesController(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            appPreferencesRepository = repository,
            reminderScheduler = reminderScheduler
        )
        val config = ReminderConfig(enabled = true, daysOfWeek = setOf(Weekday.MONDAY, Weekday.FRIDAY))

        controller.updateReminderConfig(config)
        advanceUntilIdle()

        assertEquals(config, repository.current.reminderConfig)
        assertEquals(config, reminderScheduler.syncedConfig)
    }

    @Test
    fun updateShareWeightHistoryAcrossContexts_persistsFlag() = runTest {
        val repository = FakeSharedAppPreferencesRepository()
        val reminderScheduler = FakeSharedReminderScheduler()
        val controller = SettingsPreferencesController(
            scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
            appPreferencesRepository = repository,
            reminderScheduler = reminderScheduler
        )

        controller.updateShareWeightHistoryAcrossContexts(true)
        advanceUntilIdle()

        assertTrue(repository.current.shareWeightHistoryAcrossContexts)
    }
}

private class FakeSharedAppPreferencesRepository(
    initial: AppPreferences = AppPreferences()
) : SharedAppPreferencesRepository {
    private val state = MutableStateFlow(initial)

    val current: AppPreferences
        get() = state.value

    override val preferences: Flow<AppPreferences> = state.asStateFlow()

    override suspend fun updateUnitSystem(unitSystem: UnitSystem) {
        state.value = state.value.copy(unitSystem = unitSystem)
    }

    override suspend fun updateWeekStart(weekStart: WeekStart) {
        state.value = state.value.copy(weekStart = weekStart)
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
    }

    override suspend fun updateThemeScheme(themeScheme: ThemeScheme) {
        state.value = state.value.copy(themeScheme = themeScheme)
    }

    override suspend fun updateUseDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(useDynamicColor = enabled)
    }

    override suspend fun updateReducedMotion(enabled: Boolean) {
        state.value = state.value.copy(reducedMotion = enabled)
    }

    override suspend fun updateDefaultWarmupFlag(enabled: Boolean) {
        state.value = state.value.copy(defaultWarmupFlag = enabled)
    }

    override suspend fun updateTimerKeepScreenOn(enabled: Boolean) {
        state.value = state.value.copy(timerKeepScreenOn = enabled)
    }

    override suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean) {
        state.value = state.value.copy(betaDiagnosticsOptIn = enabled)
    }

    override suspend fun updateReminderConfig(config: ReminderConfig) {
        state.value = state.value.copy(reminderConfig = config)
    }

    override suspend fun updateIntensitySystem(intensitySystem: IntensitySystem) {
        state.value = state.value.copy(intensitySystem = intensitySystem)
    }

    override suspend fun updateShareWeightHistoryAcrossContexts(enabled: Boolean) {
        state.value = state.value.copy(shareWeightHistoryAcrossContexts = enabled)
    }
}

private class FakeSharedReminderScheduler : SharedReminderScheduler {
    var syncedConfig: ReminderConfig? = null
        private set

    override suspend fun sync(config: ReminderConfig) {
        syncedConfig = config
    }

    override suspend fun cancel() = Unit
}
