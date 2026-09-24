import Foundation
import Shared
import SwiftUI

@MainActor
final class IOSSettingsViewModel: ObservableObject {
    @Published var state = SettingsFormState.empty
    @Published var incidentSummary = ""
    @Published var incidentDetails = ""
    @Published var isBusy = false
    @Published var isImportingBackup = false
    @Published var isExportingBackup = false
    @Published var isConfirmingReset = false
    @Published var isConfirmingBackupImport = false
    @Published var isConfirmingRecoveryRestore = false
    @Published var recoveryBackup: IosRecoveryBackup?
    @Published var exportDocument = BinaryFileDocument(fileName: "ironlog-backup-v1.json", data: Data())
    @Published var activeAlert: SettingsAlert?
    @Published var shareItem: SharedFileItem?

    let unitSystemOptions: [String]
    let weekStartOptions: [String]
    let themeModeOptions: [String]
    let themeSchemeOptions: [String]
    let intensitySystemOptions: [String]
    let deloadModeOptions: [String]
    let weekdayOptions: [String]

    private let feature = IosSettingsFeature()
    private var stateHandle: IosCloseable?
    private var pendingBackupImport: PendingBackupImport?

    init() {
        unitSystemOptions = feature.unitSystemOptions()
        weekStartOptions = feature.weekStartOptions()
        themeModeOptions = feature.themeModeOptions()
        themeSchemeOptions = feature.themeSchemeOptions()
        intensitySystemOptions = feature.intensitySystemOptions()
        deloadModeOptions = feature.deloadModeOptions()
        weekdayOptions = feature.weekdayOptions()
        state = SettingsFormState(sharedState: feature.currentState())
        stateHandle = feature.watchState(onState: { [weak self] sharedState in
            guard let self else { return }
            Task { @MainActor in
                self.state = SettingsFormState(sharedState: sharedState)
            }
        })
        refreshRecoveryBackup()
    }

    deinit {
        stateHandle?.close()
        feature.close()
    }

    func updateUnitSystem(_ value: String) {
        feature.updateUnitSystem(value: value)
    }

    func updateWeekStart(_ value: String) {
        feature.updateWeekStart(value: value)
    }

    func updateThemeMode(_ value: String) {
        feature.updateThemeMode(value: value)
    }

    func updateThemeScheme(_ value: String) {
        feature.updateThemeScheme(value: value)
    }

    func updateIntensitySystem(_ value: String) {
        feature.updateIntensitySystem(value: value)
    }

    func updateDeloadMode(_ value: String) {
        feature.updateDeloadMode(value: value)
    }

    func updateUseDynamicColor(_ enabled: Bool) {
        feature.updateUseDynamicColor(enabled: enabled)
    }

    func updateReducedMotion(_ enabled: Bool) {
        feature.updateReducedMotion(enabled: enabled)
    }

    func updateDefaultWarmupFlag(_ enabled: Bool) {
        feature.updateDefaultWarmupFlag(enabled: enabled)
    }

    func updateTimerKeepScreenOn(_ enabled: Bool) {
        feature.updateTimerKeepScreenOn(enabled: enabled)
    }

    func updateShareWeightHistoryAcrossContexts(_ enabled: Bool) {
        feature.updateShareWeightHistoryAcrossContexts(enabled: enabled)
    }

    func updateAutoRestTimerEnabled(_ enabled: Bool) {
        feature.updateAutoRestTimerEnabled(enabled: enabled)
    }

    func updateDefaultRestTimeSeconds(_ seconds: Int) {
        feature.updateDefaultRestTimeSeconds(seconds: Int32(seconds))
    }

    func updatePlateCalculatorEnabled(_ enabled: Bool) {
        feature.updatePlateCalculatorEnabled(enabled: enabled)
    }

    func updateAvailablePlates(_ plates: [Double]) {
        feature.updateAvailablePlates(plates: plates.map { KotlinDouble(double: $0) })
    }

    func updateBarbellWeightKg(_ weightKg: Double) {
        feature.updateBarbellWeightKg(weightKg: weightKg)
    }

    func updateBackupReminderEnabled(_ enabled: Bool) {
        feature.updateBackupReminderEnabled(enabled: enabled)
    }

    func updateBetaDiagnosticsOptIn(_ enabled: Bool) {
        feature.updateBetaDiagnosticsOptIn(enabled: enabled)
    }

