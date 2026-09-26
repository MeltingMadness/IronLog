import Foundation

/// Presentation-only helpers shared by the dashboard and statistics screens.
/// Aggregation, thresholds, and exercise metrics stay in the shared analytics
/// projection; these helpers only format already-computed values.
func ilAnalyticsDate(_ value: String) -> Date? {
    let formatter = DateFormatter()
    formatter.calendar = Calendar(identifier: .gregorian)
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = TimeZone(secondsFromGMT: 0)
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.date(from: value)
}

func ilAnalyticsShiftedWeekStart(_ value: String, weeks: Int) -> String? {
    guard let date = ilAnalyticsDate(value) else { return nil }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
    guard let shifted = calendar.date(byAdding: .day, value: weeks * 7, to: date) else {
        return nil
    }
    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = calendar.timeZone
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: shifted)
}

func ilAnalyticsDateText(_ value: String?, dateStyle: DateFormatter.Style = .medium) -> String? {
    guard let value, let date = ilAnalyticsDate(value) else { return nil }
    let formatter = DateFormatter()
    formatter.locale = .current
    formatter.calendar = .current
    formatter.timeZone = .current
    formatter.dateStyle = dateStyle
    formatter.timeStyle = .none
    return formatter.string(from: date)
}

func ilAnalyticsDateTimeText(epochMillis: Int64) -> String {
    let date = Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1_000.0)
    return date.formatted(date: .abbreviated, time: .shortened)
}

func ilWeightValue(_ kilograms: Double, unitSystem: String) -> Double {
    unitSystem.uppercased() == "IMPERIAL" ? kilograms * 2.2046226218 : kilograms
}

func ilWeightUnit(_ unitSystem: String) -> String {
    unitSystem.uppercased() == "IMPERIAL" ? "lb" : "kg"
}

func ilWeightText(_ kilograms: Double?, unitSystem: String, signed: Bool = false) -> String {
    guard let kilograms, kilograms.isFinite else { return "—" }
    let value = ilWeightValue(kilograms, unitSystem: unitSystem)
    let sign = signed && value > 0 ? "+" : ""
    return sign + value.formatted(.number.precision(.fractionLength(0...1))) + " " + ilWeightUnit(unitSystem)
}

func ilVolumeText(_ kilograms: Double?, unitSystem: String, signed: Bool = false) -> String {
    ilWeightText(kilograms, unitSystem: unitSystem, signed: signed)
}

/// Count with the matching German noun, e.g. "1 Training" / "3 Trainings".
func ilCount<T: BinaryInteger>(_ value: T, _ singular: String, _ plural: String) -> String {
    "\(Int(value).formatted(.number)) \(value == 1 ? singular : plural)"
}

func ilCountText(_ value: Int?) -> String {
    guard let value else { return "—" }
    return value.formatted(.number)
}

func ilMuscleDisplayName(_ raw: String) -> String {
    switch raw.uppercased() {
    case "BRUST": return "Brust"
    case "RUECKEN": return "Rücken"
    case "BEINE": return "Beine"
    case "SCHULTERN": return "Schultern"
    case "BIZEPS": return "Bizeps"
    case "TRIZEPS": return "Trizeps"
    case "GESAESS": return "Gesäß"
    case "CORE": return "Core"
    case "UNTERARME": return "Unterarme"
    case "WADEN": return "Waden"
    default: return raw
    }
}

func ilRecordTypeText(_ raw: String) -> String {
    switch raw.uppercased() {
    case "MAX_WEIGHT": return "Max. Gewicht"
    case "MAX_REPS": return "Max. Wiederholungen"
    case "MAX_VOLUME": return "Max. Volumen"
    case "MAX_E1RM": return "Bestes e1RM"
    default: return raw
    }
}

