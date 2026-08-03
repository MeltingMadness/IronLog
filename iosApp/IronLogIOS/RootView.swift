import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            PlaceholderScreen(
                title: "Dashboard",
                subtitle: "Trainingsueberblick und Einstieg"
            )
            .tabItem {
                Label("Dashboard", systemImage: "house")
            }

            PlaceholderScreen(
                title: "Workout",
                subtitle: "Aktive Session und Logging"
            )
            .tabItem {
                Label("Workout", systemImage: "bolt.heart")
            }

            PlaceholderScreen(
                title: "Verlauf",
                subtitle: "Historie, PRs und Analysen"
            )
            .tabItem {
                Label("Verlauf", systemImage: "clock.arrow.circlepath")
            }

            PlaceholderScreen(
                title: "Plaene",
                subtitle: "Trainings- und Meta-Plaene"
            )
            .tabItem {
                Label("Plaene", systemImage: "list.bullet.rectangle")
            }

            SettingsScreen()
            .tabItem {
                Label("Settings", systemImage: "gearshape")
            }
        }
    }
}

private struct PlaceholderScreen: View {
    let title: String
    let subtitle: String

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [
                        Color(red: 0.94, green: 0.68, blue: 0.26),
                        Color(red: 0.95, green: 0.93, blue: 0.86)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                VStack(alignment: .leading, spacing: 16) {
                    Text(title)
                        .font(.largeTitle.bold())
                    Text(subtitle)
                        .font(.body)
                        .foregroundStyle(.secondary)
                    Text("Die native SwiftUI-Huelle ist angelegt und fuer die Anbindung an den Shared-Kern vorbereitet.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            }
            .navigationTitle(title)
        }
    }
}
