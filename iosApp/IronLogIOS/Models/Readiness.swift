import Foundation

/// Swift transport representation of `shared.readiness.ReadinessAssessment`.
///
/// The shared core owns every threshold and decision. This layer only decodes
/// and renders, so an unknown value can never turn into a fabricated score: a
/// missing `trainingIndex` stays `nil` and is displayed as "not enough data".
///
/// Every enum decodes tolerantly. When the shared core gains a new case, the
/// app keeps working and shows the value as unknown instead of failing to load
/// the whole readiness projection.
struct ILReadinessAssessment: Codable, Equatable {
    var generatedAtEpochMillis: Int64
    var trainingTrend: ILTrainingTrendAssessment
    var dailyForm: ILDailyFormAssessment?
    var muscleGroups: [ILMuscleGroupContext]
    var thresholdsRevision: Int
}

struct ILTrainingTrendAssessment: Codable, Equatable {
    var status: ILTrainingTrendStatus
    var trainingIndex: Int?
    var confidence: ILEvidenceConfidence
    var deloadActive: Bool
    var deloadSuggested: Bool
    var analyzedExerciseCount: Int
    var exercisesWithSufficientHistory: Int
    var repeatedDeclineExerciseCount: Int
    var exercises: [ILExerciseTrend]
    var dataQuality: ILTrendDataQuality
    var reasons: [ILReadinessReason]
    var thresholdsRevision: Int
}

enum ILTrainingTrendStatus: String, Codable, Equatable, CaseIterable {
    case insufficientData = "INSUFFICIENT_DATA"
    case noNotableStrain = "NO_NOTABLE_STRAIN"
    case singleExerciseDecline = "SINGLE_EXERCISE_DECLINE"
    case multipleExerciseDecline = "MULTIPLE_EXERCISE_DECLINE"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILTrainingTrendStatus(rawValue: raw) ?? .unknown
    }
}

enum ILExerciseTrendStatus: String, Codable, Equatable {
    case improving = "IMPROVING"
    case stable = "STABLE"
    case declining = "DECLINING"
    case insufficientData = "INSUFFICIENT_DATA"
    case excluded = "EXCLUDED"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILExerciseTrendStatus(rawValue: raw) ?? .unknown
    }
}

enum ILTrendMetricKind: String, Codable, Equatable {
    case estimatedOneRepMax = "ESTIMATED_ONE_REP_MAX"
    case goalScore = "GOAL_SCORE"
    case totalReps = "TOTAL_REPS"
    case none = "NONE"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILTrendMetricKind(rawValue: raw) ?? .unknown
    }
}

enum ILEvidenceConfidence: String, Codable, Equatable {
    case none = "NONE"
    case low = "LOW"
    case moderate = "MODERATE"
    case high = "HIGH"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILEvidenceConfidence(rawValue: raw) ?? .unknown
    }
}

struct ILExerciseTrend: Codable, Equatable {
    var exerciseId: Int64
    var exerciseName: String
    var equipmentType: ILEquipmentType
    var metricKind: ILTrendMetricKind
    var status: ILExerciseTrendStatus
    var comparableUnitCount: Int
    var latestMetricValue: Double?
    var baselineMetricValue: Double?
    var changePercent: Double?
    var repeatedDeclineCount: Int
    var confidence: ILEvidenceConfidence
    var excludedFromFatigue: Bool
    var reasons: [ILReadinessReason]
}

enum ILEquipmentType: String, Codable, Equatable {
    case barbell = "BARBELL"
    case dumbbell = "DUMBBELL"
    case machine = "MACHINE"
    case cable = "CABLE"
    case smithMachine = "SMITH_MACHINE"
    case bodyweight = "BODYWEIGHT"
    case kettlebell = "KETTLEBELL"
    case band = "BAND"
    case other = "OTHER"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILEquipmentType(rawValue: raw) ?? .unknown
    }
}

struct ILReadinessReason: Codable, Equatable {
    var code: ILReadinessReasonCode
    var arguments: [String: Double]
}

