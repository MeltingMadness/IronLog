import Charts
import SwiftUI

private let statsInk = Color(red: 11 / 255, green: 13 / 255, blue: 18 / 255)

/// Hero card of the Liquid Glass exercise statistics: latest value of the
/// selected metric, change since the first session as a tinted label, the curve
/// with area and end point, and the metric switch.
struct IOSExerciseGlassHero: View {
    let sessions: [ILExerciseAnalyticsSession]
    @Binding var metric: IOSExerciseMetric
    let unitSystem: String

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let ink = colorScheme == .dark ? Color.white : statsInk
        let points = sessions.compactMap { session in
            ilAnalyticsDate(session.date).map { (date: $0, value: value(session)) }
        }
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(metric == .e1rm ? "GESCHÄTZTES 1RM" : metric.title.uppercased())
                        .font(.caption.weight(.bold))
                        .tracking(0.8)
                        .foregroundStyle(.secondary)
                    let parts = split(points.last.map { text($0.value) } ?? "–")
                    HStack(alignment: .lastTextBaseline, spacing: 4) {
                        Text(parts.number)
                            .font(.system(size: 52, weight: .bold))
                            .tracking(-2)
                            .monospacedDigit()
                            .minimumScaleFactor(0.6)
                            .lineLimit(1)
                        Text(parts.unit)
                            .font(.system(size: 20))
                            .foregroundStyle(ink.opacity(0.7))
                    }
                }
                Spacer(minLength: 8)
                if let first = points.first, let last = points.last, points.count >= 2 {
                    Text(deltaText(from: first.value, to: last.value))
                        .font(.footnote.weight(.heavy))
                        .monospacedDigit()
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .liquidGlass(.tint, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .padding(.bottom, 10)
                }
            }

            if points.count >= 2, let first = points.first, let last = points.last {
                Chart {
                    ForEach(points, id: \.date) { point in
                        AreaMark(x: .value("Datum", point.date), y: .value(metric.title, point.value))
                            .foregroundStyle(ink.opacity(0.10))
                        LineMark(x: .value("Datum", point.date), y: .value(metric.title, point.value))
                            .foregroundStyle(ink)
                            .lineStyle(StrokeStyle(lineWidth: 3, lineCap: .round, lineJoin: .round))
                    }
                    PointMark(x: .value("Datum", last.date), y: .value(metric.title, last.value))
                        .foregroundStyle(ink)
                        .symbolSize(90)
                }
                .chartYScale(domain: .automatic(includesZero: false))
                .chartXAxis(.hidden)
                .chartYAxis {
                    AxisMarks(values: .automatic(desiredCount: 3)) { _ in
                        AxisGridLine().foregroundStyle(ink.opacity(0.10))
                    }
                }
                .frame(height: 140)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Verlauf \(metric.title) von \(text(first.value)) auf \(text(last.value))")
                HStack {
                    Text("\(first.date.formatted(.dateTime.day().month(.twoDigits))) · \(text(first.value))")
                    Spacer()
                    Text("\(last.date.formatted(.dateTime.day().month(.twoDigits))) · \(text(last.value))")
                }
                .font(.caption)
                .monospacedDigit()
                .foregroundStyle(.secondary)
            } else {
                Text("Mindestens zwei abgeschlossene Einheiten sind für einen Verlauf nötig.")
                    .foregroundStyle(.secondary)
            }

            IOSWorkoutGlassIntensityBar(
                options: IOSExerciseMetric.allCases.map(\.glassTitle),
                selection: Binding(
                    get: { metric.glassTitle },
                    set: { title in
                        if let chosen = IOSExerciseMetric.allCases.first(where: { $0.glassTitle == title }) { metric = chosen }
                    }
                )
            )
            .accessibilityLabel("Metrik für den Verlauf")
        }
        .padding(20)
        .liquidGlass(.strong, in: RoundedRectangle(cornerRadius: 34, style: .continuous))
    }

    private func value(_ session: ILExerciseAnalyticsSession) -> Double {
        switch metric {
        case .e1rm: return session.maxE1rmKg
        case .weight: return session.maxWeightKg
        case .reps: return Double(session.maxReps)
        case .volume: return session.volumeKg
        }
    }

    private func text(_ value: Double) -> String {
        switch metric {
        case .reps: return "\(Int(value)) Wdh."
        case .volume: return ilVolumeText(value.rounded(), unitSystem: unitSystem)
        case .e1rm, .weight: return ilWeightText(value, unitSystem: unitSystem)
        }
    }

    private func split(_ text: String) -> (number: String, unit: String) {
        let parts = text.split(separator: " ")
        guard parts.count >= 2 else { return (text, "") }
        return (parts.dropLast().joined(separator: " "), String(parts.last!))
    }

    private func deltaText(from first: Double, to last: Double) -> String {
        let delta = last - first
        let absolute: String
        switch metric {
        case .reps: absolute = (delta > 0 ? "+" : "") + "\(Int(delta)) Wdh."
        case .volume: absolute = ilVolumeText(delta.rounded(), unitSystem: unitSystem, signed: true)
        case .e1rm, .weight: absolute = ilWeightText(delta, unitSystem: unitSystem, signed: true)
        }
        guard first > 0 else { return absolute }
        let percent = Int((delta / first * 100).rounded())
        return "\(absolute) · \(percent > 0 ? "+" : "")\(percent) %"
    }
}

extension IOSExerciseMetric {
    /// Short labels for the glass segment bar.
    var glassTitle: String {
        switch self {
        case .e1rm: return "1RM"
        case .weight: return "Gewicht"
        case .reps: return "Wdh."
        case .volume: return "Volumen"
        }
    }
}

/// Four equal record tiles (best value per record type) in a 2 × 2 grid.
struct IOSExerciseGlassRecordTiles: View {
    let records: [ILPersonalRecord]
    let unitSystem: String

    var body: some View {
        let best = Dictionary(grouping: records, by: { $0.type.uppercased() }).mapValues { $0.map(\.value).max() ?? 0 }
        let tiles: [(String, String)] = [
            ("BESTES 1RM", best["MAX_E1RM"].map { ilWeightText($0, unitSystem: unitSystem) } ?? "–"),
            ("MAX. GEWICHT", best["MAX_WEIGHT"].map { ilWeightText($0, unitSystem: unitSystem) } ?? "–"),
            ("MAX. WDH", best["MAX_REPS"].map { "\(Int($0))" } ?? "–"),
            ("MAX. VOLUMEN", best["MAX_VOLUME"].map { ilVolumeText($0.rounded(), unitSystem: unitSystem) } ?? "–")
        ]
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
            ForEach(tiles, id: \.0) { label, value in
                VStack(alignment: .leading, spacing: 4) {
                    Text(label)
                        .font(.caption2.weight(.bold))
                        .tracking(0.8)
                        .foregroundStyle(.secondary)
                    Text(value)
                        .font(.system(size: 24, weight: .bold))
                        .monospacedDigit()
                        .lineLimit(1)
                        .minimumScaleFactor(0.7)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .liquidGlass(in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                .accessibilityElement(children: .combine)
            }
        }
    }
}
