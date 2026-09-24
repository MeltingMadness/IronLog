import Foundation

/// Swift transport representation of `shared.analytics.TrainingAnalytics`.
///
/// The shared layer owns all aggregation and threshold decisions. These types
/// intentionally keep the wire names and nullable values visible so a missing
/// score cannot become a fabricated zero or a perfect readiness value.
struct ILTrainingAnalytics: Codable, Equatable, Sendable {
    var generatedAtEpochMillis: Int64
    var timeZoneId: String
    var weekStartsSunday: Bool
    var currentWeekStart: String
    var workoutsThisWeek: Int
    var workoutsThisMonth: Int
    var lastSessionDate: String?
    var recentRecords: [ILTrainingAnalyticsRecord]
    var muscleVolumes: [ILTrainingMuscleVolume]
    var weeklyVolume: [ILTrainingVolumeBin]
    var readiness: ILTrainingReadiness
    var metaRotationSuggestions: [ILMetaRotationSuggestion]
    /// Per-exercise history emitted by the shared analytics projection.
    var exerciseStatistics: [ILExerciseAnalytics]

    init(
        generatedAtEpochMillis: Int64 = 0,
        timeZoneId: String = TimeZone.current.identifier,
        weekStartsSunday: Bool = false,
        currentWeekStart: String = "",
        workoutsThisWeek: Int = 0,
        workoutsThisMonth: Int = 0,
        lastSessionDate: String? = nil,
        recentRecords: [ILTrainingAnalyticsRecord] = [],
        muscleVolumes: [ILTrainingMuscleVolume] = [],
        weeklyVolume: [ILTrainingVolumeBin] = [],
        readiness: ILTrainingReadiness = .empty,
        metaRotationSuggestions: [ILMetaRotationSuggestion] = [],
        exerciseStatistics: [ILExerciseAnalytics] = []
    ) {
        self.generatedAtEpochMillis = generatedAtEpochMillis
        self.timeZoneId = timeZoneId
        self.weekStartsSunday = weekStartsSunday
        self.currentWeekStart = currentWeekStart
        self.workoutsThisWeek = workoutsThisWeek
        self.workoutsThisMonth = workoutsThisMonth
        self.lastSessionDate = lastSessionDate
        self.recentRecords = recentRecords
        self.muscleVolumes = muscleVolumes
        self.weeklyVolume = weeklyVolume
        self.readiness = readiness
        self.metaRotationSuggestions = metaRotationSuggestions
        self.exerciseStatistics = exerciseStatistics
    }

    private enum CodingKeys: String, CodingKey {
        case generatedAtEpochMillis
        case timeZoneId
        case weekStartsSunday
        case currentWeekStart
        case workoutsThisWeek
        case workoutsThisMonth
        case lastSessionDate
        case recentRecords
        case muscleVolumes
        case weeklyVolume
        case readiness
        case metaRotationSuggestions
        case exerciseStatistics
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        generatedAtEpochMillis = try c.decode(Int64.self, forKey: .generatedAtEpochMillis)
        timeZoneId = try c.decode(String.self, forKey: .timeZoneId)
        weekStartsSunday = try c.decode(Bool.self, forKey: .weekStartsSunday)
        currentWeekStart = try c.decode(String.self, forKey: .currentWeekStart)
        workoutsThisWeek = try c.decode(Int.self, forKey: .workoutsThisWeek)
        workoutsThisMonth = try c.decode(Int.self, forKey: .workoutsThisMonth)
        // `lastSessionDate` is nullable but has no Kotlin default. A null
        // value is valid; an omitted key is a malformed analytics response.
        lastSessionDate = try c.decode(String?.self, forKey: .lastSessionDate)
        recentRecords = try c.decode([ILTrainingAnalyticsRecord].self, forKey: .recentRecords)
        muscleVolumes = try c.decode([ILTrainingMuscleVolume].self, forKey: .muscleVolumes)
        weeklyVolume = try c.decode([ILTrainingVolumeBin].self, forKey: .weeklyVolume)
        readiness = try c.decode(ILTrainingReadiness.self, forKey: .readiness)
        metaRotationSuggestions = try c.decode([ILMetaRotationSuggestion].self, forKey: .metaRotationSuggestions)
        // `TrainingAnalytics.exerciseStatistics` has no Kotlin default. Keep
        // this field required so a truncated analytics response fails closed
        // instead of silently presenting an incomplete dashboard.
        exerciseStatistics = try c.decode([ILExerciseAnalytics].self, forKey: .exerciseStatistics)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(generatedAtEpochMillis, forKey: .generatedAtEpochMillis)
        try c.encode(timeZoneId, forKey: .timeZoneId)
        try c.encode(weekStartsSunday, forKey: .weekStartsSunday)
        try c.encode(currentWeekStart, forKey: .currentWeekStart)
        try c.encode(workoutsThisWeek, forKey: .workoutsThisWeek)
        try c.encode(workoutsThisMonth, forKey: .workoutsThisMonth)
        try c.encode(lastSessionDate, forKey: .lastSessionDate)
        try c.encode(recentRecords, forKey: .recentRecords)
        try c.encode(muscleVolumes, forKey: .muscleVolumes)
        try c.encode(weeklyVolume, forKey: .weeklyVolume)
        try c.encode(readiness, forKey: .readiness)
        try c.encode(metaRotationSuggestions, forKey: .metaRotationSuggestions)
        try c.encode(exerciseStatistics, forKey: .exerciseStatistics)
    }
}

