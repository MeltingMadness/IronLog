import Charts
import SwiftUI

enum IOSExerciseMetric: String, CaseIterable, Identifiable {
    case e1rm
    case weight
    case reps
    case volume

    var id: String { rawValue }

    var title: String {
        switch self {
        case .e1rm: return "e1RM"
        case .weight: return "Gewicht"
        case .reps: return "Wiederholungen"
        case .volume: return "Volumen"
        }
    }

    var shortTitle: String {
        switch self {
        case .e1rm: return "e1RM"
        case .weight: return "Gewicht"
        case .reps: return "Wdh."
        case .volume: return "Volumen"
        }
    }
}

private enum IOSRecordFilter: String, CaseIterable, Identifiable {
    case all
    case maxWeight = "MAX_WEIGHT"
    case maxReps = "MAX_REPS"
    case maxE1rm = "MAX_E1RM"
    case maxVolume = "MAX_VOLUME"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: return "Alle Rekorde"
        default: return ilRecordTypeText(rawValue)
        }
    }
}

/// Exercise detail backed by the shared per-session analytics series.
struct IOSExerciseStatisticsScreen: View {
    let exerciseId: Int64

    @Environment(IOSTrainingStore.self) private var store
    @EnvironmentObject private var settings: IOSSettingsViewModel
    @State private var selectedMetric: IOSExerciseMetric = .e1rm
    @State private var recordFilter: IOSRecordFilter = .all

    private var weekStartsSunday: Bool {
        settings.state.weekStart.uppercased() == "SUNDAY"
    }

    private var analyticsExercise: ILExerciseAnalytics? {
        store.analytics?.exerciseStatistics.first(where: { $0.exerciseId == exerciseId })
    }

    private var exercise: ILExercise? {
        store.data?.exercises.first(where: { $0.id == exerciseId })
    }

    private var exerciseName: String {
        analyticsExercise?.exerciseName ?? exercise?.name ?? "Übung \(exerciseId)"
    }

