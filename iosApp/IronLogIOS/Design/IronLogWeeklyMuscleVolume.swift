import Foundation
import SwiftUI

/// Threshold values supplied by the shared/domain layer for one muscle group.
///
/// `nil` means that the source did not provide the threshold. The view keeps
/// that absence visible instead of substituting a default value.
struct IronLogVolumeThresholds: Equatable, Sendable {
    let minimum: Double?
    let target: Double?
    let maximum: Double?

    init(
        minimum: Double? = nil,
        target: Double? = nil,
        maximum: Double? = nil
    ) {
        self.minimum = minimum
        self.target = target
        self.maximum = maximum
    }
}

/// Plain value data needed to render one weekly muscle-volume row.
///
/// The caller owns aggregation, status decisions, and localization. A nil
/// `weeklySets` is different from zero: it means that the source has not
/// supplied a measurement for this row.
struct IronLogWeeklyMuscleVolume: Identifiable, Equatable, Sendable {
    let id: String
    let title: String
    let weeklySets: Double?
    let thresholds: IronLogVolumeThresholds

    init(
        id: String,
        title: String,
        weeklySets: Double?,
        thresholds: IronLogVolumeThresholds = IronLogVolumeThresholds()
    ) {
        self.id = id
        self.title = title
        self.weeklySets = weeklySets
        self.thresholds = thresholds
    }

    init(
        id: String,
        title: String,
        weeklySets: Double?,
        minimumSets: Double? = nil,
        targetSets: Double? = nil,
        maximumSets: Double? = nil
    ) {
        self.init(
            id: id,
            title: title,
            weeklySets: weeklySets,
            thresholds: IronLogVolumeThresholds(
                minimum: minimumSets,
                target: targetSets,
                maximum: maximumSets
            )
        )
    }
}

/// A themed, collapsible collection of weekly muscle-volume rows.
///
/// Rows stay value-driven and stable through `Identifiable` data. The card
/// does not classify volume as low/optimal/high; a future integration can
/// supply that decision elsewhere without changing this surface.
struct IronLogWeeklyMuscleVolumeCard: View {
    let title: String
    let subtitle: String?
    let rows: [IronLogWeeklyMuscleVolume]
    let emptyMessage: String
    let weekStart: String?
    let currentWeekStart: String?
    let completedWorkoutCount: Int?
    let isLoading: Bool
    let errorMessage: String?
    let onRetry: (() -> Void)?
    let onPreviousWeek: (() -> Void)?
    let onNextWeek: (() -> Void)?

    @State private var isExpanded: Bool
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme
    @ScaledMetric(relativeTo: .body) private var rowPadding: CGFloat = 6

    init(
        title: String,
        subtitle: String? = nil,
        rows: [IronLogWeeklyMuscleVolume],
        initiallyExpanded: Bool = true,
        emptyMessage: String = "Keine Daten verfügbar",
        weekStart: String? = nil,
        currentWeekStart: String? = nil,
        completedWorkoutCount: Int? = nil,
        isLoading: Bool = false,
        errorMessage: String? = nil,
        onRetry: (() -> Void)? = nil,
        onPreviousWeek: (() -> Void)? = nil,
        onNextWeek: (() -> Void)? = nil
    ) {
        self.title = title
        self.subtitle = subtitle
        self.rows = rows
        self.emptyMessage = emptyMessage
        self.weekStart = weekStart
        self.currentWeekStart = currentWeekStart
        self.completedWorkoutCount = completedWorkoutCount
        self.isLoading = isLoading
        self.errorMessage = errorMessage
        self.onRetry = onRetry
        self.onPreviousWeek = onPreviousWeek
        self.onNextWeek = onNextWeek
        _isExpanded = State(initialValue: initiallyExpanded)
    }

