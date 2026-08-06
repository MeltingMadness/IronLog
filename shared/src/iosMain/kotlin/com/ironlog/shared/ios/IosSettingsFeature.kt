@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.ironlog.shared.ios

import com.ironlog.shared.incident.IncidentDiagnostics
import com.ironlog.shared.incident.IncidentReportPayload
import com.ironlog.shared.incident.IncidentReportSanitizer
import com.ironlog.shared.model.AppPreferences
import com.ironlog.shared.model.BackupBlob
import com.ironlog.shared.model.BuildInfo
import com.ironlog.shared.model.IncidentAttachment
import com.ironlog.shared.model.IntensitySystem
import com.ironlog.shared.model.ReminderConfig
import com.ironlog.shared.model.ThemeMode
import com.ironlog.shared.model.ThemeScheme
import com.ironlog.shared.model.UnitSystem
import com.ironlog.shared.model.WeekStart
import com.ironlog.shared.model.Weekday
import com.ironlog.shared.repository.SharedBackupRepository
import com.ironlog.shared.repository.SharedIncidentReportRepository
import com.ironlog.shared.settings.SettingsPreferencesController
import com.ironlog.shared.settings.SettingsPreferencesState
import com.ironlog.shared.settings.SharedAppPreferencesRepository
import com.ironlog.shared.settings.SharedReminderScheduler
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSBundle
import platform.Foundation.NSDateComponents
import platform.Foundation.NSUserDefaults
import platform.Foundation.setObject
import platform.UIKit.UIDevice
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class IosSettingsFeature {
    private val scope: CoroutineScope = MainScope()
    private val buildInfo = currentBuildInfo()
    private val preferencesRepository = IosAppPreferencesRepository()
    private val reminderScheduler = IosReminderScheduler()
    private val backupRepository = IosBackupRepository(buildInfo = buildInfo)
    private val incidentRepository = IosIncidentReportRepository(buildInfo = buildInfo)
    private val controller = SettingsPreferencesController(
        scope = scope,
        appPreferencesRepository = preferencesRepository,
        reminderScheduler = reminderScheduler,
    )

    fun currentState(): IosSettingsState = controller.state.value.toIosState(buildInfo)

    fun watchState(onState: (IosSettingsState) -> Unit): IosCloseable {
        val job = scope.launch {
            controller.state.collect { state ->
                onState(state.toIosState(buildInfo))
            }
        }
        return IosCloseable { job.cancel() }
    }

    fun unitSystemOptions(): List<String> = UnitSystem.entries.map { it.name }
    fun weekStartOptions(): List<String> = WeekStart.entries.map { it.name }
    fun themeModeOptions(): List<String> = ThemeMode.entries.map { it.name }
    fun themeSchemeOptions(): List<String> = ThemeScheme.entries.map { it.name }
    fun intensitySystemOptions(): List<String> = IntensitySystem.entries.map { it.name }
    fun weekdayOptions(): List<String> = Weekday.entries.map { it.name }

    fun updateUnitSystem(value: String) {
        controller.updateUnitSystem(UnitSystem.entries.firstOrNull { it.name == value } ?: UnitSystem.METRIC)
    }

    fun updateWeekStart(value: String) {
        controller.updateWeekStart(WeekStart.entries.firstOrNull { it.name == value } ?: WeekStart.MONDAY)
    }

    fun updateThemeMode(value: String) {
        controller.updateThemeMode(ThemeMode.entries.firstOrNull { it.name == value } ?: ThemeMode.SYSTEM)
    }

    fun updateThemeScheme(value: String) {
        controller.updateThemeScheme(ThemeScheme.entries.firstOrNull { it.name == value } ?: ThemeScheme.AMBER)
    }

    fun updateIntensitySystem(value: String) {
        controller.updateIntensitySystem(IntensitySystem.entries.firstOrNull { it.name == value } ?: IntensitySystem.RPE)
    }

    fun updateUseDynamicColor(enabled: Boolean) = controller.updateUseDynamicColor(enabled)
    fun updateReducedMotion(enabled: Boolean) = controller.updateReducedMotion(enabled)
    fun updateDefaultWarmupFlag(enabled: Boolean) = controller.updateDefaultWarmupFlag(enabled)
    fun updateTimerKeepScreenOn(enabled: Boolean) = controller.updateTimerKeepScreenOn(enabled)
    fun updateBetaDiagnosticsOptIn(enabled: Boolean) = controller.updateBetaDiagnosticsOptIn(enabled)

    fun updateReminder(enabled: Boolean, hour: Int, minute: Int, days: List<String>) {
        controller.updateReminderConfig(
            ReminderConfig(
                enabled = enabled,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                daysOfWeek = days.mapNotNull { dayName ->
                    Weekday.entries.firstOrNull { it.name == dayName }
                }.toSet(),
            ),
        )
    }

    fun requestReminderPermission(onResult: (Boolean, String?) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, error ->
            onResult(granted, error?.localizedDescription)
        }
    }

    fun exportBackup(onResult: (IosDocumentPayload?, String?) -> Unit) {
        scope.launch {
            runCatching {
                backupRepository.exportBackup().toIosDocumentPayload()
            }.onSuccess { payload ->
                onResult(payload, null)
            }.onFailure { error ->
                onResult(null, error.message ?: "Backup konnte nicht exportiert werden.")
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun importBackup(base64Data: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            runCatching {
                backupRepository.importBackup(Base64.decode(base64Data))
            }.onSuccess {
                onResult(true, null)
            }.onFailure { error ->
                onResult(false, error.message ?: "Backup konnte nicht importiert werden.")
            }
        }
    }

    fun resetUserData(onResult: (Boolean, String?) -> Unit) {
        scope.launch {
            runCatching {
                backupRepository.resetUserData()
            }.onSuccess {
                onResult(true, null)
            }.onFailure { error ->
                onResult(false, error.message ?: "Daten konnten nicht zurueckgesetzt werden.")
            }
        }
    }

    fun createIncidentReport(
        summary: String,
        details: String,
        currentScreen: String,
        includeDiagnostics: Boolean,
        onResult: (IosDocumentPayload?, String?) -> Unit,
    ) {
        scope.launch {
            runCatching {
                incidentRepository.createIncidentReport(
                    summary = summary,
                    details = details,
                    currentScreen = currentScreen,
                    includeDiagnostics = includeDiagnostics,
                ).toIosDocumentPayload()
            }.onSuccess { payload ->
                onResult(payload, null)
            }.onFailure { error ->
                onResult(null, error.message ?: "Incident-Report konnte nicht erstellt werden.")
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}

class IosCloseable(
    private val closeAction: () -> Unit,
) {
    fun close() {
        closeAction()
    }
}

class IosDocumentPayload(
    val base64Data: String,
    val fileName: String,
    val mimeType: String,
)

class IosSettingsState(
    val unitSystem: String,
    val weekStart: String,
    val themeMode: String,
    val themeScheme: String,
    val useDynamicColor: Boolean,
    val reducedMotion: Boolean,
    val defaultWarmupFlag: Boolean,
    val timerKeepScreenOn: Boolean,
    val betaDiagnosticsOptIn: Boolean,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val reminderDays: List<String>,
    val intensitySystem: String,
    val versionName: String,
    val versionCode: Int,
)

private class IosAppPreferencesRepository(
    private val userDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults(),
) : SharedAppPreferencesRepository {
    private val mutablePreferences = MutableStateFlow(loadPreferences())

    override val preferences: Flow<AppPreferences> = mutablePreferences.asStateFlow()

    override suspend fun updateUnitSystem(unitSystem: UnitSystem) = persist { it.copy(unitSystem = unitSystem) }
    override suspend fun updateWeekStart(weekStart: WeekStart) = persist { it.copy(weekStart = weekStart) }
    override suspend fun updateThemeMode(themeMode: ThemeMode) = persist { it.copy(themeMode = themeMode) }
    override suspend fun updateThemeScheme(themeScheme: ThemeScheme) = persist { it.copy(themeScheme = themeScheme) }
    override suspend fun updateUseDynamicColor(enabled: Boolean) = persist { it.copy(useDynamicColor = enabled) }
    override suspend fun updateReducedMotion(enabled: Boolean) = persist { it.copy(reducedMotion = enabled) }
    override suspend fun updateDefaultWarmupFlag(enabled: Boolean) = persist { it.copy(defaultWarmupFlag = enabled) }
    override suspend fun updateTimerKeepScreenOn(enabled: Boolean) = persist { it.copy(timerKeepScreenOn = enabled) }
    override suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean) = persist { it.copy(betaDiagnosticsOptIn = enabled) }
    override suspend fun updateReminderConfig(config: ReminderConfig) = persist { it.copy(reminderConfig = config) }
    override suspend fun updateIntensitySystem(intensitySystem: IntensitySystem) = persist { it.copy(intensitySystem = intensitySystem) }

    private fun persist(transform: (AppPreferences) -> AppPreferences) {
        val updated = transform(mutablePreferences.value)
        savePreferences(updated)
        mutablePreferences.value = updated
    }

    private fun loadPreferences(): AppPreferences {
        val defaults = AppPreferences()
        return AppPreferences(
            unitSystem = userDefaults.stringForKey(Keys.unitSystem)
                ?.let { value -> UnitSystem.entries.firstOrNull { it.name == value } }
                ?: defaults.unitSystem,
            weekStart = userDefaults.stringForKey(Keys.weekStart)
                ?.let { value -> WeekStart.entries.firstOrNull { it.name == value } }
                ?: defaults.weekStart,
            themeMode = userDefaults.stringForKey(Keys.themeMode)
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: defaults.themeMode,
            themeScheme = userDefaults.stringForKey(Keys.themeScheme)
                ?.let { value -> ThemeScheme.entries.firstOrNull { it.name == value } }
                ?: defaults.themeScheme,
            useDynamicColor = userDefaults.boolOrDefault(Keys.useDynamicColor, defaults.useDynamicColor),
            reducedMotion = userDefaults.boolOrDefault(Keys.reducedMotion, defaults.reducedMotion),
            defaultWarmupFlag = userDefaults.boolOrDefault(Keys.defaultWarmupFlag, defaults.defaultWarmupFlag),
            timerKeepScreenOn = userDefaults.boolOrDefault(Keys.timerKeepScreenOn, defaults.timerKeepScreenOn),
            betaDiagnosticsOptIn = userDefaults.boolOrDefault(Keys.betaDiagnosticsOptIn, defaults.betaDiagnosticsOptIn),
            reminderConfig = ReminderConfig(
                enabled = userDefaults.boolOrDefault(Keys.reminderEnabled, defaults.reminderConfig.enabled),
                hour = userDefaults.intOrDefault(Keys.reminderHour, defaults.reminderConfig.hour),
                minute = userDefaults.intOrDefault(Keys.reminderMinute, defaults.reminderConfig.minute),
                daysOfWeek = userDefaults.stringList(Keys.reminderDays)
                    ?.mapNotNull { value -> Weekday.entries.firstOrNull { it.name == value } }
                    ?.toSet()
                    ?.takeIf { it.isNotEmpty() }
                    ?: defaults.reminderConfig.daysOfWeek,
            ),
            intensitySystem = userDefaults.stringForKey(Keys.intensitySystem)
                ?.let { value -> IntensitySystem.entries.firstOrNull { it.name == value } }
                ?: defaults.intensitySystem,
        )
    }

    private fun savePreferences(preferences: AppPreferences) {
        userDefaults.setObject(preferences.unitSystem.name, Keys.unitSystem)
        userDefaults.setObject(preferences.weekStart.name, Keys.weekStart)
        userDefaults.setObject(preferences.themeMode.name, Keys.themeMode)
        userDefaults.setObject(preferences.themeScheme.name, Keys.themeScheme)
        userDefaults.setBool(preferences.useDynamicColor, Keys.useDynamicColor)
        userDefaults.setBool(preferences.reducedMotion, Keys.reducedMotion)
        userDefaults.setBool(preferences.defaultWarmupFlag, Keys.defaultWarmupFlag)
        userDefaults.setBool(preferences.timerKeepScreenOn, Keys.timerKeepScreenOn)
        userDefaults.setBool(preferences.betaDiagnosticsOptIn, Keys.betaDiagnosticsOptIn)
        userDefaults.setBool(preferences.reminderConfig.enabled, Keys.reminderEnabled)
        userDefaults.setInteger(preferences.reminderConfig.hour.toLong(), Keys.reminderHour)
        userDefaults.setInteger(preferences.reminderConfig.minute.toLong(), Keys.reminderMinute)
        userDefaults.setObject(preferences.reminderConfig.daysOfWeek.map { it.name }, Keys.reminderDays)
        userDefaults.setObject(preferences.intensitySystem.name, Keys.intensitySystem)
    }

    private object Keys {
        const val unitSystem = "settings.unitSystem"
        const val weekStart = "settings.weekStart"
        const val themeMode = "settings.themeMode"
        const val themeScheme = "settings.themeScheme"
        const val useDynamicColor = "settings.useDynamicColor"
        const val reducedMotion = "settings.reducedMotion"
        const val defaultWarmupFlag = "settings.defaultWarmupFlag"
        const val timerKeepScreenOn = "settings.timerKeepScreenOn"
        const val betaDiagnosticsOptIn = "settings.betaDiagnosticsOptIn"
        const val reminderEnabled = "settings.reminderEnabled"
        const val reminderHour = "settings.reminderHour"
        const val reminderMinute = "settings.reminderMinute"
        const val reminderDays = "settings.reminderDays"
        const val intensitySystem = "settings.intensitySystem"
    }
}

private class IosReminderScheduler(
    private val notificationCenter: UNUserNotificationCenter = UNUserNotificationCenter.currentNotificationCenter(),
) : SharedReminderScheduler {
    override suspend fun sync(config: ReminderConfig) {
        cancel()
        if (!config.enabled || config.daysOfWeek.isEmpty()) return

        config.daysOfWeek.forEach { weekday ->
            scheduleReminder(weekday, config.hour, config.minute)
        }
    }

    override suspend fun cancel() {
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(
            identifiers = Weekday.entries.map(::identifierForWeekday),
        )
    }

    private suspend fun scheduleReminder(weekday: Weekday, hour: Int, minute: Int) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val content = UNMutableNotificationContent().apply {
                setTitle("IronLog")
                setBody("Zeit fuer dein Training.")
                setSound(UNNotificationSound.defaultSound())
            }

            val components = NSDateComponents().apply {
                setWeekday(weekday.toIosWeekday().toLong())
                setHour(hour.toLong())
                setMinute(minute.toLong())
            }

            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = components,
                repeats = true,
            )

            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = identifierForWeekday(weekday),
                content = content,
                trigger = trigger,
            )

            notificationCenter.addNotificationRequest(request) { error ->
                if (error == null) {
                    continuation.resume(Unit)
                } else {
                    continuation.resumeWithException(
                        IllegalStateException(error.localizedDescription ?: "Reminder konnte nicht geplant werden."),
                    )
                }
            }
        }
    }

    private fun identifierForWeekday(weekday: Weekday): String = "ironlog.reminder.${weekday.name.lowercase()}"
}

