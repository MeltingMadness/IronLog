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
    case .insufficientData: return String(localized: "Noch nicht genug vergleichbare Einheiten")
    case .noNotableStrain: return String(localized: "Keine wiederholte Verschlechterung erkannt")
    case .singleExerciseDecline: return String(localized: "Eine Übung verschlechtert sich wiederholt")
    case .multipleExerciseDecline: return String(localized: "Mehrere Übungen verschlechtern sich wiederholt")
    case .unknown: return String(localized: "Unbekannter Status")
    }
}

func ilConfidenceText(_ confidence: ILEvidenceConfidence) -> String {
    switch confidence {
    case .none: return String(localized: "Keine Evidenz")
    case .low: return String(localized: "Geringe Evidenz")
    case .moderate: return String(localized: "Mittlere Evidenz")
    case .high: return String(localized: "Hohe Evidenz")
    case .unknown: return String(localized: "Unbekannte Evidenz")
    }
}

func ilExerciseTrendStatusText(_ status: ILExerciseTrendStatus) -> String {
    switch status {
    case .improving: return String(localized: "Verbessert")
    case .stable: return String(localized: "Stabil")
    case .declining: return String(localized: "Verschlechtert")
    case .insufficientData: return String(localized: "Zu wenig Historie")
    case .excluded: return String(localized: "Ausgeschlossen")
    case .unknown: return String(localized: "Unbekannt")
    }
}

func ilDailyFormDimensionText(_ dimension: ILDailyFormDimension) -> String {
    switch dimension {
    case .sleepQuality: return String(localized: "Schlaf")
    case .energy: return String(localized: "Energie")
    case .stress: return String(localized: "Stress")
    case .soreness: return String(localized: "Muskelkater")
    case .unknown: return String(localized: "Unbekannt")
    }
}

func ilDailyFormStateText(_ state: ILDailyFormState) -> String {
    switch state {
    case .good: return String(localized: "gut")
    case .neutral: return String(localized: "neutral")
    case .concern: return String(localized: "auffällig")
    case .unknown: return String(localized: "nicht bewertet")
    }
}

func ilMuscleFlagText(_ flag: ILMuscleGroupFlag) -> String {
    switch flag {
    case .trainedToday: return String(localized: "heute trainiert")
    case .highSoreness: return String(localized: "hoher Muskelkater")
    case .highRecentVolume: return String(localized: "viel Volumen")
    case .lowRecentVolume: return String(localized: "wenig Volumen")
    case .noRecentLoad: return String(localized: "keine aktuelle Belastung")
    case .unknown: return String(localized: "unbekannt")
    }
}

func ilDataQualityNoteText(_ note: ILReadinessDataQualityNote) -> String {
    switch note {
    case .missingRpeTreatedNeutral: return String(localized: "Fehlende RPE neutral behandelt")
    case .unknownSetIntention: return String(localized: "Satzabsicht unbekannt")
    case .unknownSetType: return String(localized: "Satztyp unbekannt")
    case .mixedEquipmentHistory: return String(localized: "Gerätewechsel in der Historie")
    case .exerciseSlotChanged: return String(localized: "Übungsplatz geändert")
    case .goalResetDetected: return String(localized: "Ziel zurückgesetzt")
    case .weightStepUnknown: return String(localized: "Gewichtsschritt unbekannt")
    case .deloadUnitsExcluded: return String(localized: "Deload-Einheiten ausgeschlossen")
    case .insufficientHistory: return String(localized: "Zu wenig Vergleichshistorie")
    case .noValidWorkSets: return String(localized: "Keine verwertbaren Arbeitssätze")
    case .comparabilityBreak: return String(localized: "Vergleichbarkeit unterbrochen")
    case .multipleSlotsInSession: return String(localized: "Mehrere Übungsplätze in einer Einheit")
    case .repTargetChanged: return String(localized: "Wiederholungsziel geändert")
    case .unknown: return String(localized: "Unbekannter Datenhinweis")
    }
}

func ilSetIntentionText(_ intention: ILSetIntention) -> String {
    switch intention {
    case .plannedFailure: return String(localized: "Geplantes Versagen")
    case .unexpectedTargetMiss: return String(localized: "Ziel unerwartet verfehlt")
    case .unknown: return String(localized: "Nicht angegeben")
    }
}