enum ILReadinessReasonCode: String, Codable, Equatable {
    case insufficientHistory = "INSUFFICIENT_HISTORY"
    case noNotableStrain = "NO_NOTABLE_STRAIN"
    case singleExerciseDecline = "SINGLE_EXERCISE_DECLINE"
    case multipleExerciseDeclines = "MULTIPLE_EXERCISE_DECLINES"
    case deloadInProgress = "DELOAD_IN_PROGRESS"
    case deloadSuggested = "DELOAD_SUGGESTED"
    case targetRpeApplied = "TARGET_RPE_APPLIED"
    case stagnationNeutral = "STAGNATION_NEUTRAL"
    case plannedFailureNeutral = "PLANNED_FAILURE_NEUTRAL"
    case plannedFailureUnitNeutral = "PLANNED_FAILURE_UNIT_NEUTRAL"
    case deloadContextNeutral = "DELOAD_CONTEXT_NEUTRAL"
    case missingRpeNeutral = "MISSING_RPE_NEUTRAL"
    case unknownIntentionPresent = "UNKNOWN_INTENTION_PRESENT"
    case unexpectedTargetMissPresent = "UNEXPECTED_TARGET_MISS_PRESENT"
    case comparableUnitsBelowMinimum = "COMPARABLE_UNITS_BELOW_MINIMUM"
    case noValidWorkSets = "NO_VALID_WORK_SETS"
    case deloadUnitsExcluded = "DELOAD_UNITS_EXCLUDED"
    case linearLoadScheme = "LINEAR_LOAD_SCHEME"
    case doubleProgressionScheme = "DOUBLE_PROGRESSION_SCHEME"
    case totalRepsScheme = "TOTAL_REPS_SCHEME"
    case equipmentChangedReset = "EQUIPMENT_CHANGED_RESET"
    case slotChangedReset = "SLOT_CHANGED_RESET"
    case goalChangedReset = "GOAL_CHANGED_RESET"
    case longGapReset = "LONG_GAP_RESET"
    case metricKindChangedReset = "METRIC_KIND_CHANGED_RESET"
    case repBandChangedReset = "REP_BAND_CHANGED_RESET"
    case repTargetChangedReset = "REP_TARGET_CHANGED_RESET"
    case setCountChangedReset = "SET_COUNT_CHANGED_RESET"
    case multipleSlotsInSessionMerged = "MULTIPLE_SLOTS_IN_SESSION_MERGED"
    case repeatedNotableDecline = "REPEATED_NOTABLE_DECLINE"
    case singleNotableDecline = "SINGLE_NOTABLE_DECLINE"
    case withinTrendBand = "WITHIN_TREND_BAND"
    case improvingTrend = "IMPROVING_TREND"
    case missingRpeReducesConfidence = "MISSING_RPE_REDUCES_CONFIDENCE"
    case unknownIntentionReducesConfidence = "UNKNOWN_INTENTION_REDUCES_CONFIDENCE"
    case plannedFailureSetsPresent = "PLANNED_FAILURE_SETS_PRESENT"
    case unexpectedTargetMissSetsPresent = "UNEXPECTED_TARGET_MISS_SETS_PRESENT"
    case noCheckIn = "NO_CHECK_IN"
    case partialCheckIn = "PARTIAL_CHECK_IN"
    case checkInAllNeutral = "CHECK_IN_ALL_NEUTRAL"
    case sleepBelowThreshold = "SLEEP_BELOW_THRESHOLD"
    case energyBelowThreshold = "ENERGY_BELOW_THRESHOLD"
    case stressAboveThreshold = "STRESS_ABOVE_THRESHOLD"
    case sorenessAboveThreshold = "SORENESS_ABOVE_THRESHOLD"
    case muscleTrainedToday = "MUSCLE_TRAINED_TODAY"
    case muscleHighSoreness = "MUSCLE_HIGH_SORENESS"
    case muscleHighRecentVolume = "MUSCLE_HIGH_RECENT_VOLUME"
    case muscleLowRecentVolume = "MUSCLE_LOW_RECENT_VOLUME"
    case muscleNoRecentLoad = "MUSCLE_NO_RECENT_LOAD"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILReadinessReasonCode(rawValue: raw) ?? .unknown
    }
}

struct ILDailyFormAssessment: Codable, Equatable {
    var coverage: Int
    var confidence: ILEvidenceConfidence
    var hasAnyConcern: Bool
    var signals: [ILDailyFormSignal]
    var reasons: [ILReadinessReason]
}

struct ILDailyFormSignal: Codable, Equatable {
    var dimension: ILDailyFormDimension
    var value: Int
    var state: ILDailyFormState
}

enum ILDailyFormDimension: String, Codable, Equatable, CaseIterable {
    case sleepQuality = "SLEEP_QUALITY"
    case energy = "ENERGY"
    case stress = "STRESS"
    case soreness = "SORENESS"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILDailyFormDimension(rawValue: raw) ?? .unknown
    }
}