/**
 * iOS does not yet persist workout data in a real local database (unlike Android's Room-backed
 * store), so there is nothing meaningful to export or restore. Earlier revisions silently
 * produced an empty-but-"successful" backup on export and merely stashed the raw JSON without
 * ever restoring anything on import, which misleads users into believing their training data is
 * safely backed up / restored. Until iOS has real persistence to back this feature, we fail
 * loudly instead of pretending to succeed.
 */
private class IosBackupRepository(
    private val buildInfo: BuildInfo,
) : SharedBackupRepository {

    override suspend fun exportBackup(): BackupBlob {
        throw BackupNotSupportedException(
            "Backup-Export wird auf iOS noch nicht unterstuetzt (App-Version ${buildInfo.versionName}).",
        )
    }

    override suspend fun importBackup(bytes: ByteArray) {
        throw BackupNotSupportedException("Backup-Import wird auf iOS noch nicht unterstuetzt.")
    }

    override suspend fun resetUserData() {
        // No local workout data exists on iOS yet, so there is nothing to reset.
    }
}

/**
 * Thrown by [IosBackupRepository] to signal that backup/restore is not available on this
 * platform yet, so callers can surface a clear message instead of assuming success.
 */
class BackupNotSupportedException(message: String) : Exception(message)

private class IosIncidentReportRepository(
    private val buildInfo: BuildInfo,
) : SharedIncidentReportRepository {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun createIncidentReport(
        summary: String,
        details: String,
        currentScreen: String,
        includeDiagnostics: Boolean,
        throwableDescription: String?,
    ): IncidentAttachment {
        val incidentId = Uuid.random().toString().take(8)
        val createdAt = currentEpochMillis()
        val payload = IncidentReportPayload(
            incidentId = incidentId,
            createdAtEpochMillis = createdAt,
            appVersionName = buildInfo.versionName,
            appVersionCode = buildInfo.versionCode,
            currentScreen = currentScreen,
            summary = IncidentReportSanitizer.sanitizeText(summary.trim()),
            details = IncidentReportSanitizer.sanitizeText(details.trim()),
            stacktrace = throwableDescription
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let(IncidentReportSanitizer::sanitizeText),
            diagnostics = if (includeDiagnostics) {
                IncidentDiagnostics(
                    osVersion = "${UIDevice.currentDevice.systemName} ${UIDevice.currentDevice.systemVersion}",
                    deviceModel = UIDevice.currentDevice.model,
                    manufacturer = "Apple",
                )
            } else {
                null
            },
        )

        return IncidentAttachment(
            bytes = json.encodeToString(IncidentReportPayload.serializer(), payload).encodeToByteArray(),
            fileName = "incident-$incidentId.json",
            mimeType = "application/json",
        )
    }
}