/// Renders a reason code for the UI. The core deliberately emits codes instead of
/// prose; a code this build does not know yet stays visible as "unbekannt".
func ilReadinessReasonText(_ reason: ILReadinessReason) -> String {
    switch reason.code {
    case .insufficientHistory: return String(localized: "Zu wenig Vergleichsdaten")
    case .noNotableStrain: return String(localized: "Keine wiederholte Verschlechterung")
    case .singleExerciseDecline: return String(localized: "Eine Übung fällt wiederholt ab")
    case .multipleExerciseDeclines: return String(localized: "Mehrere Übungen fallen wiederholt ab")
    case .deloadInProgress: return String(localized: "Deload aktiv")
    case .deloadSuggested: return String(localized: "Deload vorgeschlagen")
    case .targetRpeApplied: return String(localized: "Ziel-RPE berücksichtigt")
    case .stagnationNeutral: return String(localized: "Stagnation allein gilt nicht als Ermüdung")
    case .plannedFailureNeutral: return String(localized: "Geplantes Versagen zählt nicht als Ermüdung")
    case .plannedFailureUnitNeutral: return String(localized: "Einheit mit geplantem Versagen neutral behandelt")
    case .deloadContextNeutral: return String(localized: "Deload-Kontext ausgeschlossen")
    case .missingRpeNeutral: return String(localized: "Fehlende RPE verändert den Trend nicht")
    case .unknownIntentionPresent: return String(localized: "Unbekannte Satzabsicht vorhanden")
    case .unexpectedTargetMissPresent: return String(localized: "Ziel wurde unerwartet verfehlt")
    case .comparableUnitsBelowMinimum: return String(localized: "Zu wenige vergleichbare Einheiten")
    case .noValidWorkSets: return String(localized: "Keine gültigen Arbeitssätze")
    case .deloadUnitsExcluded: return String(localized: "Deload-Einheiten ausgeschlossen")
    case .linearLoadScheme: return String(localized: "Lineare Laststeigerung")
    case .doubleProgressionScheme: return String(localized: "Doppelte Progression")
    case .totalRepsScheme: return String(localized: "Gesamtwiederholungen")
    case .equipmentChangedReset: return String(localized: "Gerätewechsel: neue Vergleichsreihe")
    case .slotChangedReset: return String(localized: "Übungsplatz geändert: neue Reihe")
    case .goalChangedReset: return String(localized: "Ziel geändert: neue Reihe")
    case .longGapReset: return String(localized: "Lange Pause: neue Reihe")
    case .metricKindChangedReset: return String(localized: "Vergleichsmaßstab geändert: neue Reihe")
    case .repBandChangedReset: return String(localized: "Wiederholungsband geändert: neue Reihe")
    case .repTargetChangedReset: return String(localized: "Wiederholungsziel geändert: neue Reihe")
    case .setCountChangedReset: return String(localized: "Arbeitssatzanzahl geändert: neue Reihe")
    case .multipleSlotsInSessionMerged: return String(localized: "Mehrere Übungsplätze je Einheit zusammengeführt")
    case .repeatedNotableDecline: return String(localized: "Wiederholter Rückgang")
    case .singleNotableDecline: return String(localized: "Einmaliger Rückgang")
    case .withinTrendBand: return String(localized: "Im neutralen Trendband")
    case .improvingTrend: return String(localized: "Aufwärtstrend")
    case .missingRpeReducesConfidence: return String(localized: "Fehlende RPE senkt die Aussagekraft")
    case .unknownIntentionReducesConfidence: return String(localized: "Unbekannte Absicht senkt die Aussagekraft")
    case .plannedFailureSetsPresent: return String(localized: "Geplante Versagessätze vorhanden")
    case .unexpectedTargetMissSetsPresent: return String(localized: "Sätze mit verfehltem Ziel vorhanden")
    case .noCheckIn: return String(localized: "Kein Check-in")
    case .partialCheckIn: return String(localized: "Teilweiser Check-in")
    case .checkInAllNeutral: return String(localized: "Check-in durchweg neutral")
    case .sleepBelowThreshold: return String(localized: "Schlaf unter dem Schwellenwert")
    case .energyBelowThreshold: return String(localized: "Energie unter dem Schwellenwert")
    case .stressAboveThreshold: return String(localized: "Stress über dem Schwellenwert")
    case .sorenessAboveThreshold: return String(localized: "Muskelkater über dem Schwellenwert")
    case .muscleTrainedToday: return String(localized: "Heute trainiert")
    case .muscleHighSoreness: return String(localized: "Hoher Muskelkater")
    case .muscleHighRecentVolume: return String(localized: "Viel Volumen im Fenster")
    case .muscleLowRecentVolume: return String(localized: "Wenig Volumen im Fenster")
    case .muscleNoRecentLoad: return String(localized: "Keine aktuelle Belastung")
    case .unknown: return String(localized: "Unbekannter Grund")
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
        case 1: return String(localized: "1 · sehr schlecht")
        case 2: return String(localized: "2 · schlecht")
        case 3: return String(localized: "3 · mittel")
        case 4: return String(localized: "4 · gut")
        default: return String(localized: "5 · sehr gut")
        }
    case .energy:
        switch value {
        case 1: return String(localized: "1 · sehr niedrig")
        case 2: return String(localized: "2 · niedrig")
        case 3: return String(localized: "3 · mittel")
        case 4: return String(localized: "4 · hoch")
        default: return String(localized: "5 · sehr hoch")
        }
    case .stress, .soreness:
        switch value {
        case 1: return String(localized: "1 · sehr niedrig")
        case 2: return String(localized: "2 · niedrig")
        case 3: return String(localized: "3 · mittel")
        case 4: return String(localized: "4 · hoch")
        default: return String(localized: "5 · sehr hoch")
        }
    case .unknown:
        return "\(value)/5"
    }
}