    var body: some View {
        ScrollView {
            if let analyticsExercise {
                detailContent(analyticsExercise)
            } else {
                ContentUnavailableView {
                    Label("Keine Übungsstatistik", systemImage: "chart.xyaxis.line")
                } description: {
                    Text("Für diese Übung gibt es noch keine abgeschlossenen Arbeitssätze.")
                }
                .frame(maxWidth: .infinity, minHeight: 420)
                .padding(20)
            }
        }
        .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
        .navigationTitle(exerciseName)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: "\(settings.state.weekStart)-\(settings.state.unitSystem)") {
            store.refreshAnalytics(
                timeZoneId: TimeZone.current.identifier,
                weekStartsSunday: weekStartsSunday
            )
        }
    }

    @ViewBuilder
    private func detailContent(_ analyticsExercise: ILExerciseAnalytics) -> some View {
        let sessions = analyticsExercise.sessions
        let unitSystem = settings.state.unitSystem

        LazyVStack(alignment: .leading, spacing: 16) {
            exerciseSummary

            if sessions.count >= 2, analyticsExercise.lastWorkoutComparison != nil {
                IOSExerciseE1rmProgressionCard(
                    firstE1rmKg: analyticsExercise.firstE1rmKg,
                    latestE1rmKg: analyticsExercise.latestE1rmKg,
                    absoluteChangeKg: analyticsExercise.e1rmDeltaKg,
                    relativeChangePercent: analyticsExercise.e1rmDeltaPercent,
                    unitSystem: unitSystem
                )
            }

            IronLogCard(title: "Entwicklung", subtitle: "Einheit pro Einheit · \(selectedMetric.title)") {
                Picker("Metrik", selection: $selectedMetric) {
                    ForEach(IOSExerciseMetric.allCases) { metric in
                        Text(metric.shortTitle).tag(metric)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityLabel("Metrik für den Verlauf")

                if sessions.count < 2 {
                    Text("Mindestens zwei abgeschlossene Einheiten sind für einen Verlauf nötig.")
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 12)
                } else {
                    Chart {
                        ForEach(sessions) { session in
                            if let date = ilAnalyticsDate(session.date) {
                                LineMark(
                                    x: .value("Datum", date),
                                    y: .value(selectedMetric.title, metricValue(session))
                                )
                                .foregroundStyle(.orange)
                                .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))

                                PointMark(
                                    x: .value("Datum", date),
                                    y: .value(selectedMetric.title, metricValue(session))
                                )
                                .foregroundStyle(.orange)
                                .symbolSize(28)
                            }
                        }
                    }
                    .chartYScale(domain: .automatic(includesZero: selectedMetric == .reps || selectedMetric == .volume))
                    .chartXAxis {
                        AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                            AxisGridLine()
                            AxisValueLabel(format: .dateTime.month(.abbreviated).day())
                        }
                    }
                    .chartYAxis {
                        AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                            AxisGridLine()
                            AxisValueLabel {
                                if let number = value.as(Double.self) {
                                    Text(metricAxisValue(number, unitSystem: unitSystem))
                                }
                            }
                        }
                    }
                    .frame(minHeight: 230)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Verlauf \(selectedMetric.title)")
                    .accessibilityValue(
                        Text(sessions.map { "\(ilAnalyticsDateText($0.date, dateStyle: .short) ?? $0.date): \(metricText($0, unitSystem: unitSystem))" }.joined(separator: ", "))
                    )
                }
            }

            if let comparison = analyticsExercise.lastWorkoutComparison {
                IOSExerciseLastWorkoutComparisonCard(
                    comparison: comparison,
                    metric: selectedMetric,
                    unitSystem: unitSystem
                )
            }

            IOSExerciseRecordsCard(
                records: filteredRecords,
                unitSystem: unitSystem,
                filter: $recordFilter
            )

            IOSExerciseSessionHistoryCard(
                sessions: sessions,
                selectedMetric: selectedMetric,
                unitSystem: unitSystem
            )

            if !analyticsExercise.recentSets.isEmpty {
                IOSExerciseRecentSetsCard(
                    sets: analyticsExercise.recentSets,
                    unitSystem: unitSystem
                )
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var exerciseSummary: some View {
        IronLogCard(tone: .elevated) {
            VStack(alignment: .leading, spacing: 6) {
                Text(exerciseName)
                    .font(.title2.weight(.bold))
                if let exercise {
                    Text([exercise.primaryMuscleGroupDisplayName, exercise.categoryDisplayName].joined(separator: " · "))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Text("\(analyticsExercise?.sessions.count ?? 0) abgeschlossene Einheiten")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var filteredRecords: [ILPersonalRecord] {
        let records = store.data?.personalRecords
            .filter { $0.exerciseId == exerciseId }
            .filter { recordFilter == .all || $0.type.uppercased() == recordFilter.rawValue }
            .sorted { $0.achievedAt > $1.achievedAt } ?? []
        return records
    }

    private func metricValue(_ session: ILExerciseAnalyticsSession) -> Double {
        switch selectedMetric {
        case .e1rm: return session.maxE1rmKg
        case .weight: return session.maxWeightKg
        case .reps: return Double(session.maxReps)
        case .volume: return session.volumeKg
        }
    }

    private func metricText(_ session: ILExerciseAnalyticsSession, unitSystem: String) -> String {
        switch selectedMetric {
        case .e1rm: return ilWeightText(session.maxE1rmKg, unitSystem: unitSystem)
        case .weight: return ilWeightText(session.maxWeightKg, unitSystem: unitSystem)
        case .reps: return "\(session.maxReps) Wdh."
        case .volume: return ilVolumeText(session.volumeKg, unitSystem: unitSystem)
        }
    }

    private func metricAxisValue(_ value: Double, unitSystem: String) -> String {
        switch selectedMetric {
        case .reps: return value.formatted(.number.precision(.fractionLength(0...0)))
        case .e1rm, .weight, .volume: return ilWeightText(value, unitSystem: unitSystem)
        }
    }
}

private struct IOSExerciseRecordsCard: View {
    let records: [ILPersonalRecord]
    let unitSystem: String
    @Binding var filter: IOSRecordFilter

    var body: some View {
        IronLogCard(title: "Persönliche Rekorde", subtitle: records.isEmpty ? nil : "Auswahl nach Rekordtyp") {
            Picker("Rekordfilter", selection: $filter) {
                ForEach(IOSRecordFilter.allCases) { item in
                    Text(item.title).tag(item)
                }
            }
            .pickerStyle(.menu)

            if records.isEmpty {
                Text("Für diesen Filter gibt es noch keinen Rekord.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(records) { record in
                    HStack(alignment: .firstTextBaseline) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(ilRecordTypeText(record.type))
                                .font(.body.weight(.semibold))
                            Text(record.achievedDate.formatted(date: .abbreviated, time: .omitted))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 12)
                        Text(ilRecordValueText(type: record.type, value: record.value, unitSystem: unitSystem))
                            .font(.body.weight(.semibold))
                    }
                    .padding(.vertical, 3)
                }
            }
        }
    }
}

private struct IOSExerciseE1rmProgressionCard: View {
    let firstE1rmKg: Double
    let latestE1rmKg: Double
    let absoluteChangeKg: Double
    let relativeChangePercent: Double
    let unitSystem: String

    var body: some View {
        IronLogCard(title: "1RM-Entwicklung", subtitle: "Geschätzt nach der Epley-Formel") {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 12) {
                    metric(title: "Erstes 1RM", value: ilWeightText(firstE1rmKg, unitSystem: unitSystem))
                    metric(title: "Aktuelles 1RM", value: ilWeightText(latestE1rmKg, unitSystem: unitSystem))
                }

                HStack(spacing: 12) {
                    metric(
                        title: "Steigerung",
                        value: ilWeightText(absoluteChangeKg, unitSystem: unitSystem, signed: true)
                    )
                    metric(title: "Steigerung (%)", value: percentText(relativeChangePercent))
                }
            }
        }
    }

    private func metric(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
                .font(.body.weight(.semibold))
                .monospacedDigit()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func percentText(_ value: Double) -> String {
        let sign = value > 0 ? "+" : ""
        return "\(sign)\(value.formatted(.number.precision(.fractionLength(0...1)))) %"
    }
}

private struct IOSExerciseLastWorkoutComparisonCard: View {
    let comparison: ILExerciseWorkoutComparison
    let metric: IOSExerciseMetric
    let unitSystem: String

    private var deltaIsPositive: Bool {
        switch metric {
        case .e1rm: return comparison.e1rmDeltaKg >= 0
        case .weight: return comparison.weightDeltaKg >= 0
        case .reps: return comparison.repsDelta >= 0
        case .volume: return comparison.volumeDeltaKg >= 0
        }
    }

    var body: some View {
        IronLogCard(title: "Vergleich zum vorherigen passenden Training", tone: .muted) {
            VStack(alignment: .leading, spacing: 8) {
                comparisonRow(
                    label: "Vorheriges",
                    date: comparison.previous.completedAtEpochMillis,
                    value: metricText(comparison.previous)
                )
                comparisonRow(
                    label: "Letztes",
                    date: comparison.latest.completedAtEpochMillis,
                    value: metricText(comparison.latest),
                    emphasized: true
                )
                Text("Veränderung: \(metricDeltaText)")
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(deltaIsPositive ? .green : .red)
            }
        }
    }

    private func comparisonRow(
        label: String,
        date: Int64,
        value: String,
        emphasized: Bool = false
    ) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(label) · \(ilAnalyticsDateTimeText(epochMillis: date))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Text(value)
                    .font(.body.weight(emphasized ? .semibold : .regular))
            }
            Spacer(minLength: 8)
        }
    }

    private func metricText(_ session: ILExerciseAnalyticsSession) -> String {
        switch metric {
        case .e1rm: return ilWeightText(session.maxE1rmKg, unitSystem: unitSystem)
        case .weight: return ilWeightText(session.maxWeightKg, unitSystem: unitSystem)
        case .reps: return "\(session.maxReps) Wdh."
        case .volume: return ilVolumeText(session.volumeKg, unitSystem: unitSystem)
        }
    }

    private var metricDeltaText: String {
        switch metric {
        case .e1rm:
            return ilWeightText(comparison.e1rmDeltaKg, unitSystem: unitSystem, signed: true)
        case .weight:
            return ilWeightText(comparison.weightDeltaKg, unitSystem: unitSystem, signed: true)
        case .reps:
            return signedCount(comparison.repsDelta) + " Wdh."
        case .volume:
            return ilVolumeText(comparison.volumeDeltaKg, unitSystem: unitSystem, signed: true)
        }
    }

    private func signedCount(_ value: Int) -> String {
        let sign = value > 0 ? "+" : ""
        return "\(sign)\(value)"
    }
}