enum ILDailyFormState: String, Codable, Equatable {
    case good = "GOOD"
    case neutral = "NEUTRAL"
    case concern = "CONCERN"
    case unknown = "UNKNOWN"

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILDailyFormState(rawValue: raw) ?? .unknown
    }
}

struct ILMuscleGroupContext: Codable, Equatable {
    var muscleGroup: String
    var lastTrainedEpochMillis: Int64?
    var setsInWindow: Double
    var setsToday: Double
    var soreness: Int?
    var flags: [ILMuscleGroupFlag]
    /// `true` when the muscle is part of today's planned session. Optional so payloads written
    /// before the shared core exposed the field still decode; `nil` means "not reported".
    var plannedToday: Bool?
}

enum ILMuscleGroupFlag: String, Codable, Equatable {
    case trainedToday = "TRAINED_TODAY"
    case highSoreness = "HIGH_SORENESS"
    case highRecentVolume = "HIGH_RECENT_VOLUME"
    case lowRecentVolume = "LOW_RECENT_VOLUME"
    case noRecentLoad = "NO_RECENT_LOAD"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILMuscleGroupFlag(rawValue: raw) ?? .unknown
    }
}

struct ILTrendDataQuality: Codable, Equatable {
    var comparableUnitCount: Int
    var analyzedExerciseCount: Int
    var exercisesWithSufficientHistory: Int
    var insufficientDataExerciseCount: Int
    var missingRpeSetCount: Int
    var unknownIntentionSetCount: Int
    var excludedDeloadUnitCount: Int
    var notes: [ILReadinessDataQualityNote]
}

enum ILReadinessDataQualityNote: String, Codable, Equatable {
    case missingRpeTreatedNeutral = "MISSING_RPE_TREATED_NEUTRAL"
    case unknownSetIntention = "UNKNOWN_SET_INTENTION"
    case unknownSetType = "UNKNOWN_SET_TYPE"
    case mixedEquipmentHistory = "MIXED_EQUIPMENT_HISTORY"
    case exerciseSlotChanged = "EXERCISE_SLOT_CHANGED"
    case goalResetDetected = "GOAL_RESET_DETECTED"
    case weightStepUnknown = "WEIGHT_STEP_UNKNOWN"
    case deloadUnitsExcluded = "DELOAD_UNITS_EXCLUDED"
    case insufficientHistory = "INSUFFICIENT_HISTORY"
    case noValidWorkSets = "NO_VALID_WORK_SETS"
    case comparabilityBreak = "COMPARABILITY_BREAK"
    case multipleSlotsInSession = "MULTIPLE_SLOTS_IN_SESSION"
    case repTargetChanged = "REP_TARGET_CHANGED"
    case unknown

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILReadinessDataQualityNote(rawValue: raw) ?? .unknown
    }
}

// MARK: - Stored readiness side channel

/// Swift value representation of `shared.readinessdata.ReadinessData`.
///
/// A schema-12 payload has no `readinessData` key at all, so it decodes to the
/// empty document: no check-ins and every set intention stays `UNKNOWN`.
struct ILReadinessData: Codable, Equatable {
    var formatVersion: Int
    var schemaVersion: Int
    var checkIns: [ILReadinessCheckIn]
    var setIntentions: [ILSetIntentionRecord]

    static let empty = ILReadinessData(
        formatVersion: 1,
        schemaVersion: 1,
        checkIns: [],
        setIntentions: []
    )

    func checkIn(for localDate: String) -> ILReadinessCheckIn? {
        checkIns.first { $0.localDate == localDate }
    }

    /// Absence of a record always means "unknown", never "normal" or "failure".
    func intention(forSetId setId: Int64) -> ILSetIntention {
        setIntentions.first { $0.setId == setId }?.intention ?? .unknown
    }
}

struct ILReadinessCheckIn: Codable, Equatable {
    var localDate: String
    var sleepQuality: Int?
    var energy: Int?
    var stress: Int?
    var muscleSoreness: [String: Int]
    var recordedAtEpochMillis: Int64?
    var updatedAtEpochMillis: Int64?

