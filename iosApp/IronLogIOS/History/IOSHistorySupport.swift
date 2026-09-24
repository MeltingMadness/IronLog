import Foundation

/// Formatting shared by the history and progression review surfaces.
///
/// The transport model stores kilograms and epoch milliseconds. Keeping the
/// conversion at the display boundary prevents an imperial preference from
/// changing the persisted values or the progression commands.
enum IOSWeightFormatter {
    static let poundsPerKilogram = 2.2046226218

    static func format(_ kilograms: Double, unitSystem: String) -> String {
        guard kilograms.isFinite else { return "—" }
        let isImperial = unitSystem.uppercased() == "IMPERIAL"
        let value = isImperial ? kilograms * poundsPerKilogram : kilograms
        let unit = isImperial ? "lb" : "kg"
        return "\(number(value)) \(unit)"
    }

    static func unitLabel(for unitSystem: String) -> String {
        unitSystem.uppercased() == "IMPERIAL" ? "lb" : "kg"
    }

    static func kilograms(fromDisplayedValue value: Double, unitSystem: String) -> Double {
        unitSystem.uppercased() == "IMPERIAL" ? value / poundsPerKilogram : value
    }

    private static func number(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.locale = .current
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 2
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }
}

enum IOSHistoryDateFilter: String, CaseIterable, Identifiable {
    case all
    case last7
    case last30
    case last90

    var id: String { rawValue }

    var title: String {
        switch self {
        case .all: return "Alle"
        case .last7: return "7 Tage"
        case .last30: return "30 Tage"
        case .last90: return "90 Tage"
        }
    }

    func includes(_ date: Date, now: Date = Date(), calendar: Calendar = .current) -> Bool {
        guard self != .all else { return true }
        let days: Int
        switch self {
        case .all: days = 0
        case .last7: days = 7
        case .last30: days = 30
        case .last90: days = 90
        }
        // Android derives the lower bound from the local calendar date and then uses midnight.
        // Subtracting from the current instant would incorrectly exclude earlier workouts on
        // the boundary day (for example, today at 15:00 would start the window at 15:00).
        let today = calendar.startOfDay(for: now)
        guard let boundary = calendar.date(byAdding: .day, value: -days, to: today) else {
            return true
        }
        return date >= boundary
    }
}

enum IOSHistoryFormatting {
    static func date(_ value: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: value)
    }

    static func duration(seconds: Int64) -> String {
        let clamped = max(0, seconds)
        let hours = clamped / 3_600
        let minutes = (clamped % 3_600) / 60
        if hours > 0 { return "\(hours) h \(minutes) min" }
        return "\(minutes) min"
    }

    static func number(_ value: Double, maximumFractionDigits: Int = 1) -> String {
        let formatter = NumberFormatter()
        formatter.locale = .current
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = maximumFractionDigits
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }
}

extension ILWorkoutSet {
    /// Resolves the schema-11 compatibility flag without changing the DTO.
    var iosResolvedSetType: String {
        if let setType, !setType.isEmpty { return setType.uppercased() }
        return isWarmup == true ? "WARMUP" : "NORMAL"
    }

    var iosIsWarmup: Bool { iosResolvedSetType == "WARMUP" }
    var iosIsFailure: Bool { iosResolvedSetType == "FAILURE" }

    var iosSetTypeDisplayName: String {
        switch iosResolvedSetType {
        case "NORMAL": return "Arbeitssatz"
        case "WARMUP": return "Aufwärmsatz"
        case "DROP_SET": return "Dropsatz"
        case "FAILURE": return "Versagen"
        default: return iosResolvedSetType
        }
    }

    /// Android keeps a zero-repetition failure attempt visible in history while
    /// hiding empty normal rows. This also preserves an actual zero as evidence.
    var iosVisibleInHistory: Bool { reps > 0 || iosIsFailure }
    var iosCountsAsWorkSet: Bool { !iosIsWarmup && iosVisibleInHistory }
}
