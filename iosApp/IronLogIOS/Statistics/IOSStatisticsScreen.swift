import SwiftUI

/// Full statistics overview. The exercise rows and trend bins are emitted by
/// shared analytics; this screen only filters and formats them.
struct IOSStatisticsScreen: View {
    @Environment(IOSTrainingStore.self) private var store
    @EnvironmentObject private var settings: IOSSettingsViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var searchText = ""
    @State private var muscleFilter = "ALL"
    @State private var selectedMuscleVolumeWeek: ILTrainingWeeklyMuscleVolume?
    @State private var isMuscleVolumeLoading = false
    @State private var muscleVolumeError: String?

    private var weekStartsSunday: Bool {
        settings.state.weekStart.uppercased() == "SUNDAY"
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                if let analytics = store.analytics {
                    statisticsContent(analytics)
                } else {
                    ContentUnavailableView {
                        Label("Statistiken nicht verfügbar", systemImage: "chart.xyaxis.line")
                    } description: {
                        Text(store.errorMessage ?? "Die Trainingsdaten werden noch ausgewertet.")
                    }
                    .frame(maxWidth: .infinity, minHeight: 420)
                    .padding(20)
                }
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("Statistiken")
            .navigationBarTitleDisplayMode(.large)
            .searchable(text: $searchText, prompt: "Übungen suchen")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Schließen") { dismiss() }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    filterMenu
                }
            }
            .refreshable {
                store.refreshAnalytics(
                    timeZoneId: TimeZone.current.identifier,
                    weekStartsSunday: weekStartsSunday
                )
                loadMuscleVolumeWeek(
                    selectedMuscleVolumeWeek?.weekStart ?? store.analytics?.currentWeekStart
                )
            }
            .navigationDestination(for: Int64.self) { exerciseId in
                IOSExerciseStatisticsScreen(exerciseId: exerciseId)
            }
        }
        .task(id: settings.state.weekStart) {
            store.refreshAnalytics(
                timeZoneId: TimeZone.current.identifier,
                weekStartsSunday: weekStartsSunday
            )
            selectedMuscleVolumeWeek = nil
            muscleVolumeError = nil
            loadMuscleVolumeWeek(store.analytics?.currentWeekStart)
        }
        .onChange(of: store.analytics?.currentWeekStart) { _, currentWeekStart in
            guard selectedMuscleVolumeWeek == nil else { return }
            loadMuscleVolumeWeek(currentWeekStart)
        }
        .onChange(of: store.data) { _, _ in
            // Keep the selected historical week in sync after a finished
            // workout, set edit, or imported snapshot even when the week start
            // itself did not change.
            loadMuscleVolumeWeek(
                selectedMuscleVolumeWeek?.weekStart ?? store.analytics?.currentWeekStart
            )
        }
    }

    @ViewBuilder
    private func statisticsContent(_ analytics: ILTrainingAnalytics) -> some View {
        let unitSystem = settings.state.unitSystem
        let weekly = selectedMuscleVolumeWeek

        LazyVStack(alignment: .leading, spacing: 16) {
            IOSStatisticsSummary(
                analytics: analytics,
                trend: store.readiness?.trainingTrend
            )

            IOSWeeklyVolumeTrendCard(
                bins: analytics.weeklyVolume,
                unitSystem: unitSystem
            )

            IronLogWeeklyMuscleVolumeCard(
                title: "Muskelvolumen",
                subtitle: weekly.map { $0.weekStart == $0.currentWeekStart
                    ? "Vollständige aktuelle Woche · MEV · MAV · MRV"
                    : "Vollständige ausgewählte Woche · MEV · MAV · MRV"
                } ?? "Vollständige aktuelle Woche · MEV · MAV · MRV",
                rows: (weekly?.volumes ?? analytics.muscleVolumes).map { volume in
                    IronLogWeeklyMuscleVolume(
                        id: volume.id,
                        title: ilMuscleDisplayName(volume.muscleGroup),
                        weeklySets: volume.weeklySets,
                        minimumSets: volume.mev,
                        targetSets: volume.mav,
                        maximumSets: volume.mrv
                    )
                },
                initiallyExpanded: true,
                emptyMessage: "Noch keine Muskelvolumenwerte verfügbar",
                weekStart: weekly?.weekStart ?? analytics.currentWeekStart,
                currentWeekStart: weekly?.currentWeekStart ?? analytics.currentWeekStart,
                completedWorkoutCount: weekly?.completedWorkoutCount ?? analytics.workoutsThisWeek,
                isLoading: isMuscleVolumeLoading,
                errorMessage: muscleVolumeError,
                onRetry: {
                    loadMuscleVolumeWeek(weekly?.weekStart ?? analytics.currentWeekStart)
                },
                onPreviousWeek: {
                    guard let previous = ilAnalyticsShiftedWeekStart(
                        weekly?.weekStart ?? analytics.currentWeekStart,
                        weeks: -1
                    ) else { return }
                    loadMuscleVolumeWeek(previous)
                },
                onNextWeek: {
                    guard let next = ilAnalyticsShiftedWeekStart(
                        weekly?.weekStart ?? analytics.currentWeekStart,
                        weeks: 1
                    ) else { return }
                    loadMuscleVolumeWeek(next)
                }
            )

            Text("Übungen")
                .font(.title2.weight(.bold))
                .padding(.top, 4)

            let exercises = filteredExercises(analytics)
            if exercises.isEmpty {
                Text("Keine Übung mit dieser Auswahl gefunden.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 16)
            } else {
                ForEach(exercises) { exercise in
                    NavigationLink(value: exercise.exerciseId) {
                        IOSExerciseStatisticsRow(
                            exercise: exercise,
                            muscleName: muscleName(for: exercise.exerciseId),
                            unitSystem: unitSystem
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func loadMuscleVolumeWeek(_ requestedWeekStart: String?) {
        guard let requestedWeekStart else { return }
        isMuscleVolumeLoading = true
        muscleVolumeError = nil

        let result = store.weeklyMuscleVolume(
            for: requestedWeekStart,
            timeZoneId: TimeZone.current.identifier,
            weekStartsSunday: weekStartsSunday
        )
        selectedMuscleVolumeWeek = result
        if result == nil {
            muscleVolumeError = store.weeklyMuscleVolumeError ?? "Wochenvolumen konnte nicht geladen werden."
        }
        isMuscleVolumeLoading = false
    }

    private func filteredExercises(_ analytics: ILTrainingAnalytics) -> [ILExerciseAnalytics] {
        let normalizedQuery = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return analytics.exerciseStatistics.filter { exercise in
            let name = exercise.exerciseName ?? exerciseName(for: exercise.exerciseId)
            let matchesSearch = normalizedQuery.isEmpty || name.lowercased().contains(normalizedQuery)
            let matchesMuscle = muscleFilter == "ALL" || muscleName(for: exercise.exerciseId) == muscleFilter
            return matchesSearch && matchesMuscle
        }
    }

    private func muscleName(for exerciseId: Int64) -> String {
        store.data?.exercises.first(where: { $0.id == exerciseId })?.primaryMuscleGroupDisplayName ?? ""
    }

    private func exerciseName(for exerciseId: Int64) -> String {
        store.data?.exercises.first(where: { $0.id == exerciseId })?.name ?? "Übung \(exerciseId)"
    }

    private var filterMenu: some View {
        Menu {
            Button {
                muscleFilter = "ALL"
            } label: {
                Label("Alle Muskeln", systemImage: muscleFilter == "ALL" ? "checkmark" : "")
            }

            ForEach(availableMuscles, id: \.self) { muscle in
                Button {
                    muscleFilter = muscle
                } label: {
                    Label(muscle, systemImage: muscleFilter == muscle ? "checkmark" : "")
                }
            }
        } label: {
            Label("Filter", systemImage: "line.3.horizontal.decrease.circle")
        }
        .accessibilityLabel("Übungsfilter")
    }

    private var availableMuscles: [String] {
        let muscles = store.data?.exercises
            .filter { !$0.isArchived }
            .map(\.primaryMuscleGroupDisplayName) ?? []
        return Array(Set(muscles)).sorted()
    }
}

private struct IOSStatisticsSummary: View {
    let analytics: ILTrainingAnalytics
    let trend: ILTrainingTrendAssessment?

    var body: some View {
        IronLogCard(
            title: "Überblick",
            subtitle: "Trainingstrend: \(ilTrendStatusText(trend?.status ?? .insufficientData))"
        ) {
            HStack(alignment: .center, spacing: 16) {
                IronLogMetricRing(
                    title: "Trainingstrend",
                    value: ringValue,
                    progress: ringScore.map { Double($0) / 100.0 },
                    unit: ringScore == nil ? nil : "/100",
                    accent: readinessAccent,
                    unknownLabel: "Nicht genug Daten"
                )

                VStack(alignment: .leading, spacing: 10) {
                    summaryMetric("Woche", ilCount(analytics.workoutsThisWeek, "Training", "Trainings"))
                    summaryMetric("Monat", ilCount(analytics.workoutsThisMonth, "Training", "Trainings"))
                    if let last = ilAnalyticsDateText(analytics.lastSessionDate) {
                        summaryMetric("Letztes Training", last)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var ringScore: Int? { trend?.trainingIndex }

    private var ringValue: String? { ringScore.map { String($0) } }

    private var readinessAccent: IronLogTheme.Accent {
        switch trend?.status ?? .insufficientData {
        case .singleExerciseDecline, .multipleExerciseDecline: return .warning
        case .noNotableStrain: return .success
        case .insufficientData, .unknown: return .secondary
        }
    }

    private func summaryMetric(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.body.weight(.semibold))
        }
    }
}

private struct IOSExerciseStatisticsRow: View {
    let exercise: ILExerciseAnalytics
    let muscleName: String
    let unitSystem: String

    private var latest: ILExerciseAnalyticsSession? { exercise.sessions.last }

    var body: some View {
        IronLogCard(tone: .standard) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(exercise.exerciseName ?? "Übung \(exercise.exerciseId)")
                        .font(.headline)
                        .foregroundStyle(.primary)
                    Text([muscleName, ilCount(exercise.sessions.count, "Einheit", "Einheiten")].filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                if let latest {
                    VStack(alignment: .trailing, spacing: 4) {
                        Text(ilWeightText(latest.maxE1rmKg, unitSystem: unitSystem))
                            .font(.body.weight(.semibold))
                        Text("e1RM zuletzt")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