private struct IOSExerciseSessionHistoryCard: View {
    let sessions: [ILExerciseAnalyticsSession]
    let selectedMetric: IOSExerciseMetric
    let unitSystem: String

    var body: some View {
        IronLogCard(title: "Einheiten", subtitle: "Quellwerte pro abgeschlossener Einheit") {
            if sessions.isEmpty {
                Text("Noch keine Einheiten verfügbar.")
                    .font(.body)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(sessions.reversed()) { session in
                    VStack(alignment: .leading, spacing: 5) {
                        HStack {
                            Text(ilAnalyticsDateText(session.date) ?? session.date)
                                .font(.body.weight(.semibold))
                            Spacer()
                            Text(metricText(session))
                                .font(.body.weight(.semibold))
                        }
                        Text("\(ilWeightText(session.maxWeightKg, unitSystem: unitSystem)) · \(session.maxReps) Wdh. · \(ilWeightText(session.maxE1rmKg, unitSystem: unitSystem)) e1RM · \(ilVolumeText(session.volumeKg, unitSystem: unitSystem))")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 5)
                    if session.id != sessions.first?.id {
                        Divider()
                    }
                }
            }
        }
    }

    private func metricText(_ session: ILExerciseAnalyticsSession) -> String {
        switch selectedMetric {
        case .e1rm: return ilWeightText(session.maxE1rmKg, unitSystem: unitSystem)
        case .weight: return ilWeightText(session.maxWeightKg, unitSystem: unitSystem)
        case .reps: return "\(session.maxReps) Wdh."
        case .volume: return ilVolumeText(session.volumeKg, unitSystem: unitSystem)
        }
    }
}

private struct IOSExerciseRecentSetsCard: View {
    let sets: [ILExerciseSetStatistics]
    let unitSystem: String

    var body: some View {
        IronLogCard(title: "Letzte Sätze", tone: .muted) {
            ForEach(sets.prefix(12)) { set in
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    Text(ilAnalyticsDateTimeText(epochMillis: set.completedAtEpochMillis))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    Text("\(setTypeText(set.setType)) · \(iosSetValueText(weightKg: set.weightKg, reps: set.reps, unitSystem: unitSystem))")
                        .font(.body.weight(.medium))
                        .multilineTextAlignment(.trailing)
                }
                .padding(.vertical, 3)
            }
        }
    }

    private func setTypeText(_ raw: String) -> String {
        switch raw.uppercased() {
        case "NORMAL": return "Arbeitssatz"
        case "WARMUP": return "Aufwärmen"
        case "DROP_SET": return "Dropsatz"
        case "FAILURE": return "Failure-Satz"
        default: return raw
        }
    }
}
