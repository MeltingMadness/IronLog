import SwiftUI
import UniformTypeIdentifiers

struct SettingsScreen: View {
    @EnvironmentObject private var viewModel: IOSSettingsViewModel

    var body: some View {
        NavigationStack {
            Form {
                Section("Anzeige") {
                    Picker("Einheiten", selection: Binding(
                        get: { viewModel.state.unitSystem },
                        set: viewModel.updateUnitSystem
                    )) {
                        ForEach(viewModel.unitSystemOptions, id: \.self) { option in
                            Text(viewModel.label(for: option)).tag(option)
                        }
                    }

                    Picker("Wochenstart", selection: Binding(
                        get: { viewModel.state.weekStart },
                        set: viewModel.updateWeekStart
                    )) {
                        ForEach(viewModel.weekStartOptions, id: \.self) { option in
                            Text(viewModel.label(for: option)).tag(option)
                        }
                    }

                    Picker("Theme-Modus", selection: Binding(
                        get: { viewModel.state.themeMode },
                        set: viewModel.updateThemeMode
                    )) {
                        ForEach(viewModel.themeModeOptions, id: \.self) { option in
                            Text(viewModel.label(for: option)).tag(option)
                        }
                    }

                    Picker("Akzent", selection: Binding(
                        get: { viewModel.state.themeScheme },
                        set: viewModel.updateThemeScheme
                    )) {
                        ForEach(viewModel.themeSchemeOptions, id: \.self) { option in
                            Text(viewModel.label(for: option)).tag(option)
                        }
                    }

                    Toggle("Reduzierte Bewegung", isOn: Binding(
                        get: { viewModel.state.reducedMotion },
                        set: viewModel.updateReducedMotion
                    ))
                }

                Section("Training") {
                    Picker("Intensität", selection: Binding(
                        get: { viewModel.state.intensitySystem },
                        set: viewModel.updateIntensitySystem
                    )) {
                        ForEach(viewModel.intensitySystemOptions, id: \.self) { option in
                            Text(viewModel.label(for: option)).tag(option)
                        }
                    }

                    Picker("Deload", selection: Binding(
                        get: { viewModel.state.deloadMode },
                        set: viewModel.updateDeloadMode
                    )) {
                        ForEach(viewModel.deloadModeOptions, id: \.self) { option in
                            Text(viewModel.label(for: option)).tag(option)
                        }
                    }

                    Toggle("Warmup standardmäßig markieren", isOn: Binding(
                        get: { viewModel.state.defaultWarmupFlag },
                        set: viewModel.updateDefaultWarmupFlag
                    ))

                    Toggle("Timer darf Bildschirm aktiv halten", isOn: Binding(
                        get: { viewModel.state.timerKeepScreenOn },
                        set: viewModel.updateTimerKeepScreenOn
                    ))

                    Toggle("Gewichtshistorie über Kontexte teilen", isOn: Binding(
                        get: { viewModel.state.shareWeightHistoryAcrossContexts },
                        set: viewModel.updateShareWeightHistoryAcrossContexts
                    ))
                }

                Section("Rest-Timer") {
                    Toggle("Automatischer Rest-Timer", isOn: Binding(
                        get: { viewModel.state.autoRestTimerEnabled },
                        set: viewModel.updateAutoRestTimerEnabled
                    ))

                    Picker("Feste Dauer", selection: Binding(
                        get: { viewModel.state.defaultRestTimeSeconds },
                        set: viewModel.updateDefaultRestTimeSeconds
                    )) {
                        ForEach([30, 60, 90, 120, 180, 300, 600], id: \.self) { seconds in
                            Text(viewModel.restTimeLabel(seconds)).tag(seconds)
                        }
                    }
                    .disabled(!viewModel.state.autoRestTimerEnabled)

                    Text(
                        viewModel.state.autoRestTimerEnabled
                            ? "Nach passenden Arbeitssätzen läuft ein Countdown."
                            : "Deaktiviert: Nach dem Satz läuft die Zeit aufwärts."
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }

                Section("Hantel und Platten") {
                    Toggle("Plattenrechner aktiv", isOn: Binding(
                        get: { viewModel.state.plateCalculatorEnabled },
                        set: viewModel.updatePlateCalculatorEnabled
                    ))

                    if viewModel.state.plateCalculatorEnabled {
                        Picker("Hantelstange", selection: Binding(
                            get: { viewModel.state.barbellWeightKg },
                            set: viewModel.updateBarbellWeightKg
                        )) {
                            ForEach([20.0, 15.0, 10.0, 8.0, 2.5], id: \.self) { weight in
                                Text(viewModel.weightLabel(weight)).tag(weight)
                            }
                        }

                        Text("Verfügbare Platten")
                            .font(.subheadline.weight(.semibold))

                        ForEach([25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5], id: \.self) { plate in
                            Toggle(viewModel.weightLabel(plate), isOn: Binding(
                                get: { viewModel.state.availablePlates.contains(plate) },
                                set: { enabled in
                                    var plates = viewModel.state.availablePlates
                                    if enabled {
                                        plates.append(plate)
                                    } else {
                                        plates.removeAll { $0 == plate }
                                    }
                                    viewModel.updateAvailablePlates(plates)
                                }
                            ))
                        }
                    }
                }

                Section("Reminder") {
                    Toggle("Workout-Erinnerung", isOn: Binding(
                        get: { viewModel.state.reminderEnabled },
                        set: viewModel.setReminderEnabled
                    ))

                    if viewModel.state.reminderEnabled {
                        DatePicker(
                            "Uhrzeit",
                            selection: Binding(
                                get: { viewModel.reminderDate },
                                set: viewModel.updateReminderTime
                            ),
                            displayedComponents: .hourAndMinute
                        )

                        ForEach(viewModel.weekdayOptions, id: \.self) { day in
                            Toggle(viewModel.label(for: day), isOn: Binding(
                                get: { viewModel.state.reminderDays.contains(day) },
                                set: { viewModel.setReminderDay(day, enabled: $0) }
                            ))
                        }
                    }
                }

                Section("Backup") {
                    Button("Backup exportieren") {
                        viewModel.startBackupExport()
                    }

                    Button("Backup importieren") {
                        viewModel.isImportingBackup = true
                    }

                    Button("Lokale Trainingsdaten zurücksetzen", role: .destructive) {
                        viewModel.isConfirmingReset = true
                    }

                    if let date = viewModel.recoveryDate {
                        Text("Wiederherstellungspunkt: \(date.formatted(date: .abbreviated, time: .shortened))")
                            .font(.footnote.weight(.semibold))

                        if let summary = viewModel.recoverySummary {
                            Text(summary)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }

                        Button("Vorherige Trainingsdaten wiederherstellen") {
                            viewModel.requestRestoreLatestRecovery()
                        }
                    } else {
                        Text("Kein Wiederherstellungspunkt verfügbar")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }

                    if let date = viewModel.lastSuccessfulExportDate {
                        Text("Letzter erfolgreicher Export: \(date.formatted(date: .abbreviated, time: .shortened))")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    } else {
                        Text("Noch kein erfolgreicher Export")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }

                    Toggle("An Backup erinnern", isOn: Binding(
                        get: { viewModel.state.backupReminderEnabled },
                        set: viewModel.updateBackupReminderEnabled
                    ))

                    if viewModel.state.backupReminderDue {
                        Text("Ein neuer Export ist fällig.")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.orange)
                    }
                }

                Section("Diagnostics") {
                    Toggle("Diagnostik in Reports beilegen", isOn: Binding(
                        get: { viewModel.state.betaDiagnosticsOptIn },
                        set: viewModel.updateBetaDiagnosticsOptIn
                    ))

                    TextField("Kurzbeschreibung", text: $viewModel.incidentSummary, axis: .vertical)
                        .textInputAutocapitalization(.sentences)

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Details")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.secondary)

                        TextEditor(text: $viewModel.incidentDetails)
                            .frame(minHeight: 120)
                    }

                    Button("Incident-Report erstellen") {
                        viewModel.createIncidentReport()
                    }
                }

                Section("Build") {
                    LabeledContent("Shared-Status", value: SharedBootstrap.statusLine)
                    LabeledContent("Version", value: "\(viewModel.state.versionName) (\(viewModel.state.versionCode))")
                }
            }
            .navigationTitle("Einstellungen")
            .overlay {
                if viewModel.isBusy {
                    ProgressView("Bitte warten …")
                        .padding(20)
                        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                }
            }
        }
        .fileImporter(
            isPresented: $viewModel.isImportingBackup,
            allowedContentTypes: [.json],
            allowsMultipleSelection: false
        ) { result in
            viewModel.prepareBackupImport(from: result)
        }
        .fileExporter(
            isPresented: $viewModel.isExportingBackup,
            document: viewModel.exportDocument,
            contentType: .json,
            defaultFilename: viewModel.exportDocument.fileName
        ) { result in
            switch result {
            case .success:
                viewModel.recordSuccessfulBackupExport()
            case .failure(let error):
                viewModel.activeAlert = SettingsAlert(
                    title: "Export fehlgeschlagen",
                    message: error.localizedDescription
                )
            }
        }
        .sheet(item: $viewModel.shareItem) { item in
            ShareSheet(items: [item.url])
        }
        .alert(item: $viewModel.activeAlert) { alert in
            Alert(
                title: Text(alert.title),
                message: Text(alert.message),
                dismissButton: .default(Text("OK"))
            )
        }
        .confirmationDialog(
            "Lokale Trainingsdaten zurücksetzen?",
            isPresented: $viewModel.isConfirmingReset,
            titleVisibility: .visible
        ) {
            Button("Trainingsdaten zurücksetzen", role: .destructive) {
                viewModel.resetUserData()
            }
            Button("Abbrechen", role: .cancel) { }
        } message: {
            Text("Übungen, Pläne und Trainings werden aus dem lokalen iOS-Speicher gelöscht.")
        }
        .confirmationDialog(
            "Backup importieren?",
            isPresented: $viewModel.isConfirmingBackupImport,
            titleVisibility: .visible
        ) {
            Button("Backup importieren", role: .destructive) {
                viewModel.confirmBackupImport()
            }
            Button("Abbrechen", role: .cancel) {
                viewModel.cancelBackupImport()
            }
        } message: {
            Text(viewModel.pendingImportSummary ?? "Die Backup-Datei wurde geprüft.")
        }
        .confirmationDialog(
            "Vorherige Trainingsdaten wiederherstellen?",
            isPresented: $viewModel.isConfirmingRecoveryRestore,
            titleVisibility: .visible
        ) {
            Button("Wiederherstellung durchführen", role: .destructive) {
                viewModel.confirmRestoreLatestRecovery()
            }
            Button("Abbrechen", role: .cancel) {
                viewModel.cancelRecoveryRestore()
            }
        } message: {
            Text("Der aktuelle Trainingsstand wird zuvor wieder als neuer Wiederherstellungspunkt gesichert.")
        }
    }
}
