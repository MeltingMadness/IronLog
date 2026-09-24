import SwiftUI

/// A metric or readiness ring that keeps an unknown value visibly unknown.
///
/// `progress` is supplied by the caller as a presentation-ready fraction in
/// the range 0...1. Passing `nil` leaves the ring unfilled and renders the
/// value as unavailable; the component never invents a score or percentage.
struct IronLogMetricRing: View {
    let title: String
    let value: String?
    let progress: Double?
    let unit: String?
    let detail: String?
    let accent: IronLogTheme.Accent
    let unknownLabel: String

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme
    @ScaledMetric(relativeTo: .largeTitle) private var diameter: CGFloat = 112
    @ScaledMetric(relativeTo: .body) private var lineWidth: CGFloat = 10

    init(
        title: String,
        value: String?,
        progress: Double?,
        unit: String? = nil,
        detail: String? = nil,
        accent: IronLogTheme.Accent = .primary,
        unknownLabel: String = "Nicht verfügbar"
    ) {
        self.title = title
        self.value = value
        self.progress = progress
        self.unit = unit
        self.detail = detail
        self.accent = accent
        self.unknownLabel = unknownLabel
    }

    var body: some View {
        let palette = theme.palette(for: colorScheme)
        let normalizedProgress = ironLogNormalizedProgress

        ZStack {
            Circle()
                .stroke(palette.progressTrack, style: StrokeStyle(lineWidth: lineWidth))

            if let normalizedProgress {
                Circle()
                    .trim(from: 0, to: normalizedProgress)
                    .stroke(
                        palette.accent(accent),
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .round)
                    )
                    .rotationEffect(.degrees(-90))
            } else {
                Circle()
                    .stroke(
                        palette.textSecondary.opacity(0.72),
                        style: StrokeStyle(lineWidth: lineWidth, lineCap: .round, dash: [4, 6])
                    )
                    .rotationEffect(.degrees(-90))
            }

            VStack(spacing: 2) {
                Text(value ?? "—")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(palette.textPrimary)
                    .lineLimit(2)
                    .minimumScaleFactor(0.75)

                if let unit, !unit.isEmpty {
                    Text(unit)
                        .font(.caption)
                        .foregroundStyle(palette.textSecondary)
                        .lineLimit(1)
                }
            }
            .padding(lineWidth * 1.5)
        }
        .frame(width: diameter, height: diameter)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(title))
        .accessibilityValue(Text(ironLogAccessibilitySummary))
    }

    private var ironLogNormalizedProgress: Double? {
        guard let progress, progress.isFinite else { return nil }
        return min(max(progress, 0), 1)
    }

    private var ironLogAccessibilitySummary: String {
        var parts: [String] = []
        if let value, !value.isEmpty {
            parts.append(value)
            if let unit, !unit.isEmpty {
                parts.append(unit)
            }
        } else {
            parts.append(unknownLabel)
        }

        if let detail, !detail.isEmpty {
            parts.append(detail)
        }
        return parts.joined(separator: ", ")
    }
}