struct ILTrainingAnalyticsRecord: Codable, Equatable, Identifiable, Sendable {
    var id: Int64
    var exerciseId: Int64
    var exerciseName: String?
    var type: String
    var value: Double
    var achievedAtEpochMillis: Int64
    var achievedAt: String

    init(
        id: Int64,
        exerciseId: Int64,
        exerciseName: String?,
        type: String,
        value: Double,
        achievedAtEpochMillis: Int64,
        achievedAt: String
    ) {
        self.id = id
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.type = type
        self.value = value
        self.achievedAtEpochMillis = achievedAtEpochMillis
        self.achievedAt = achievedAt
    }

    private enum CodingKeys: String, CodingKey {
        case id, exerciseId, exerciseName, type, value, achievedAtEpochMillis, achievedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        exerciseId = try c.decode(Int64.self, forKey: .exerciseId)
        // Nullable in Kotlin, but without a default: the key itself is
        // required and may carry JSON null.
        exerciseName = try c.decode(String?.self, forKey: .exerciseName)
        type = try c.decode(String.self, forKey: .type)
        value = try c.decode(Double.self, forKey: .value)
        achievedAtEpochMillis = try c.decode(Int64.self, forKey: .achievedAtEpochMillis)
        achievedAt = try c.decode(String.self, forKey: .achievedAt)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(exerciseId, forKey: .exerciseId)
        try c.encode(exerciseName, forKey: .exerciseName)
        try c.encode(type, forKey: .type)
        try c.encode(value, forKey: .value)
        try c.encode(achievedAtEpochMillis, forKey: .achievedAtEpochMillis)
        try c.encode(achievedAt, forKey: .achievedAt)
    }
}

struct ILTrainingMuscleVolume: Codable, Equatable, Identifiable, Sendable {
    var muscleGroup: String
    var weeklySets: Double
    var mev: Double
    var mav: Double
    var mrv: Double
    var status: ILTrainingVolumeStatus

    var id: String { muscleGroup }
}

enum ILTrainingVolumeStatus: Codable, Equatable, Sendable {
    case low
    case optimal
    case high
    case unknown(String)

    var rawValue: String {
        switch self {
        case .low: "LOW"
        case .optimal: "OPTIMAL"
        case .high: "HIGH"
        case .unknown(let value): value
        }
    }

    init(rawValue: String) {
        switch rawValue {
        case "LOW": self = .low
        case "OPTIMAL": self = .optimal
        case "HIGH": self = .high
        default: self = .unknown(rawValue)
        }
    }

