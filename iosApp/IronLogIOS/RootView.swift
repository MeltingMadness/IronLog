import SwiftUI

struct RootView: View {
    @Environment(\.scenePhase) private var scenePhase
    @State private var training = IOSTrainingStore()
    @StateObject private var settings = IOSSettingsViewModel()

    var body: some View {
        TabView(selection: $training.selectedTab) {
            trainingContent { IOSDashboardScreen() }
                .tabItem { Label("Start", systemImage: "house") }.tag(0)
            trainingContent { NavigationStack { IOSWorkoutScreen() } }
                .tabItem { Label("Training", systemImage: "dumbbell") }.tag(1)
            trainingContent { IOSHistoryScreen() }
                .tabItem { Label("Verlauf", systemImage: "clock.arrow.circlepath") }.tag(2)
            trainingContent { IOSPlansScreen(unitSystem: settings.state.unitSystem) }
                .tabItem { Label("Pläne", systemImage: "list.bullet.rectangle") }.tag(3)
            SettingsScreen()
                .tabItem { Label("Einstellungen", systemImage: "gearshape") }.tag(4)
        }
        .environment(training)
        .environmentObject(settings)
        .task {
            // Central deload wiring for every workout start: the settings mode is authoritative,
            // and the store sends an explicit `false` while no mode is active.
            training.deloadModeActive = settings.state.deloadMode != "NONE"
            if training.deloadModeActive {
                _ = await training.markActiveSessionDeload()
            }
            if training.data != nil {
                await training.command("progression.generate")
            }
        }
        .onChange(of: settings.state.deloadMode) { _, newValue in
            let active = newValue != "NONE"
            training.deloadModeActive = active
            // Switching the mode on must mark a session that is already running. Switching it off
            // must not rewrite history: the durable store only ever escalates to `true`.
            if active {
                Task { _ = await training.markActiveSessionDeload() }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                training.refreshAnalytics(
                    timeZoneId: TimeZone.current.identifier,
                    weekStartsSunday: settings.state.weekStart == "SUNDAY"
                )
            }
        }
        .environment(\.ironLogAppearance, IronLogAppearance(rawPreference: settings.state.appearanceStyle))
        .ironLogTheme(
            IronLogTheme(
                schemeName: settings.state.themeScheme,
                reducedMotion: settings.state.reducedMotion
            )
        )
        .preferredColorScheme(settings.state.themeMode == "DARK" ? .dark : settings.state.themeMode == "LIGHT" ? .light : nil)
        .safeAreaInset(edge: .top) {
            if let error = training.errorMessage, training.data != nil {
                HStack(alignment: .top, spacing: 12) {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(.callout)
                    Spacer(minLength: 0)
                    Button { training.errorMessage = nil } label: {
                        Image(systemName: "xmark.circle.fill")
                    }
                    .accessibilityLabel("Fehlermeldung schließen")
                }
                .padding()
                .background(.regularMaterial)
            }
        }
    }

    @ViewBuilder
    private func trainingContent<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        if training.data != nil {
            content()
        } else {
            ContentUnavailableView {
                Label("Trainingsdaten nicht verfügbar", systemImage: "externaldrive.badge.exclamationmark")
            } description: {
                Text(training.errorMessage ?? "Die Trainingsdaten werden geladen.")
            } actions: {
                Button("Backup in Einstellungen wiederherstellen") { training.selectedTab = 4 }
            }
        }
    }
}