    init(
        localDate: String,
        sleepQuality: Int? = nil,
        energy: Int? = nil,
        stress: Int? = nil,
        muscleSoreness: [String: Int] = [:],
        recordedAtEpochMillis: Int64? = nil,
        updatedAtEpochMillis: Int64? = nil
    ) {
        self.localDate = localDate
        self.sleepQuality = sleepQuality
        self.energy = energy
        self.stress = stress
        self.muscleSoreness = muscleSoreness
        self.recordedAtEpochMillis = recordedAtEpochMillis
        self.updatedAtEpochMillis = updatedAtEpochMillis
    }

    private enum CodingKeys: String, CodingKey {
        case localDate, sleepQuality, energy, stress, muscleSoreness
        case recordedAtEpochMillis, updatedAtEpochMillis
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        localDate = try c.decode(String.self, forKey: .localDate)
        sleepQuality = try c.decodeIfPresent(Int.self, forKey: .sleepQuality)
        energy = try c.decodeIfPresent(Int.self, forKey: .energy)
        stress = try c.decodeIfPresent(Int.self, forKey: .stress)
        muscleSoreness = try c.decodeIfPresent([String: Int].self, forKey: .muscleSoreness) ?? [:]
        recordedAtEpochMillis = try c.decodeIfPresent(Int64.self, forKey: .recordedAtEpochMillis)
        updatedAtEpochMillis = try c.decodeIfPresent(Int64.self, forKey: .updatedAtEpochMillis)
    }

    var hasAnyAnswer: Bool {
        sleepQuality != nil || energy != nil || stress != nil || !muscleSoreness.isEmpty
    }
}

enum ILSetIntention: String, Codable, Equatable, CaseIterable {
    case plannedFailure = "PLANNED_FAILURE"
    case unexpectedTargetMiss = "UNEXPECTED_TARGET_MISS"
    case unknown = "UNKNOWN"

    init(from decoder: Decoder) throws {
        let raw = (try? decoder.singleValueContainer().decode(String.self)) ?? ""
        self = ILSetIntention(rawValue: raw) ?? .unknown
    }
}

struct ILSetIntentionRecord: Codable, Equatable {
    var setId: Int64
    var intention: ILSetIntention
    var recordedAtEpochMillis: Int64?
    var note: String
}

// MARK: - Presentation helpers

/// The ten product muscle groups, in the same order Android and the shared
/// data layer use them.
let ilReadinessMuscleGroups: [String] = [
    "BRUST",
    "RUECKEN",
    "BEINE",
    "SCHULTERN",
    "BIZEPS",
    "TRIZEPS",
    "GESAESS",
    "CORE",
    "UNTERARME",
    "WADEN",
]

func ilTrendStatusText(_ status: ILTrainingTrendStatus) -> String {
    switch status {
    case .insufficientData: return "Noch nicht genug vergleichbare Einheiten"
    case .noNotableStrain: return "Keine wiederholte Verschlechterung erkannt"
    case .singleExerciseDecline: return "Eine Übung verschlechtert sich wiederholt"
    case .multipleExerciseDecline: return "Mehrere Übungen verschlechtern sich wiederholt"
    case .unknown: return "Unbekannter Status"
    }
}

func ilConfidenceText(_ confidence: ILEvidenceConfidence) -> String {
    switch confidence {
    case .none: return "Keine Evidenz"
    case .low: return "Geringe Evidenz"
    case .moderate: return "Mittlere Evidenz"
    case .high: return "Hohe Evidenz"
    case .unknown: return "Unbekannte Evidenz"
    }
}

func ilExerciseTrendStatusText(_ status: ILExerciseTrendStatus) -> String {
    switch status {
    case .improving: return "Verbessert"
    case .stable: return "Stabil"
    case .declining: return "Verschlechtert"
    case .insufficientData: return "Zu wenig Historie"
    case .excluded: return "Ausgeschlossen"
    case .unknown: return "Unbekannt"
    }
}

func ilDailyFormDimensionText(_ dimension: ILDailyFormDimension) -> String {
    switch dimension {
    case .sleepQuality: return "Schlaf"
    case .energy: return "Energie"
    case .stress: return "Stress"
    case .soreness: return "Muskelkater"
    case .unknown: return "Unbekannt"
    }
}

func ilDailyFormStateText(_ state: ILDailyFormState) -> String {
    switch state {
    case .good: return "gut"
    case .neutral: return "neutral"
    case .concern: return "auffällig"
    case .unknown: return "nicht bewertet"
    }
}

