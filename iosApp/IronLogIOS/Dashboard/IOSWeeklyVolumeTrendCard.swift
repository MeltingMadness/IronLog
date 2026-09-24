import Charts
import SwiftUI

/// Eight fixed calendar-week bins from shared analytics.
///
/// The y-axis converts the source kilograms to the selected display unit while
/// preserving the zero bins and calendar dates, so a quiet week is still
/// visible instead of being dropped from the series.
struct IOSWeeklyVolumeTrendCard: View {
    let bins: [ILTrainingVolumeBin]
    let unitSystem: String

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme

    private var points: [(bin: ILTrainingVolumeBin, date: Date)] {
        bins.compactMap { bin in
            guard let date = ilAnalyticsDate(bin.weekStart) else { return nil }
            return (bin, date)
        }
    }

    var body: some View {
        let palette = theme.palette(for: colorScheme)

        IronLogCard(
            title: "Volumen über acht Wochen",
            subtitle: "Arbeitsvolumen pro Kalenderwoche · " + ilWeightUnit(unitSystem)
        ) {
            if points.isEmpty {
                Text("Noch keine Wochenwerte verfügbar")
                    .font(.body)
                    .foregroundStyle(palette.textSecondary)
            } else {
                Chart {
                    ForEach(points.indices, id: \.self) { index in
                        let point = points[index]
                        AreaMark(
                            x: .value("Woche", point.date),
                            y: .value("Volumen", ilWeightValue(point.bin.volumeKg, unitSystem: unitSystem))
                        )
                        .foregroundStyle(palette.secondary.opacity(0.16))

                        LineMark(
                            x: .value("Woche", point.date),
                            y: .value("Volumen", ilWeightValue(point.bin.volumeKg, unitSystem: unitSystem))
                        )
                        .foregroundStyle(palette.secondary)
                        .lineStyle(StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))

                        PointMark(
                            x: .value("Woche", point.date),
                            y: .value("Volumen", ilWeightValue(point.bin.volumeKg, unitSystem: unitSystem))
                        )
                        .foregroundStyle(palette.secondary)
                        .symbolSize(30)
                    }
                }
                .chartYScale(domain: .automatic(includesZero: true))
                .chartXAxis {
                    AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(palette.separator)
                        AxisTick(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(palette.separator)
                        AxisValueLabel(format: .dateTime.month(.abbreviated).day())
                            .foregroundStyle(palette.textSecondary)
                    }
                }
                .chartYAxis {
                    AxisMarks(position: .leading, values: .automatic(desiredCount: 4)) { value in
                        AxisGridLine(stroke: StrokeStyle(lineWidth: 0.5))
                            .foregroundStyle(palette.separator)
                        AxisValueLabel {
                            if let number = value.as(Double.self) {
                                Text(number.formatted(.number.precision(.fractionLength(0...0))))
                                    .foregroundStyle(palette.textSecondary)
                            }
                        }
                    }
                }
                .frame(minHeight: 190)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Volumen über acht Wochen")
                .accessibilityValue(
                    Text(points.map { "\(ilAnalyticsDateText($0.bin.weekStart, dateStyle: .short) ?? $0.bin.weekStart): \(ilVolumeText($0.bin.volumeKg, unitSystem: unitSystem))" }.joined(separator: ", "))
                )

                HStack(spacing: theme.metrics.compactSpacing) {
                    Image(systemName: "chart.line.uptrend.xyaxis")
                        .foregroundStyle(palette.secondary)
                    Text("Die Linie zeigt das gesamte Gewicht × Wiederholungen der gültigen Arbeitssätze.")
                        .font(.caption)
                        .foregroundStyle(palette.textSecondary)
                }
            }
        }
    }
}
