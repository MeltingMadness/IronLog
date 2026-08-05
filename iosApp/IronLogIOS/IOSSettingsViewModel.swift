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
    @Published var exportDocument = BinaryFileDocument(fileName: "ironlog-backup-v1.json", data: Data())
    @Published var activeAlert: SettingsAlert?
    @Published var shareItem: SharedFileItem?

    let unitSystemOptions: [String]
    let weekStartOptions: [String]
    let themeModeOptions: [String]
    let themeSchemeOptions: [String]
    let intensitySystemOptions: [String]
    let weekdayOptions: [String]

    private let feature = IosSettingsFeature()
    private var stateHandle: IosCloseable?

    init() {
        unitSystemOptions = feature.unitSystemOptions() as? [String] ?? []
        weekStartOptions = feature.weekStartOptions() as? [String] ?? []
        themeModeOptions = feature.themeModeOptions() as? [String] ?? []
        themeSchemeOptions = feature.themeSchemeOptions() as? [String] ?? []
        intensitySystemOptions = feature.intensitySystemOptions() as? [String] ?? []
        weekdayOptions = feature.weekdayOptions() as? [String] ?? []
        state = SettingsFormState(sharedState: feature.currentState())
        stateHandle = feature.watchState(onState: { [weak self] sharedState in
            guard let self else { return }
            Task { @MainActor in
                self.state = SettingsFormState(sharedState: sharedState)
            }
        })
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

    func updateBetaDiagnosticsOptIn(_ enabled: Bool) {
        feature.updateBetaDiagnosticsOptIn(enabled: enabled)
    }

    func setReminderEnabled(_ enabled: Bool) {
        if enabled {
            feature.requestReminderPermission { [weak self] granted, error in
                Task { @MainActor in
                    guard let self else { return }
                    if granted {
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

    func importBackup(from result: Result<URL, Error>) {
        switch result {
        case .success(let url):
            do {
                isBusy = true
                let data = try Data(contentsOf: url)
                feature.importBackup(base64Data: data.base64EncodedString()) { [weak self] success, error in
                    Task { @MainActor in
                        guard let self else { return }
                        self.isBusy = false
                        self.activeAlert = SettingsAlert(
                            title: success ? "Backup importiert" : "Import fehlgeschlagen",
                            message: success ? "Das Backup wurde validiert und fuer spaetere iOS-Migrationen gespeichert." : (error ?? "Backup konnte nicht importiert werden.")
                        )
                    }
                }
            } catch {
                activeAlert = SettingsAlert(title: "Import fehlgeschlagen", message: error.localizedDescription)
            }
        case .failure(let error):
            activeAlert = SettingsAlert(title: "Import abgebrochen", message: error.localizedDescription)
        }
    }

    func resetUserData() {
        isBusy = true
        feature.resetUserData { [weak self] success, error in
            Task { @MainActor in
                guard let self else { return }
                self.isBusy = false
                self.activeAlert = SettingsAlert(
                    title: success ? "Daten zurueckgesetzt" : "Reset fehlgeschlagen",
                    message: success ? "Lokale Backup-Zwischenstaende wurden geloescht." : (error ?? "Daten konnten nicht zurueckgesetzt werden.")
                )
            }
        }
    }

    func createIncidentReport() {
        guard !incidentSummary.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            activeAlert = SettingsAlert(title: "Kurzbeschreibung fehlt", message: "Bitte fuege eine kurze Zusammenfassung hinzu.")
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
        case "OFF": return "Aus"
        case "RPE": return "RPE"
        case "RIR": return "RIR"
        case "TUESDAY": return "Dienstag"
        case "WEDNESDAY": return "Mittwoch"
        case "THURSDAY": return "Donnerstag"
        case "FRIDAY": return "Freitag"
        case "SATURDAY": return "Samstag"
        default: return rawValue
        }
    }

    var reminderDate: Date {
        var components = DateComponents()
        components.hour = state.reminderHour
        components.minute = state.reminderMinute
        return Calendar.current.date(from: components) ?? Date()
    }

    private func pushReminderState(enabled: Bool, hour: Int? = nil, minute: Int? = nil, days: [String]? = nil) {
        feature.updateReminder(
            enabled: enabled,
            hour: hour ?? state.reminderHour,
            minute: minute ?? state.reminderMinute,
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
    var reminderEnabled: Bool
    var reminderHour: Int
    var reminderMinute: Int
    var reminderDays: Set<String>
    var intensitySystem: String
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
        reminderEnabled: Bool,
        reminderHour: Int,
        reminderMinute: Int,
        reminderDays: Set<String>,
        intensitySystem: String,
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
        self.reminderEnabled = reminderEnabled
        self.reminderHour = reminderHour
        self.reminderMinute = reminderMinute
        self.reminderDays = reminderDays
        self.intensitySystem = intensitySystem
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
        reminderEnabled: false,
        reminderHour: 19,
        reminderMinute: 0,
        reminderDays: ["MONDAY", "WEDNESDAY", "FRIDAY"],
        intensitySystem: "RPE",
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
        reminderEnabled = sharedState.reminderEnabled
        reminderHour = Int(sharedState.reminderHour)
        reminderMinute = Int(sharedState.reminderMinute)
        reminderDays = Set(sharedState.reminderDays.compactMap { $0 as? String })
        intensitySystem = sharedState.intensitySystem
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