    func setReminderEnabled(_ enabled: Bool) {
        if enabled {
            feature.requestReminderPermission { [weak self] granted, error in
                Task { @MainActor in
                    guard let self else { return }
                    if granted.boolValue {
                        self.pushReminderState(enabled: true)
                    } else {
                        self.activeAlert = SettingsAlert(
                            title: "Reminder nicht erlaubt",
                            message: error ?? "Bitte erlaube Mitteilungen in den iPhone-Einstellungen."
                        )
                    }
                }
            }
        } else {
            pushReminderState(enabled: false)
        }
    }

    func updateReminderTime(_ date: Date) {
        let components = Calendar.current.dateComponents([.hour, .minute], from: date)
        pushReminderState(
            enabled: state.reminderEnabled,
            hour: components.hour ?? state.reminderHour,
            minute: components.minute ?? state.reminderMinute,
            days: Array(state.reminderDays)
        )
    }

    func setReminderDay(_ day: String, enabled: Bool) {
        var days = state.reminderDays
        if enabled {
            days.insert(day)
        } else {
            days.remove(day)
        }

        pushReminderState(
            enabled: state.reminderEnabled,
            hour: state.reminderHour,
            minute: state.reminderMinute,
            days: Array(days)
        )
    }

    func startBackupExport() {
        isBusy = true
        feature.exportBackup { [weak self] payload, error in
            Task { @MainActor in
                guard let self else { return }
                self.isBusy = false
                if let payload {
                    let data = Data(base64Encoded: payload.base64Data) ?? Data()
                    self.exportDocument = BinaryFileDocument(
                        fileName: payload.fileName,
                        data: data
                    )
                    self.isExportingBackup = true
                } else {
                    self.activeAlert = SettingsAlert(
                        title: "Backup fehlgeschlagen",
                        message: error ?? "Backup konnte nicht exportiert werden."
                    )
                }
            }
        }
    }

    /// Called only after SwiftUI's file exporter has written the selected document successfully.
    /// Preparing a payload is not enough because the user can cancel the exporter.
    func recordSuccessfulBackupExport() {
        feature.recordSuccessfulBackupExport()
    }

    func prepareBackupImport(from result: Result<[URL], Error>) {
        pendingBackupImport = nil
        isConfirmingBackupImport = false
        switch result {
        case .success(let urls):
            guard let url = urls.first else {
                activeAlert = SettingsAlert(
                    title: "Import abgebrochen",
                    message: "Es wurde keine Backup-Datei ausgewählt."
                )
                return
            }
            do {
                isBusy = true
                let hasSecurityScopedAccess = url.startAccessingSecurityScopedResource()
                defer {
                    if hasSecurityScopedAccess {
                        url.stopAccessingSecurityScopedResource()
                    }
                }
                let data = try Data(contentsOf: url)
                let base64Data = data.base64EncodedString()
                feature.inspectBackup(base64Data: base64Data) { [weak self] preview, error in
                    Task { @MainActor in
                        guard let self else { return }
                        self.isBusy = false
                        if let preview {
                            self.pendingBackupImport = PendingBackupImport(
                                base64Data: base64Data,
                                preview: preview,
                            )
                            self.isConfirmingBackupImport = true
                        } else {
                            self.activeAlert = SettingsAlert(
                                title: "Import abgebrochen",
                                message: error ?? "Backup konnte nicht geprüft werden."
                            )
                        }
                    }
                }
            } catch {
                isBusy = false
                activeAlert = SettingsAlert(title: "Import fehlgeschlagen", message: error.localizedDescription)
            }
        case .failure(let error):
            activeAlert = SettingsAlert(title: "Import abgebrochen", message: error.localizedDescription)
        }
    }

    /// Imports exactly the bytes that were validated and shown in the confirmation dialog.
    func confirmBackupImport() {
        guard let pending = pendingBackupImport else { return }
        isConfirmingBackupImport = false
        isBusy = true
        feature.importBackup(
            base64Data: pending.base64Data,
            expectedSnapshot: pending.preview.expectedSnapshot,
        ) { [weak self] success, error in
            Task { @MainActor in
                guard let self else { return }
                self.isBusy = false
                self.pendingBackupImport = nil
                let didImport = success.boolValue && error == nil
                self.activeAlert = SettingsAlert(
                    title: didImport ? "Backup importiert" : "Import fehlgeschlagen",
                    message: didImport
                        ? "Die lokalen Trainingsdaten wurden wiederhergestellt."
                        : (error ?? "Backup konnte nicht importiert werden.")
                )
                if didImport {
                    self.refreshRecoveryBackup()
                }
            }
        }
    }

    func cancelBackupImport() {
        isConfirmingBackupImport = false
        pendingBackupImport = nil
    }

