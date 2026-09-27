import SwiftUI

/// One calendar week (Monday to Sunday) of the history in Liquid Glass.
struct IOSHistoryWeek: Identifiable, Equatable {
    let weekStart: Date
    /// Sessions shown in the list, newest first.
    let sessions: [ILWorkoutSession]
    /// Training minutes per day, Monday first; counts every completed training of the
    /// week, also when a filter hides some of them in the list.
    let minutesPerDay: [Int]
    let workoutCount: Int

    var id: Date { weekStart }
    var totalMinutes: Int { minutesPerDay.reduce(0, +) }

    static var calendar: Calendar {
        var calendar = Calendar(identifier: .iso8601)
        calendar.timeZone = .current
        return calendar
    }

    static func weekStart(of date: Date) -> Date {
        calendar.dateInterval(of: .weekOfYear, for: date)?.start ?? calendar.startOfDay(for: date)
    }

    /// Groups the visible sessions by week, newest week first. Every training counts at
    /// least one minute, so a short training still shows up as a bar.
    static func group(visible: [ILWorkoutSession], all: [ILWorkoutSession]) -> [IOSHistoryWeek] {
        let completed = all.filter { $0.endTime != nil }
        let allByWeek = Dictionary(grouping: completed) { weekStart(of: $0.startDate) }
        let visibleByWeek = Dictionary(grouping: visible) { weekStart(of: $0.startDate) }
        return visibleByWeek.keys.sorted(by: >).map { start in
            var minutes = Array(repeating: 0, count: 7)
            let weekSessions = allByWeek[start] ?? []
            for session in weekSessions {
                let day = calendar.dateComponents([.day], from: start, to: calendar.startOfDay(for: session.startDate)).day ?? 0
                guard (0..<7).contains(day) else { continue }
                minutes[day] += max(1, Int(session.iosDurationSeconds / 60))
            }
            return IOSHistoryWeek(
                weekStart: start,
                sessions: (visibleByWeek[start] ?? []).sorted { $0.startTime > $1.startTime },
                minutesPerDay: minutes,
                workoutCount: weekSessions.count
            )
        }
    }
}

private let historyGerman = Locale(identifier: "de_DE")

private func historyGlassMinutes(_ minutes: Int) -> String {
    minutes >= 60 ? "\(minutes / 60) h \(minutes % 60) min" : "\(minutes) min"
}

/// Week header: name of the week, number of trainings, date range, total time and mini bars
/// Monday to Sunday.
struct IOSHistoryGlassWeekHeader: View {
    let week: IOSHistoryWeek
    var now: Date = Date()

    private var weekName: String {
        let current = IOSHistoryWeek.weekStart(of: now)
        if week.weekStart == current { return "Diese Woche" }
        if let last = IOSHistoryWeek.calendar.date(byAdding: .weekOfYear, value: -1, to: current), week.weekStart == last {
            return "Letzte Woche"
        }
        return "KW \(IOSHistoryWeek.calendar.component(.weekOfYear, from: week.weekStart))"
    }

    private var range: String {
        let end = IOSHistoryWeek.calendar.date(byAdding: .day, value: 6, to: week.weekStart) ?? week.weekStart
        let dayMonth = Date.FormatStyle().day().month(.abbreviated).locale(historyGerman)
        let sameMonth = IOSHistoryWeek.calendar.component(.month, from: week.weekStart) == IOSHistoryWeek.calendar.component(.month, from: end)
        if sameMonth {
            return "\(IOSHistoryWeek.calendar.component(.day, from: week.weekStart)).–\(end.formatted(dayMonth))"
        }
        return "\(week.weekStart.formatted(dayMonth)) – \(end.formatted(dayMonth))"
    }

    var body: some View {
        HStack(alignment: .center, spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text(weekName.uppercased())
                    .font(.caption.weight(.bold))
                    .tracking(0.8)
                    .foregroundStyle(.secondary)
                Text(ilCount(week.workoutCount, "Training", "Trainings"))
                    .font(.title2.weight(.heavy))
                Text("\(range) · \(historyGlassMinutes(week.totalMinutes))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            IOSHistoryGlassMiniBars(minutesPerDay: week.minutesPerDay, weekStart: week.weekStart, now: now)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .liquidGlass(in: RoundedRectangle(cornerRadius: 28, style: .continuous))
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}

private struct IOSHistoryGlassMiniBars: View {
    let minutesPerDay: [Int]
    let weekStart: Date
    let now: Date

    private static let dayLetters = ["M", "D", "M", "D", "F", "S", "S"]

    var body: some View {
        let maxMinutes = max(1, minutesPerDay.max() ?? 0)
        HStack(alignment: .bottom, spacing: 6) {
            ForEach(0..<7, id: \.self) { index in
                let value = index < minutesPerDay.count ? minutesPerDay[index] : 0
                let day = IOSHistoryWeek.calendar.date(byAdding: .day, value: index, to: weekStart) ?? weekStart
                let isToday = IOSHistoryWeek.calendar.isDate(day, inSameDayAs: now)
                VStack(spacing: 4) {
                    ZStack(alignment: .bottom) {
                        RoundedRectangle(cornerRadius: 4).fill(Color.primary.opacity(0.12))
                        if value > 0 {
                            RoundedRectangle(cornerRadius: 4)
                                .fill(Color.accentColor)
                                .frame(height: max(6, 40 * CGFloat(value) / CGFloat(maxMinutes)))
                        }
                    }
                    .frame(width: 8, height: 40)
                    Text(Self.dayLetters[index])
                        .font(.system(size: 10, weight: isToday ? .heavy : .semibold))
                        .foregroundStyle(isToday ? .primary : .secondary)
                }
            }
        }
        .accessibilityHidden(true)
    }
}

/// One training as a glass row: date lens, name and key figures.
struct IOSHistoryGlassRow: View {
    let session: ILWorkoutSession
    let data: ILTrainingData?
    let unitSystem: String

    private var planName: String? {
        guard let planID = session.planId else { return nil }
        return data?.trainingPlans.first(where: { $0.id == planID })?.name
    }

    private var figures: String {
        let sets = data.map { $0.sets(for: session).filter(\.iosVisibleInHistory) } ?? []
        let exerciseCount = Set(sets.map(\.exerciseId)).count
        let volume = sets.filter(\.iosCountsAsWorkSet).reduce(0) { $0 + max(0, $1.weightKg) * Double(max(0, $1.reps)) }
        var parts = [
            session.iosDurationSeconds < 60 ? "< 1 min" : historyGlassMinutes(Int(session.iosDurationSeconds / 60)),
            ilCount(exerciseCount, "Übung", "Übungen")
        ]
        if volume > 0 { parts.append(IOSWeightFormatter.format(volume, unitSystem: unitSystem)) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        HStack(spacing: 14) {
            VStack(spacing: 0) {
                Text("\(IOSHistoryWeek.calendar.component(.day, from: session.startDate))")
                    .font(.system(size: 18, weight: .heavy).monospacedDigit())
                Text(session.startDate.formatted(.dateTime.weekday(.abbreviated).locale(historyGerman)).replacingOccurrences(of: ".", with: "").uppercased())
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(.secondary)
            }
            .frame(width: 50, height: 50)
            .liquidGlass(in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text(session.displayName(planName: planName))
                    .font(.system(size: 16, weight: .heavy))
                    .lineLimit(1)
                Text(figures)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .liquidGlass(in: RoundedRectangle(cornerRadius: 24, style: .continuous))
        .contentShape(Rectangle())
    }
}