func ilMuscleFlagText(_ flag: ILMuscleGroupFlag) -> String {
    switch flag {
    case .trainedToday: return "heute trainiert"
    case .highSoreness: return "hoher Muskelkater"
    case .highRecentVolume: return "viel Volumen"
    case .lowRecentVolume: return "wenig Volumen"
    case .noRecentLoad: return "keine aktuelle Belastung"
    case .unknown: return "unbekannt"
    }
}

func ilDataQualityNoteText(_ note: ILReadinessDataQualityNote) -> String {
    switch note {
    case .missingRpeTreatedNeutral: return "Fehlende RPE neutral behandelt"
    case .unknownSetIntention: return "Satzabsicht unbekannt"
    case .unknownSetType: return "Satztyp unbekannt"
    case .mixedEquipmentHistory: return "Gerätewechsel in der Historie"
    case .exerciseSlotChanged: return "Übungsplatz geändert"
    case .goalResetDetected: return "Ziel zurückgesetzt"
    case .weightStepUnknown: return "Gewichtsschritt unbekannt"
    case .deloadUnitsExcluded: return "Deload-Einheiten ausgeschlossen"
    case .insufficientHistory: return "Zu wenig Vergleichshistorie"
    case .noValidWorkSets: return "Keine verwertbaren Arbeitssätze"
    case .comparabilityBreak: return "Vergleichbarkeit unterbrochen"
    case .multipleSlotsInSession: return "Mehrere Übungsplätze in einer Einheit"
    case .repTargetChanged: return "Wiederholungsziel geändert"
    case .unknown: return "Unbekannter Datenhinweis"
    }
}

func ilSetIntentionText(_ intention: ILSetIntention) -> String {
    switch intention {
    case .plannedFailure: return "Geplantes Versagen"
    case .unexpectedTargetMiss: return "Ziel unerwartet verfehlt"
    case .unknown: return "Nicht angegeben"
    }
}

/// Renders a reason code for the UI. The core deliberately emits codes instead of
/// prose; a code this build does not know yet stays visible as "unbekannt".
func ilReadinessReasonText(_ reason: ILReadinessReason) -> String {
    switch reason.code {
    case .insufficientHistory: return "Zu wenig Vergleichsdaten"
    case .noNotableStrain: return "Keine wiederholte Verschlechterung"
    case .singleExerciseDecline: return "Eine Übung fällt wiederholt ab"
    case .multipleExerciseDeclines: return "Mehrere Übungen fallen wiederholt ab"
    case .deloadInProgress: return "Deload aktiv"
    case .deloadSuggested: return "Deload vorgeschlagen"
    case .targetRpeApplied: return "Ziel-RPE berücksichtigt"
    case .stagnationNeutral: return "Stagnation allein gilt nicht als Ermüdung"
    case .plannedFailureNeutral: return "Geplantes Versagen zählt nicht als Ermüdung"
    case .plannedFailureUnitNeutral: return "Einheit mit geplantem Versagen neutral behandelt"
    case .deloadContextNeutral: return "Deload-Kontext ausgeschlossen"
    case .missingRpeNeutral: return "Fehlende RPE verändert den Trend nicht"
    case .unknownIntentionPresent: return "Unbekannte Satzabsicht vorhanden"
    case .unexpectedTargetMissPresent: return "Ziel wurde unerwartet verfehlt"
    case .comparableUnitsBelowMinimum: return "Zu wenige vergleichbare Einheiten"
    case .noValidWorkSets: return "Keine gültigen Arbeitssätze"
    case .deloadUnitsExcluded: return "Deload-Einheiten ausgeschlossen"
    case .linearLoadScheme: return "Lineare Laststeigerung"
    case .doubleProgressionScheme: return "Doppelte Progression"
    case .totalRepsScheme: return "Gesamtwiederholungen"
    case .equipmentChangedReset: return "Gerätewechsel: neue Vergleichsreihe"
    case .slotChangedReset: return "Übungsplatz geändert: neue Reihe"
    case .goalChangedReset: return "Ziel geändert: neue Reihe"
    case .longGapReset: return "Lange Pause: neue Reihe"
    case .metricKindChangedReset: return "Vergleichsmaßstab geändert: neue Reihe"
    case .repBandChangedReset: return "Wiederholungsband geändert: neue Reihe"
    case .repTargetChangedReset: return "Wiederholungsziel geändert: neue Reihe"
    case .setCountChangedReset: return "Arbeitssatzanzahl geändert: neue Reihe"
    case .multipleSlotsInSessionMerged: return "Mehrere Übungsplätze je Einheit zusammengeführt"
    case .repeatedNotableDecline: return "Wiederholter Rückgang"
    case .singleNotableDecline: return "Einmaliger Rückgang"
    case .withinTrendBand: return "Im neutralen Trendband"
    case .improvingTrend: return "Aufwärtstrend"
    case .missingRpeReducesConfidence: return "Fehlende RPE senkt die Aussagekraft"
    case .unknownIntentionReducesConfidence: return "Unbekannte Absicht senkt die Aussagekraft"
    case .plannedFailureSetsPresent: return "Geplante Versagessätze vorhanden"
    case .unexpectedTargetMissSetsPresent: return "Sätze mit verfehltem Ziel vorhanden"
    case .noCheckIn: return "Kein Check-in"
    case .partialCheckIn: return "Teilweiser Check-in"
    case .checkInAllNeutral: return "Check-in durchweg neutral"
    case .sleepBelowThreshold: return "Schlaf unter dem Schwellenwert"
    case .energyBelowThreshold: return "Energie unter dem Schwellenwert"
    case .stressAboveThreshold: return "Stress über dem Schwellenwert"
    case .sorenessAboveThreshold: return "Muskelkater über dem Schwellenwert"
    case .muscleTrainedToday: return "Heute trainiert"
    case .muscleHighSoreness: return "Hoher Muskelkater"
    case .muscleHighRecentVolume: return "Viel Volumen im Fenster"
    case .muscleLowRecentVolume: return "Wenig Volumen im Fenster"
    case .muscleNoRecentLoad: return "Keine aktuelle Belastung"
    case .unknown: return "Unbekannter Grund"
    }
}