    func refreshRecoveryBackup() {
        feature.latestRecovery { [weak self] recovery, error in
            Task { @MainActor in
                guard let self else { return }
                if let error, recovery == nil {
                    self.activeAlert = SettingsAlert(
                        title: "Wiederherstellungspunkt nicht verfügbar",
                        message: error
                    )
                } else {
                    self.recoveryBackup = recovery
                }
            }
        }
    }

    func requestRestoreLatestRecovery() {
        guard recoveryBackup != nil else { return }
        isConfirmingRecoveryRestore = true
    }

    func confirmRestoreLatestRecovery() {
        guard recoveryBackup != nil else { return }
        isConfirmingRecoveryRestore = false
        isBusy = true
        feature.restoreLatestRecovery { [weak self] recovery, error in
            Task { @MainActor in
                guard let self else { return }
                self.isBusy = false
                if let recovery {
                    self.recoveryBackup = recovery
                    self.activeAlert = SettingsAlert(
                        title: "Trainingsdaten wiederhergestellt",
                        message: "Der vorherige Wiederherstellungspunkt wurde geladen."
                    )
                } else {
                    self.activeAlert = SettingsAlert(
                        title: "Wiederherstellung fehlgeschlagen",
                        message: error ?? "Kein Wiederherstellungspunkt ist verfügbar."
                    )
                    self.refreshRecoveryBackup()
                }
            }
        }
    }

    func cancelRecoveryRestore() {
        isConfirmingRecoveryRestore = false
    }

    var pendingImportSummary: String? {
        guard let preview = pendingBackupImport?.preview else { return nil }
        let existingDataMessage = preview.replacesExistingData
            ? "Die vorhandenen lokalen Trainingsdaten werden vollständig ersetzt."
            : "Auf diesem Gerät sind noch keine lokalen Trainingsdaten geladen."
        return "Schema \(preview.schemaVersion): " +
            "\(preview.exerciseCount) Übungen, " +
            "\(preview.workoutSessionCount) Trainings, " +
            "\(preview.workoutSetCount) Sätze, " +
            "\(preview.trainingPlanCount) Pläne, " +
            "\(preview.metaPlanCount) Meta-Pläne, " +
            "\(preview.progressionSuggestionCount) Progressionsvorschläge. " +
            existingDataMessage
    }

    func resetUserData() {
        isConfirmingReset = false
        isBusy = true
        feature.resetUserData { [weak self] success, error in
            Task { @MainActor in
                guard let self else { return }
                self.isBusy = false
                self.activeAlert = SettingsAlert(
                    title: success.boolValue ? "Daten zurückgesetzt" : "Reset fehlgeschlagen",
                    message: success.boolValue ? "Lokale Trainingsdaten wurden zurückgesetzt." : (error ?? "Daten konnten nicht zurückgesetzt werden.")
                )
            }
        }
    }

    func createIncidentReport() {
        guard !incidentSummary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            activeAlert = SettingsAlert(title: "Kurzbeschreibung fehlt", message: "Bitte füge eine kurze Zusammenfassung hinzu.")
            return
        }

