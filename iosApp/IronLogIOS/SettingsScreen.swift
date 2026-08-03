import SwiftUI
import UniformTypeIdentifiers

struct SettingsScreen: View {
    @StateObject private var viewModel = IOSSettingsViewModel()

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

                    Toggle("Dynamische Farben", isOn: Binding(
                        get: { viewModel.state.useDynamicColor },
                        set: viewModel.updateUseDynamicColor
                    ))

                    Toggle("Reduzierte Bewegung", isOn: Binding(
                        get: { viewModel.state.reducedMotion },
                        set: viewModel.updateReducedMotion
                    ))
                }

                Section("Training") {
                    Picker("Intensitaet", selection: Binding(
                        get: { viewModel.state.intensitySystem },
                        set: viewModel.updateIntensitySystem
                    )) {
                        ForEach(viewModel.intensitySystemOptions, id: \.self) { option in
                            Text(viewModel.label(for: option)).tag(option)
                        }
                    }

                    Toggle("Warmup standardmaessig markieren", isOn: Binding(
                        get: { viewModel.state.defaultWarmupFlag },
                        set: viewModel.updateDefaultWarmupFlag
                    ))

                    Toggle("Timer darf Bildschirm aktiv halten", isOn: Binding(
                        get: { viewModel.state.timerKeepScreenOn },
                        set: viewModel.updateTimerKeepScreenOn
                    ))
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

                    Button("Lokale Backup-Zwischenstaende loeschen", role: .destructive) {
                        viewModel.resetUserData()
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
            viewModel.importBackup(from: result)
        }
        .fileExporter(
            isPresented: $viewModel.isExportingBackup,
            document: viewModel.exportDocument,
            contentType: .json,
            defaultFilename: viewModel.exportDocument.fileName
        ) { result in
            if case .failure(let error) = result {
                viewModel.activeAlert = SettingsAlert(title: "Export fehlgeschlagen", message: error.localizedDescription)
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
    }
}