    var body: some View {
        let palette = theme.palette(for: colorScheme)

        IronLogCard(tone: .muted) {
            Button {
                withAnimation(theme.reducedMotion ? nil : .snappy(duration: 0.22)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack(alignment: .firstTextBaseline, spacing: theme.metrics.compactSpacing) {
                    VStack(alignment: .leading, spacing: theme.metrics.compactSpacing) {
                        Text(title)
                            .font(.headline.weight(.semibold))
                            .foregroundStyle(palette.textPrimary)

                        if let subtitle {
                            Text(subtitle)
                                .font(.subheadline)
                                .foregroundStyle(palette.textSecondary)
                        }
                    }

                    Spacer(minLength: theme.metrics.compactSpacing)

                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(palette.textSecondary)
                        .accessibilityHidden(true)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(title))
            .accessibilityValue(Text(isExpanded ? "Geöffnet" : "Geschlossen"))
                        .accessibilityHint(Text(isExpanded ? "Zum Reduzieren tippen" : "Zum Anzeigen tippen"))

            if weekStart != nil {
                IronLogWeeklyMuscleVolumeWeekHeader(
                    weekStart: weekStart,
                    currentWeekStart: currentWeekStart,
                    isLoading: isLoading,
                    onPreviousWeek: onPreviousWeek,
                    onNextWeek: onNextWeek
                )
            }

            if !isLoading, errorMessage == nil, let completedWorkoutCount {
                Text("\(completedWorkoutCount) abgeschlossene Trainings")
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
            }

            if isExpanded {
                if isLoading {
                    ProgressView("Wochenvolumen wird geladen …")
                        .frame(maxWidth: .infinity, alignment: .center)
                        .padding(.vertical, rowPadding * 2)
                } else if let errorMessage {
                    VStack(alignment: .leading, spacing: theme.metrics.compactSpacing) {
                        Text(errorMessage)
                            .font(.body)
                            .foregroundStyle(palette.textSecondary)

                        if let onRetry {
                            Button("Erneut versuchen", action: onRetry)
                                .buttonStyle(.bordered)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, rowPadding)
                } else if rows.isEmpty {
                    Text(emptyMessage)
                        .font(.body)
                        .foregroundStyle(palette.textSecondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, rowPadding)
                } else {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(rows) { volume in
                            IronLogWeeklyMuscleVolumeRow(volume: volume)
                                .padding(.vertical, rowPadding)

                            if volume.id != rows.last?.id {
                                Divider()
                                    .overlay(palette.separator)
                            }
                        }
                    }
                }
            }
        }
    }
}

private struct IronLogWeeklyMuscleVolumeWeekHeader: View {
    let weekStart: String?
    let currentWeekStart: String?
    let isLoading: Bool
    let onPreviousWeek: (() -> Void)?
    let onNextWeek: (() -> Void)?

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme

    private var isCurrentWeek: Bool {
        guard let weekStart, let currentWeekStart else { return false }
        return weekStart == currentWeekStart
    }

    private var canShowNextWeek: Bool {
        guard onNextWeek != nil,
              let weekStart,
              let currentWeekStart
        else { return false }
        return weekStart < currentWeekStart
    }

    var body: some View {
        let palette = theme.palette(for: colorScheme)

        HStack(alignment: .center, spacing: theme.metrics.compactSpacing) {
            VStack(alignment: .leading, spacing: 2) {
                if let weekLabel {
                    Text(weekLabel)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textPrimary)
                } else if let weekStart {
                    Text(weekStart)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(palette.textPrimary)
                }

                Text(isCurrentWeek ? "Aktuelle Woche" : "Abgeschlossene Woche")
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
            }

            Spacer(minLength: theme.metrics.compactSpacing)

            HStack(spacing: 4) {
                Button {
                    onPreviousWeek?()
                } label: {
                    Image(systemName: "chevron.left")
                        .frame(width: 44, height: 44)
                }
                .disabled(onPreviousWeek == nil || isLoading)
                .accessibilityLabel("Vorherige Woche")

                Button {
                    onNextWeek?()
                } label: {
                    Image(systemName: "chevron.right")
                        .frame(width: 44, height: 44)
                }
                .disabled(!canShowNextWeek || isLoading)
                .accessibilityLabel("Nächste Woche")
            }
            .buttonStyle(.bordered)
        }
        .accessibilityElement(children: .contain)
    }

    private var weekLabel: String? {
        guard let weekStart,
              let start = Self.isoFormatter.date(from: weekStart),
              let end = Calendar(identifier: .gregorian).date(byAdding: .day, value: 6, to: start)
        else { return nil }
        return "\(Self.displayFormatter.string(from: start)) – \(Self.displayFormatter.string(from: end))"
    }

    private static let isoFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private static let displayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "de_DE")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "dd.MM.yyyy"
        return formatter
    }()
}