        isBusy = true
        feature.createIncidentReport(
            summary: incidentSummary,
            details: incidentDetails,
            currentScreen: "Settings",
            includeDiagnostics: state.betaDiagnosticsOptIn
        ) { [weak self] payload, error in
            Task { @MainActor in
                guard let self else { return }
                self.isBusy = false
                if let payload {
                    do {
                        let url = try self.writeTemporaryFile(payload: payload)
                        self.shareItem = SharedFileItem(url: url)
                        self.incidentSummary = ""
                        self.incidentDetails = ""
                    } catch {
                        self.activeAlert = SettingsAlert(title: "Report fehlgeschlagen", message: error.localizedDescription)
                    }
                } else {
                    self.activeAlert = SettingsAlert(
                        title: "Report fehlgeschlagen",
                        message: error ?? "Incident-Report konnte nicht erstellt werden."
                    )
                }
            }
        }
    }

    func label(for rawValue: String) -> String {
        switch rawValue {
        case "METRIC": return "Metrisch"
        case "IMPERIAL": return "Imperial"
        case "MONDAY": return "Montag"
        case "SUNDAY": return "Sonntag"
        case "SYSTEM": return "System"
        case "LIGHT": return "Hell"
        case "DARK": return "Dunkel"
        case "AMBER": return "Amber"
        case "DEEP_CYAN": return "Deep Cyan"
        case "NEON_RED": return "Neon Red"
        case "FORGE": return "Forge"
        case "RASTER": return "Raster"
        case "TIDE": return "Tide"
        case "PULSE": return "Pulse"
        case "OFF": return "Aus"
        case "RPE": return "RPE"
        case "RIR": return "RIR"
        case "NONE": return "Aus"
        case "HALVE_SET_VOLUME": return "Satzvolumen halbieren"
        case "REDUCE_INTENSITY_BY_15_PERCENT": return "Intensität um 15 % reduzieren"
        case "TUESDAY": return "Dienstag"
        case "WEDNESDAY": return "Mittwoch"
        case "THURSDAY": return "Donnerstag"
        case "FRIDAY": return "Freitag"
        case "SATURDAY": return "Samstag"
        default: return rawValue
        }
    }

    func restTimeLabel(_ seconds: Int) -> String {
        if seconds < 60 { return "\(seconds) s" }
        let minutes = seconds / 60
        let remainder = seconds % 60
        return remainder == 0 ? "\(minutes) min" : "\(minutes) min \(remainder) s"
    }

    func weightLabel(_ kilograms: Double) -> String {
        let value = kilograms.truncatingRemainder(dividingBy: 1) == 0
            ? String(Int(kilograms))
            : String(kilograms)
        return "\(value) kg"
    }

    var reminderDate: Date {
        var components = DateComponents()
        components.hour = state.reminderHour
        components.minute = state.reminderMinute
        return Calendar.current.date(from: components) ?? Date()
    }

    var lastSuccessfulExportDate: Date? {
        guard let timestamp = state.lastSuccessfulExportEpochMillis else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(timestamp) / 1_000.0)
    }

    var recoveryDate: Date? {
        guard let timestamp = recoveryBackup?.timestampMillis else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(timestamp) / 1_000.0)
    }

    var recoverySummary: String? {
        guard let recovery = recoveryBackup else { return nil }
        return String(Int(recovery.exerciseCount)) + " Übungen, " +
            String(Int(recovery.workoutSessionCount)) + " Trainings, " +
            String(Int(recovery.workoutSetCount)) + " Sätze, " +
            String(Int(recovery.trainingPlanCount)) + " Pläne, " +
            String(Int(recovery.metaPlanCount)) + " Meta-Pläne, " +
            String(Int(recovery.progressionSuggestionCount)) + " Progressionsvorschläge"
    }

    private func pushReminderState(enabled: Bool, hour: Int? = nil, minute: Int? = nil, days: [String]? = nil) {
        feature.updateReminder(
            enabled: enabled,
            hour: Int32(hour ?? state.reminderHour),
            minute: Int32(minute ?? state.reminderMinute),
            days: days ?? Array(state.reminderDays)
        )
    }

    private func writeTemporaryFile(payload: IosDocumentPayload) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(payload.fileName)
        let data = Data(base64Encoded: payload.base64Data) ?? Data()
        try data.write(to: url, options: .atomic)
        return url
    }
}

struct SettingsFormState {
    var unitSystem: String
    var weekStart: String
    var themeMode: String
    var themeScheme: String
    var useDynamicColor: Bool
    var reducedMotion: Bool
    var defaultWarmupFlag: Bool
    var timerKeepScreenOn: Bool
    var betaDiagnosticsOptIn: Bool
    var shareWeightHistoryAcrossContexts: Bool
    var autoRestTimerEnabled: Bool
    var defaultRestTimeSeconds: Int
    var deloadMode: String
    var plateCalculatorEnabled: Bool
    var availablePlates: [Double]
    var barbellWeightKg: Double
    var reminderEnabled: Bool
    var reminderHour: Int
    var reminderMinute: Int
    var reminderDays: Set<String>
    var intensitySystem: String
    var lastSuccessfulExportEpochMillis: Int64?
    var backupReminderEnabled: Bool
    var backupReminderDue: Bool
    var versionName: String
    var versionCode: Int

