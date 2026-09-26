import Foundation

enum IOSWorkoutSetType: String, CaseIterable, Identifiable {
    case normal = "NORMAL"
    case warmup = "WARMUP"
    case dropSet = "DROP_SET"
    case failure = "FAILURE"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .normal: return "Arbeitsset"
        case .warmup: return "Warmup"
        case .dropSet: return "Drop-Set"
        case .failure: return "Failure-Set"
        }
    }

    var shortName: String {
        switch self {
        case .normal: return "Arbeit"
        case .warmup: return "Warmup"
        case .dropSet: return "Drop"
        case .failure: return "Failure"
        }
    }
}

func resolvedIOSWorkoutSetType(_ set: ILWorkoutSet) -> IOSWorkoutSetType {
    if let raw = set.setType, let value = IOSWorkoutSetType(rawValue: raw) {
        return value
    }
    return set.isWarmup == true ? .warmup : .normal
}

func iosWorkoutDecimal(_ value: Double, fractionDigits: Int = 2) -> String {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "de_DE")
    formatter.numberStyle = .decimal
    formatter.minimumFractionDigits = 0
    formatter.maximumFractionDigits = fractionDigits
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

func iosWorkoutParseDecimal(_ text: String) -> Double? {
    let normalized = text
        .trimmingCharacters(in: .whitespacesAndNewlines)
        .replacingOccurrences(of: ",", with: ".")
    guard !normalized.isEmpty else { return nil }
    return Double(normalized)
}

func iosWorkoutFormatDuration(_ seconds: Int) -> String {
    let safeSeconds = max(0, seconds)
    return String(format: "%02d:%02d", safeSeconds / 60, safeSeconds % 60)
}

func iosWorkoutDisplayWeight(
    kilograms: Double,
    unitSystem: String
) -> String {
    let value = unitSystem.uppercased() == "IMPERIAL" ? kilograms * 2.2046226218 : kilograms
    let unit = unitSystem.uppercased() == "IMPERIAL" ? "lb" : "kg"
    return "\(iosWorkoutDecimal(value)) \(unit)"
}

/// Logged set as "82,5 kg × 8 Wdh"; 0 kg reads as bodyweight, like on Android.
func iosSetValueText(weightKg: Double, reps: Int, unitSystem: String) -> String {
    let weight = weightKg == 0 ? "Körpergewicht" : iosWorkoutDisplayWeight(kilograms: weightKg, unitSystem: unitSystem)
    return "\(weight) × \(reps) Wdh"
}

func iosWorkoutInputWeightToKg(
    _ value: Double,
    unitSystem: String
) -> Double {
    unitSystem.uppercased() == "IMPERIAL" ? value / 2.2046226218 : value
}

func iosWorkoutWeightInInputUnit(
    kilograms: Double,
    unitSystem: String
) -> Double {
    unitSystem.uppercased() == "IMPERIAL" ? kilograms * 2.2046226218 : kilograms
}

func iosWorkoutFormatIntensity(
    storedRPE: Double?,
    intensitySystem: String
) -> String? {
    guard let storedRPE, intensitySystem.uppercased() != "OFF" else { return nil }
    let value = intensitySystem.uppercased() == "RIR" ? 10 - storedRPE : storedRPE
    return iosWorkoutDecimal(value, fractionDigits: 1)
}

func iosWorkoutStoredRPE(
    inputValue: Double,
    intensitySystem: String
) -> Double {
    intensitySystem.uppercased() == "RIR" ? 10 - inputValue : inputValue
}

/// Returns the intensity scale that is actually editable for one workout row.
///
/// The global preference can disable intensity input for free/manual rows. An RPE/RIR plan is
/// different: its progression contract needs an RPE value even when the global preference is
/// OFF, so the row falls back to the canonical RPE scale just like Android.
func iosWorkoutEffectiveIntensitySystem(
    configuredSystem: String,
    planTarget: ILWorkoutPlanTarget?
) -> String {
    let configured = configuredSystem.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    guard configured == "OFF",
          planTarget?.progression.scheme.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() == "RPE_RIR"
    else {
        return configured
    }
    return "RPE"
}

enum IOSWorkoutIntensityInputError: Error, Equatable {
    case invalidRPE
    case invalidRIR

    var message: String {
        switch self {
        case .invalidRPE: return "RPE muss zwischen 1 und 10 liegen."
        case .invalidRIR: return "RIR muss zwischen 0 und 9 liegen."
        }
    }
}

/// Converts the visible editor value to canonical RPE while preserving a value from a hidden
/// OFF field. A blank visible value remains an intentional clear and therefore returns `nil`.
func iosWorkoutStoredIntensityForSave(
    existingRPE: Double?,
    inputText: String,
    configuredSystem: String,
    planTarget: ILWorkoutPlanTarget?
) throws -> Double? {
    let effectiveSystem = iosWorkoutEffectiveIntensitySystem(
        configuredSystem: configuredSystem,
        planTarget: planTarget
    )
    if effectiveSystem == "OFF" {
        return existingRPE
    }

    let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty {
        return nil
    }
    guard let value = iosWorkoutParseDecimal(trimmed), value.isFinite else {
        throw effectiveSystem == "RIR"
            ? IOSWorkoutIntensityInputError.invalidRIR
            : IOSWorkoutIntensityInputError.invalidRPE
    }

    let lowerBound = effectiveSystem == "RIR" ? 0.0 : 1.0
    let upperBound = effectiveSystem == "RIR" ? 9.0 : 10.0
    guard value >= lowerBound, value <= upperBound else {
        throw effectiveSystem == "RIR"
            ? IOSWorkoutIntensityInputError.invalidRIR
            : IOSWorkoutIntensityInputError.invalidRPE
    }
    return iosWorkoutStoredRPE(inputValue: value, intensitySystem: effectiveSystem)
}