private fun SettingsPreferencesState.toIosState(buildInfo: BuildInfo): IosSettingsState = IosSettingsState(
    unitSystem = preferences.unitSystem.name,
    weekStart = preferences.weekStart.name,
    themeMode = preferences.themeMode.name,
    themeScheme = preferences.themeScheme.name,
    useDynamicColor = preferences.useDynamicColor,
    reducedMotion = preferences.reducedMotion,
    defaultWarmupFlag = preferences.defaultWarmupFlag,
    timerKeepScreenOn = preferences.timerKeepScreenOn,
    betaDiagnosticsOptIn = preferences.betaDiagnosticsOptIn,
    reminderEnabled = preferences.reminderConfig.enabled,
    reminderHour = preferences.reminderConfig.hour,
    reminderMinute = preferences.reminderConfig.minute,
    reminderDays = preferences.reminderConfig.daysOfWeek.map { it.name },
    intensitySystem = preferences.intensitySystem.name,
    versionName = buildInfo.versionName,
    versionCode = buildInfo.versionCode,
)

@OptIn(ExperimentalEncodingApi::class)
private fun BackupBlob.toIosDocumentPayload(): IosDocumentPayload = IosDocumentPayload(
    base64Data = bytes.encodeBase64(),
    fileName = fileName,
    mimeType = mimeType,
)