    init(
        unitSystem: String,
        weekStart: String,
        themeMode: String,
        themeScheme: String,
        useDynamicColor: Bool,
        reducedMotion: Bool,
        defaultWarmupFlag: Bool,
        timerKeepScreenOn: Bool,
        betaDiagnosticsOptIn: Bool,
        shareWeightHistoryAcrossContexts: Bool,
        autoRestTimerEnabled: Bool,
        defaultRestTimeSeconds: Int,
        deloadMode: String,
        plateCalculatorEnabled: Bool,
        availablePlates: [Double],
        barbellWeightKg: Double,
        reminderEnabled: Bool,
        reminderHour: Int,
        reminderMinute: Int,
        reminderDays: Set<String>,
        intensitySystem: String,
        lastSuccessfulExportEpochMillis: Int64?,
        backupReminderEnabled: Bool,
        backupReminderDue: Bool,
        versionName: String,
        versionCode: Int
    ) {
        self.unitSystem = unitSystem
        self.weekStart = weekStart
        self.themeMode = themeMode
        self.themeScheme = themeScheme
        self.useDynamicColor = useDynamicColor
        self.reducedMotion = reducedMotion
        self.defaultWarmupFlag = defaultWarmupFlag
        self.timerKeepScreenOn = timerKeepScreenOn
        self.betaDiagnosticsOptIn = betaDiagnosticsOptIn
        self.shareWeightHistoryAcrossContexts = shareWeightHistoryAcrossContexts
        self.autoRestTimerEnabled = autoRestTimerEnabled
        self.defaultRestTimeSeconds = defaultRestTimeSeconds
        self.deloadMode = deloadMode
        self.plateCalculatorEnabled = plateCalculatorEnabled
        self.availablePlates = availablePlates
        self.barbellWeightKg = barbellWeightKg
        self.reminderEnabled = reminderEnabled
        self.reminderHour = reminderHour
        self.reminderMinute = reminderMinute
        self.reminderDays = reminderDays
        self.intensitySystem = intensitySystem
        self.lastSuccessfulExportEpochMillis = lastSuccessfulExportEpochMillis
        self.backupReminderEnabled = backupReminderEnabled
        self.backupReminderDue = backupReminderDue
        self.versionName = versionName
        self.versionCode = versionCode
    }

    static let empty = SettingsFormState(
        unitSystem: "METRIC",
        weekStart: "MONDAY",
        themeMode: "SYSTEM",
        themeScheme: "AMBER",
        useDynamicColor: false,
        reducedMotion: false,
        defaultWarmupFlag: false,
        timerKeepScreenOn: false,
        betaDiagnosticsOptIn: false,
        shareWeightHistoryAcrossContexts: false,
        autoRestTimerEnabled: false,
        defaultRestTimeSeconds: 120,
        deloadMode: "NONE",
        plateCalculatorEnabled: true,
        availablePlates: [25, 20, 15, 10, 5, 2.5, 1.25],
        barbellWeightKg: 20,
        reminderEnabled: false,
        reminderHour: 19,
        reminderMinute: 0,
        reminderDays: ["MONDAY", "WEDNESDAY", "FRIDAY"],
        intensitySystem: "RPE",
        lastSuccessfulExportEpochMillis: nil,
        backupReminderEnabled: false,
        backupReminderDue: false,
        versionName: "0.1.0",
        versionCode: 1
    )

    init(sharedState: IosSettingsState) {
        unitSystem = sharedState.unitSystem
        weekStart = sharedState.weekStart
        themeMode = sharedState.themeMode
        themeScheme = sharedState.themeScheme
        useDynamicColor = sharedState.useDynamicColor
        reducedMotion = sharedState.reducedMotion
        defaultWarmupFlag = sharedState.defaultWarmupFlag
        timerKeepScreenOn = sharedState.timerKeepScreenOn
        betaDiagnosticsOptIn = sharedState.betaDiagnosticsOptIn
        shareWeightHistoryAcrossContexts = sharedState.shareWeightHistoryAcrossContexts
        autoRestTimerEnabled = sharedState.autoRestTimerEnabled
        defaultRestTimeSeconds = Int(sharedState.defaultRestTimeSeconds)
        deloadMode = sharedState.deloadMode
        plateCalculatorEnabled = sharedState.plateCalculatorEnabled
        availablePlates = sharedState.availablePlates.map { $0.doubleValue }
        barbellWeightKg = sharedState.barbellWeightKg
        reminderEnabled = sharedState.reminderEnabled
        reminderHour = Int(sharedState.reminderHour)
        reminderMinute = Int(sharedState.reminderMinute)
        reminderDays = Set(sharedState.reminderDays)
        intensitySystem = sharedState.intensitySystem
        lastSuccessfulExportEpochMillis = sharedState.lastSuccessfulExportEpochMillis?.int64Value
        backupReminderEnabled = sharedState.backupReminderEnabled
        backupReminderDue = sharedState.backupReminderDue
        versionName = sharedState.versionName
        versionCode = Int(sharedState.versionCode)
    }
}

struct SettingsAlert: Identifiable {
    let id = UUID()
    let title: String
    let message: String
}

struct SharedFileItem: Identifiable {
    let id = UUID()
    let url: URL
}

private struct PendingBackupImport {
    let base64Data: String
    let preview: IosBackupPreview
}