func ilRecordValueText(type: String, value: Double, unitSystem: String) -> String {
    switch type.uppercased() {
    case "MAX_WEIGHT", "MAX_E1RM":
        return ilWeightText(value, unitSystem: unitSystem)
    case "MAX_VOLUME":
        return ilVolumeText(value, unitSystem: unitSystem)
    case "MAX_REPS":
        return value.formatted(.number.precision(.fractionLength(0))) + " Wdh."
    default:
        return value.formatted(.number.precision(.fractionLength(0...1)))
    }
}

func ilReadinessStatusText(_ status: ILTrainingReadinessStatus) -> String {
    switch status {
    case .insufficientData: return "Noch nicht genug Daten"
    case .noNotableStrain: return "Keine auffällige Belastung"
    case .signalsPresent: return "Belastungssignale erkannt"
    case .unknown: return "Unbekannter Status"
    }
}

func ilReadinessSignalText(_ signal: ILTrainingReadinessSignal) -> String {
    switch signal {
    case .e1rmStagnation: return "e1RM stagniert"
    case .e1rmDrop: return "e1RM fällt"
    case .rpeCreep: return "RPE steigt"
    case .failureFrequency: return "Viele Fehlersätze"
    }
}

func ilVolumeStatusText(_ status: ILTrainingVolumeStatus) -> String {
    switch status {
    case .low: return "Unter MEV"
    case .optimal: return "Im Zielbereich"
    case .high: return "Über MRV"
    case .unknown: return "Nicht klassifiziert"
    }
}

// MARK: - Trainingstrend und Tagesform

/// Distinct, human-readable German reasons for one assessment.
///
/// The shared core emits stable codes instead of prose. Rendering them here keeps
/// internal identifiers out of the interface and collapses repeated codes, so a card
/// never lists the same sentence twice.
func ilReasonTexts(_ reasons: [ILReadinessReason]) -> [String] {
    var seen = Set<String>()
    var texts: [String] = []
    for reason in reasons {
        let text = ilReadinessReasonText(reason)
        guard seen.insert(text).inserted else { continue }
        texts.append(text)
    }
    return texts
}

/// The first `limit` distinct reasons plus the number omitted, so long evidence
/// lists stay scannable without hiding that more exist.
func ilCappedReasonTexts(
    _ reasons: [ILReadinessReason],
    limit: Int
) -> (texts: [String], remaining: Int) {
    let all = ilReasonTexts(reasons)
    guard all.count > limit, limit > 0 else { return (all, 0) }
    return (Array(all.prefix(limit)), all.count - limit)
}

/// Calendar date of an epoch-millisecond instant in the device's time zone.
func ilEpochDateText(_ epochMillis: Int64?) -> String? {
    guard let epochMillis else { return nil }
    let formatter = DateFormatter()
    formatter.locale = .current
    formatter.calendar = .current
    formatter.timeZone = .current
    formatter.dateStyle = .medium
    formatter.timeStyle = .none
    return formatter.string(from: Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1_000.0))
}

/// "Zuletzt heute/gestern/vor N Tagen" for a past load. `nil` stays explicit: the
/// store holds no load, so nothing is interpreted.
func ilPastDayText(_ epochMillis: Int64?, reference: Date = Date()) -> String {
    guard let epochMillis else { return "Keine Belastung erfasst" }
    let calendar = Calendar.current
    let then = calendar.startOfDay(for: Date(timeIntervalSince1970: TimeInterval(epochMillis) / 1_000.0))
    let today = calendar.startOfDay(for: reference)
    let days = calendar.dateComponents([.day], from: then, to: today).day ?? 0
    if days <= 0 { return "Zuletzt heute" }
    if days == 1 { return "Zuletzt gestern" }
    return "Zuletzt vor \(days) Tagen"
}

/// Work sets as a factual count. The shared projection reports fractional values
/// because a secondary muscle group counts with half a set.
func ilSetCountText(_ sets: Double) -> String {
    let value = sets.formatted(.number.precision(.fractionLength(0...1)))
    return "\(value) \(sets == 1 ? "Satz" : "Sätze")"
}