@OptIn(ExperimentalEncodingApi::class)
private fun IncidentAttachment.toIosDocumentPayload(): IosDocumentPayload = IosDocumentPayload(
    base64Data = bytes.encodeBase64(),
    fileName = fileName,
    mimeType = mimeType,
)

private fun NSUserDefaults.boolOrDefault(key: String, defaultValue: Boolean): Boolean =
    if (objectForKey(key) == null) defaultValue else boolForKey(key)

private fun NSUserDefaults.intOrDefault(key: String, defaultValue: Int): Int =
    if (objectForKey(key) == null) defaultValue else integerForKey(key).toInt()

private fun NSUserDefaults.stringList(key: String): List<String>? =
    (arrayForKey(key) as? List<*>)?.mapNotNull { it as? String }

private fun Weekday.toIosWeekday(): Int = when (this) {
    Weekday.SUNDAY -> 1
    Weekday.MONDAY -> 2
    Weekday.TUESDAY -> 3
    Weekday.WEDNESDAY -> 4
    Weekday.THURSDAY -> 5
    Weekday.FRIDAY -> 6
    Weekday.SATURDAY -> 7
}

private fun currentBuildInfo(): BuildInfo {
    val bundle = NSBundle.mainBundle
    val versionName = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "0.1.0"
    val versionCode = (bundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)?.toIntOrNull() ?: 1
    return BuildInfo(versionName = versionName, versionCode = versionCode)
}

@OptIn(ExperimentalEncodingApi::class)
private fun ByteArray.encodeBase64(): String = Base64.encode(this)

private fun currentEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