// MARK: - Derived presentation helpers

extension ILTrainingTrendAssessment {
    /// The exercise with the largest recorded decline, or `nil` when none is declining.
    var strongestDecline: ILExerciseTrend? {
        exercises
            .filter { $0.status == .declining && ($0.changePercent ?? 0) < 0 }
            .min { ($0.changePercent ?? 0) < ($1.changePercent ?? 0) }
    }
}

/// The caller's local calendar date in the wire format the shared readiness contract
/// expects. The core deliberately has no clock, so "today" is always supplied here.
func ilCurrentLocalDate(_ now: Date = Date(), calendar: Calendar = .current) -> String {
    let formatter = DateFormatter()
    formatter.calendar = calendar
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.timeZone = calendar.timeZone
    formatter.dateFormat = "yyyy-MM-dd"
    return formatter.string(from: now)
}

/// Remembers the local calendar day the UI last presented and reports when it moves on.
///
/// The dashboard re-reads the date on every check (timer tick and scene re-activation)
/// instead of assuming midnight was crossed while the app was suspended. Keeping that
/// comparison in one small value type makes the rule testable without a running view.
struct ILLocalDayGuard {
    private(set) var currentDate: String

    init(currentDate: String = ilCurrentLocalDate()) {
        self.currentDate = currentDate
    }

    /// Returns `true` only when `date` differs from the last observed day, and advances
    /// the guard to it. Re-observing the same day never reports a change.
    mutating func observe(_ date: String = ilCurrentLocalDate()) -> Bool {
        guard date != currentDate else { return false }
        currentDate = date
        return true
    }
}

/// Human-readable date for a stored `yyyy-MM-dd` check-in key.
func ilCheckInDateText(_ localDate: String) -> String {
    ilAnalyticsDateText(localDate) ?? localDate
}

/// Label for one value of a 1...5 check-in dimension. Stress and soreness are better
/// when low, so the wording is explicit per dimension instead of a bare number.
func ilCheckInScaleText(_ value: Int, dimension: ILDailyFormDimension) -> String {
    switch dimension {
    case .sleepQuality:
        switch value {
        case 1: return "1 · sehr schlecht"
        case 2: return "2 · schlecht"
        case 3: return "3 · mittel"
        case 4: return "4 · gut"
        default: return "5 · sehr gut"
        }
    case .energy:
        switch value {
        case 1: return "1 · sehr niedrig"
        case 2: return "2 · niedrig"
        case 3: return "3 · mittel"
        case 4: return "4 · hoch"
        default: return "5 · sehr hoch"
        }
    case .stress, .soreness:
        switch value {
        case 1: return "1 · sehr niedrig"
        case 2: return "2 · niedrig"
        case 3: return "3 · mittel"
        case 4: return "4 · hoch"
        default: return "5 · sehr hoch"
        }
    case .unknown:
        return "\(value)/5"
    }
}