/// A single weekly volume row with optional threshold markers.
struct IronLogWeeklyMuscleVolumeRow: View {
    let volume: IronLogWeeklyMuscleVolume

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.ironLogTheme) private var theme
    @ScaledMetric(relativeTo: .body) private var progressHeight: CGFloat = 10

    var body: some View {
        let palette = theme.palette(for: colorScheme)

        VStack(alignment: .leading, spacing: theme.metrics.compactSpacing) {
            HStack(alignment: .firstTextBaseline, spacing: theme.metrics.compactSpacing) {
                Text(volume.title)
                    .font(.body.weight(.semibold))
                    .foregroundStyle(palette.textPrimary)
                    .lineLimit(2)

                Spacer(minLength: theme.metrics.compactSpacing)

                Text(ironLogSetsDescription)
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(palette.textSecondary)
                    .multilineTextAlignment(.trailing)
                    .layoutPriority(1)
            }

            IronLogVolumeProgressBar(
                volume: volume,
                height: progressHeight,
                palette: palette
            )

            if let thresholdDescription = ironLogThresholdDescription {
                Text(thresholdDescription)
                    .font(.caption)
                    .foregroundStyle(palette.textSecondary)
                    .lineLimit(2)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(volume.title))
        .accessibilityValue(Text(ironLogAccessibilitySummary))
    }

    private var ironLogSetsDescription: String {
        guard let weeklySets = volume.weeklySets, weeklySets.isFinite else {
            return "—"
        }
        return "\(ironLogFormatSets(weeklySets)) \(weeklySets == 1 ? "Satz" : "Sätze")"
    }

    private var ironLogThresholdDescription: String? {
        let thresholds = volume.thresholds
        let entries: [(String, Double?)] = [
            ("Min", thresholds.minimum),
            ("Ziel", thresholds.target),
            ("Max", thresholds.maximum)
        ]
        let available: [String] = entries.compactMap { entry -> String? in
            let (label, value) = entry
            guard let value, value.isFinite else { return nil }
            return "\(label) \(ironLogFormatSets(value))"
        }
        return available.isEmpty ? nil : available.joined(separator: " · ") + " Sätze"
    }

    private var ironLogAccessibilitySummary: String {
        var parts = [ironLogSetsDescription]
        if let thresholdDescription = ironLogThresholdDescription {
            parts.append(thresholdDescription)
        }
        return parts.joined(separator: ", ")
    }
}

private struct IronLogVolumeProgressBar: View {
    let volume: IronLogWeeklyMuscleVolume
    let height: CGFloat
    let palette: IronLogTheme.Palette

    var body: some View {
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule()
                    .fill(palette.progressTrack)

                if let fillFraction = ironLogFillFraction {
                    Capsule()
                        .fill(palette.primary.opacity(0.88))
                        .frame(width: proxy.size.width * fillFraction)
                }

                IronLogVolumeThresholdMarker(
                    fraction: ironLogFraction(for: volume.thresholds.minimum),
                    height: height,
                    color: palette.textSecondary
                )

                IronLogVolumeThresholdMarker(
                    fraction: ironLogFraction(for: volume.thresholds.target),
                    height: height,
                    color: palette.textPrimary.opacity(0.72)
                )
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }

    private var ironLogFillFraction: CGFloat? {
        guard
            let weeklySets = volume.weeklySets,
            weeklySets.isFinite,
            let maximum = volume.thresholds.maximum,
            maximum.isFinite,
            maximum > 0
        else {
            return nil
        }
        return CGFloat(min(max(weeklySets / maximum, 0), 1))
    }

    private func ironLogFraction(for threshold: Double?) -> CGFloat? {
        guard
            let threshold,
            threshold.isFinite,
            let maximum = volume.thresholds.maximum,
            maximum.isFinite,
            maximum > 0
        else {
            return nil
        }
        return CGFloat(min(max(threshold / maximum, 0), 1))
    }
}

private struct IronLogVolumeThresholdMarker: View {
    let fraction: CGFloat?
    let height: CGFloat
    let color: Color

    var body: some View {
        if let fraction {
            GeometryReader { proxy in
                Rectangle()
                    .fill(color)
                    .frame(width: 2, height: height)
                    .offset(x: max(0, proxy.size.width - 2) * fraction)
            }
        }
    }
}

private func ironLogFormatSets(_ value: Double) -> String {
    guard value.isFinite else { return "—" }

    if value.rounded() == value {
        return value.formatted(.number.precision(.fractionLength(0)))
    }
    return value.formatted(.number.precision(.fractionLength(0...1)))
}