    init(from decoder: Decoder) throws {
        self.init(rawValue: try decoder.singleValueContainer().decode(String.self))
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

struct ILTrainingVolumeBin: Codable, Equatable, Identifiable, Sendable {
    var weekStart: String
    var volumeKg: Double
    var workoutCount: Int

    var id: String { weekStart }
}

/// Muscle-volume projection for one navigable calendar week.
///
/// This payload is intentionally separate from `ILTrainingAnalytics`: moving
/// through history must not replace current-week counts, readiness, or the
/// eight-week trend shown by the dashboard summary.
struct ILTrainingWeeklyMuscleVolume: Codable, Equatable, Sendable {
    var weekStart: String
    var currentWeekStart: String
    var weekStartsSunday: Bool
    var timeZoneId: String
    var completedWorkoutCount: Int
    var volumes: [ILTrainingMuscleVolume]
}

struct ILTrainingReadiness: Codable, Equatable, Sendable {
    var status: ILTrainingReadinessStatus
    var fatigueScore: Int?
    var readinessScore: Int?
    var deloadRecommended: Bool
    var signals: [ILTrainingReadinessSignal]
    var windowStart: String
    var windowEnd: String
    var sessionCount: Int
    var minimumSessionCount: Int
    var analysisWindowWeeks: Int
    var strongestExerciseId: Int64?
    var strongestExerciseChangePercent: Double?
    var averageRpe: Double?
    var failureRate: Double
    var analyzedCompoundCount: Int

    init(
        status: ILTrainingReadinessStatus,
        fatigueScore: Int?,
        readinessScore: Int?,
        deloadRecommended: Bool,
        signals: [ILTrainingReadinessSignal],
        windowStart: String,
        windowEnd: String,
        sessionCount: Int,
        minimumSessionCount: Int,
        analysisWindowWeeks: Int,
        strongestExerciseId: Int64?,
        strongestExerciseChangePercent: Double?,
        averageRpe: Double?,
        failureRate: Double,
        analyzedCompoundCount: Int
    ) {
        self.status = status
        self.fatigueScore = fatigueScore
        self.readinessScore = readinessScore
        self.deloadRecommended = deloadRecommended
        self.signals = signals
        self.windowStart = windowStart
        self.windowEnd = windowEnd
        self.sessionCount = sessionCount
        self.minimumSessionCount = minimumSessionCount
        self.analysisWindowWeeks = analysisWindowWeeks
        self.strongestExerciseId = strongestExerciseId
        self.strongestExerciseChangePercent = strongestExerciseChangePercent
        self.averageRpe = averageRpe
        self.failureRate = failureRate
        self.analyzedCompoundCount = analyzedCompoundCount
    }

    private enum CodingKeys: String, CodingKey {
        case status, fatigueScore, readinessScore, deloadRecommended, signals
        case windowStart, windowEnd, sessionCount, minimumSessionCount, analysisWindowWeeks
        case strongestExerciseId, strongestExerciseChangePercent, averageRpe
        case failureRate, analyzedCompoundCount
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        status = try c.decode(ILTrainingReadinessStatus.self, forKey: .status)
        // All nullable readiness scores are required keys in Kotlin; null is
        // meaningful and must remain distinct from an omitted field.
        fatigueScore = try c.decode(Int?.self, forKey: .fatigueScore)
        readinessScore = try c.decode(Int?.self, forKey: .readinessScore)
        deloadRecommended = try c.decode(Bool.self, forKey: .deloadRecommended)
        signals = try c.decode([ILTrainingReadinessSignal].self, forKey: .signals)
        windowStart = try c.decode(String.self, forKey: .windowStart)
        windowEnd = try c.decode(String.self, forKey: .windowEnd)
        sessionCount = try c.decode(Int.self, forKey: .sessionCount)
        minimumSessionCount = try c.decode(Int.self, forKey: .minimumSessionCount)
        analysisWindowWeeks = try c.decode(Int.self, forKey: .analysisWindowWeeks)
        strongestExerciseId = try c.decode(Int64?.self, forKey: .strongestExerciseId)
        strongestExerciseChangePercent = try c.decode(Double?.self, forKey: .strongestExerciseChangePercent)
        averageRpe = try c.decode(Double?.self, forKey: .averageRpe)
        failureRate = try c.decode(Double.self, forKey: .failureRate)
        analyzedCompoundCount = try c.decode(Int.self, forKey: .analyzedCompoundCount)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(status, forKey: .status)
        try c.encode(fatigueScore, forKey: .fatigueScore)
        try c.encode(readinessScore, forKey: .readinessScore)
        try c.encode(deloadRecommended, forKey: .deloadRecommended)
        try c.encode(signals, forKey: .signals)
        try c.encode(windowStart, forKey: .windowStart)
        try c.encode(windowEnd, forKey: .windowEnd)
        try c.encode(sessionCount, forKey: .sessionCount)
        try c.encode(minimumSessionCount, forKey: .minimumSessionCount)
        try c.encode(analysisWindowWeeks, forKey: .analysisWindowWeeks)
        try c.encode(strongestExerciseId, forKey: .strongestExerciseId)
        try c.encode(strongestExerciseChangePercent, forKey: .strongestExerciseChangePercent)
        try c.encode(averageRpe, forKey: .averageRpe)
        try c.encode(failureRate, forKey: .failureRate)
        try c.encode(analyzedCompoundCount, forKey: .analyzedCompoundCount)
    }

    static let empty = ILTrainingReadiness(
        status: .insufficientData,
        fatigueScore: nil,
        readinessScore: nil,
        deloadRecommended: false,
        signals: [],
        windowStart: "",
        windowEnd: "",
        sessionCount: 0,
        minimumSessionCount: 3,
        analysisWindowWeeks: 4,
        strongestExerciseId: nil,
        strongestExerciseChangePercent: nil,
        averageRpe: nil,
        failureRate: 0,
        analyzedCompoundCount: 0
    )
}

enum ILTrainingReadinessStatus: Codable, Equatable, Sendable {
    case insufficientData
    case noNotableStrain
    case signalsPresent
    case unknown(String)

    var rawValue: String {
        switch self {
        case .insufficientData: "INSUFFICIENT_DATA"
        case .noNotableStrain: "NO_NOTABLE_STRAIN"
        case .signalsPresent: "SIGNALS_PRESENT"
        case .unknown(let value): value
        }
    }

    init(rawValue: String) {
        switch rawValue {
        case "INSUFFICIENT_DATA": self = .insufficientData
        case "NO_NOTABLE_STRAIN": self = .noNotableStrain
        case "SIGNALS_PRESENT": self = .signalsPresent
        default: self = .unknown(rawValue)
        }
    }

    init(from decoder: Decoder) throws {
        self.init(rawValue: try decoder.singleValueContainer().decode(String.self))
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }
}

enum ILTrainingReadinessSignal: String, Codable, Equatable, Sendable {
    case e1rmStagnation = "E1RM_STAGNATION"
    case e1rmDrop = "E1RM_DROP"
    case rpeCreep = "RPE_CREEP"
    case failureFrequency = "FAILURE_FREQUENCY"
}

struct ILMetaRotationSuggestion: Codable, Equatable, Identifiable, Sendable {
    var metaPlanId: Int64
    var metaPlanName: String
    var nextTrainingPlanId: Int64
    var nextTrainingPlanName: String
    var orderedTrainingPlanIds: [Int64]
    var canSkip: Bool

    var id: Int64 { metaPlanId }
}

/// Per-exercise history emitted by the shared analytics projection.
///
/// `sessions` is intentionally a value series: SwiftUI can filter and chart
/// it without reimplementing aggregation or e1RM rules in the presentation
/// layer.
struct ILExerciseAnalytics: Codable, Equatable, Identifiable, Sendable {
    var exerciseId: Int64
    var exerciseName: String?
    var sessions: [ILExerciseAnalyticsSession]
    /// Session-best e1RM progression computed by the shared analytics layer.
    var firstE1rmKg: Double
    var latestE1rmKg: Double
    var bestE1rmKg: Double
    var e1rmDeltaKg: Double
    var e1rmDeltaPercent: Double
    var lastWorkoutComparison: ILExerciseWorkoutComparison?
    /// Recent valid work sets, newest first, computed by shared analytics.
    var recentSets: [ILExerciseSetStatistics]

    init(
        exerciseId: Int64,
        exerciseName: String?,
        sessions: [ILExerciseAnalyticsSession],
        firstE1rmKg: Double = 0,
        latestE1rmKg: Double = 0,
        bestE1rmKg: Double = 0,
        e1rmDeltaKg: Double = 0,
        e1rmDeltaPercent: Double = 0,
        lastWorkoutComparison: ILExerciseWorkoutComparison? = nil,
        recentSets: [ILExerciseSetStatistics] = []
    ) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.sessions = sessions
        self.firstE1rmKg = firstE1rmKg
        self.latestE1rmKg = latestE1rmKg
        self.bestE1rmKg = bestE1rmKg
        self.e1rmDeltaKg = e1rmDeltaKg
        self.e1rmDeltaPercent = e1rmDeltaPercent
        self.lastWorkoutComparison = lastWorkoutComparison
        self.recentSets = recentSets
    }

    var id: Int64 { exerciseId }

    private enum CodingKeys: String, CodingKey {
        case exerciseId, exerciseName, sessions
        case firstE1rmKg, latestE1rmKg, bestE1rmKg, e1rmDeltaKg, e1rmDeltaPercent
        case lastWorkoutComparison, recentSets
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        exerciseId = try c.decode(Int64.self, forKey: .exerciseId)
        // Nullable in Kotlin, but without a default: require the key while
        // preserving an explicit JSON null.
        exerciseName = try c.decode(String?.self, forKey: .exerciseName)
        sessions = try c.decode([ILExerciseAnalyticsSession].self, forKey: .sessions)
        firstE1rmKg = try c.decode(Double.self, forKey: .firstE1rmKg)
        latestE1rmKg = try c.decode(Double.self, forKey: .latestE1rmKg)
        bestE1rmKg = try c.decode(Double.self, forKey: .bestE1rmKg)
        e1rmDeltaKg = try c.decode(Double.self, forKey: .e1rmDeltaKg)
        e1rmDeltaPercent = try c.decode(Double.self, forKey: .e1rmDeltaPercent)
        lastWorkoutComparison = try c.decode(ILExerciseWorkoutComparison?.self, forKey: .lastWorkoutComparison)
        recentSets = try c.decode([ILExerciseSetStatistics].self, forKey: .recentSets)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(exerciseId, forKey: .exerciseId)
        try c.encode(exerciseName, forKey: .exerciseName)
        try c.encode(sessions, forKey: .sessions)
        try c.encode(firstE1rmKg, forKey: .firstE1rmKg)
        try c.encode(latestE1rmKg, forKey: .latestE1rmKg)
        try c.encode(bestE1rmKg, forKey: .bestE1rmKg)
        try c.encode(e1rmDeltaKg, forKey: .e1rmDeltaKg)
        try c.encode(e1rmDeltaPercent, forKey: .e1rmDeltaPercent)
        try c.encode(lastWorkoutComparison, forKey: .lastWorkoutComparison)
        try c.encode(recentSets, forKey: .recentSets)
    }
}

struct ILExerciseAnalyticsSession: Codable, Equatable, Identifiable, Sendable {
    var sessionId: Int64
    var date: String
    var completedAtEpochMillis: Int64
    var maxWeightKg: Double
    var maxReps: Int
    var maxE1rmKg: Double
    var volumeKg: Double

    var id: Int64 { sessionId }
}

/// Values for the final two completed sessions of an exercise.
///
/// All deltas are emitted by the shared projection so the iOS detail view
/// cannot drift from Android's session aggregation or Epley calculation.
struct ILExerciseWorkoutComparison: Codable, Equatable, Sendable {
    var previous: ILExerciseAnalyticsSession
    var latest: ILExerciseAnalyticsSession
    var weightDeltaKg: Double
    var repsDelta: Int
    var e1rmDeltaKg: Double
    var e1rmDeltaPercent: Double
    var volumeDeltaKg: Double
}

/// One recent, valid work set for an exercise.
struct ILExerciseSetStatistics: Codable, Equatable, Identifiable, Sendable {
    var id: Int64
    var sessionId: Int64
    var date: String
    var completedAtEpochMillis: Int64
    var setType: String
    var weightKg: Double
    var reps: Int
    var e1rmKg: Double
    var volumeKg: Double
}
