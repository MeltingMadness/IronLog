import Foundation

// MARK: - Codable compatibility helpers

private extension KeyedDecodingContainer {
    /// Decode a Kotlin `@Serializable` property with a default value.
    ///
    /// `decodeIfPresent` treats an explicit JSON `null` like a missing key. Kotlin
    /// does not do that for non-nullable properties with defaults, so checking
    /// `contains` first keeps malformed payloads visible while still accepting
    /// fields that were added after an older backup was written.
    func decodeOrDefault<T: Decodable>(
        _ type: T.Type,
        forKey key: Key,
        default defaultValue: T
    ) throws -> T {
        guard contains(key) else { return defaultValue }
        return try decode(type, forKey: key)
    }
}

private func ilDate(fromEpochMillis value: Int64) -> Date {
    Date(timeIntervalSince1970: TimeInterval(value) / 1_000.0)
}

private func ilOptionalDate(fromEpochMillis value: Int64?) -> Date? {
    value.map(ilDate(fromEpochMillis:))
}

private enum ILDisplayNames {
    static func muscle(_ raw: String) -> String {
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

    static func category(_ raw: String) -> String {
        switch raw.uppercased() {
        case "LANGHANTEL": return "Langhantel"
        case "KURZHANTEL": return "Kurzhantel"
        case "MASCHINE": return "Maschine"
        case "KABEL": return "Kabel"
        case "EIGENGEWICHT": return "Eigengewicht"
        default: return raw
        }
    }

    static func scheme(_ raw: String) -> String {
        switch raw.uppercased() {
        case "MANUAL": return "Manuell"
        case "LINEAR": return "Gewicht steigern"
        case "DOUBLE": return "Wiederholungen, dann Gewicht"
        case "TOTAL_REPS": return "Gesamtwiederholungen"
        case "RPE_RIR": return "RPE/RIR"
        default: return raw
        }
    }
}

// MARK: - Payload

/// Swift value representation of `shared/.../BackupPayloadV1.kt`.
///
/// Epoch values intentionally stay as `Int64` milliseconds. This keeps the
/// transport lossless and lets callers opt into `Date` only at the UI boundary.
struct ILTrainingData: Codable, Equatable {
    var formatVersion: Int
    var schemaVersion: Int
    var appVersion: String
    var exportedAtEpochMillis: Int64
    var exercises: [ILExercise]
    var workoutSessions: [ILWorkoutSession]
    var workoutSets: [ILWorkoutSet]
    var trainingPlans: [ILTrainingPlan]
    var planExercises: [ILPlanExercise]
    var personalRecords: [ILPersonalRecord]
    var metaTrainingPlans: [ILMetaTrainingPlan]
    var metaPlanItems: [ILMetaPlanItem]
    var metaPlanSkips: [ILMetaPlanSkip]
    var workoutPlanTargets: [ILWorkoutPlanTarget]
    var progressionSuggestions: [ILProgressionSuggestion]
    /// Additive schema-13 side channel. A schema-12 payload has no key and
    /// decodes to the empty document, so nothing is invented.
    var readinessData: ILReadinessData

    init(
        formatVersion: Int = 1,
        schemaVersion: Int = 12,
        appVersion: String = "",
        exportedAtEpochMillis: Int64 = 0,
        exercises: [ILExercise] = [],
        workoutSessions: [ILWorkoutSession] = [],
        workoutSets: [ILWorkoutSet] = [],
        trainingPlans: [ILTrainingPlan] = [],
        planExercises: [ILPlanExercise] = [],
        personalRecords: [ILPersonalRecord] = [],
        metaTrainingPlans: [ILMetaTrainingPlan] = [],
        metaPlanItems: [ILMetaPlanItem] = [],
        metaPlanSkips: [ILMetaPlanSkip] = [],
        workoutPlanTargets: [ILWorkoutPlanTarget] = [],
        progressionSuggestions: [ILProgressionSuggestion] = [],
        readinessData: ILReadinessData = .empty
    ) {
        self.formatVersion = formatVersion
        self.schemaVersion = schemaVersion
        self.appVersion = appVersion
        self.exportedAtEpochMillis = exportedAtEpochMillis
        self.exercises = exercises
        self.workoutSessions = workoutSessions
        self.workoutSets = workoutSets
        self.trainingPlans = trainingPlans
        self.planExercises = planExercises
        self.personalRecords = personalRecords
        self.metaTrainingPlans = metaTrainingPlans
        self.metaPlanItems = metaPlanItems
        self.metaPlanSkips = metaPlanSkips
        self.workoutPlanTargets = workoutPlanTargets
        self.progressionSuggestions = progressionSuggestions
        self.readinessData = readinessData
    }

    private enum CodingKeys: String, CodingKey {
        case formatVersion
        case schemaVersion
        case appVersion
        case exportedAtEpochMillis
        case exercises
        case workoutSessions
        case workoutSets
        case trainingPlans
        case planExercises
        case personalRecords
        case metaTrainingPlans
        case metaPlanItems
        case metaPlanSkips
        case workoutPlanTargets
        case progressionSuggestions
        case readinessData
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        formatVersion = try c.decode(Int.self, forKey: .formatVersion)
        schemaVersion = try c.decode(Int.self, forKey: .schemaVersion)
        appVersion = try c.decode(String.self, forKey: .appVersion)
        exportedAtEpochMillis = try c.decode(Int64.self, forKey: .exportedAtEpochMillis)
        exercises = try c.decode([ILExercise].self, forKey: .exercises)
        workoutSessions = try c.decode([ILWorkoutSession].self, forKey: .workoutSessions)
        workoutSets = try c.decode([ILWorkoutSet].self, forKey: .workoutSets)
        trainingPlans = try c.decode([ILTrainingPlan].self, forKey: .trainingPlans)
        planExercises = try c.decode([ILPlanExercise].self, forKey: .planExercises)
        personalRecords = try c.decode([ILPersonalRecord].self, forKey: .personalRecords)
        metaTrainingPlans = try c.decodeOrDefault([ILMetaTrainingPlan].self, forKey: .metaTrainingPlans, default: [])
        metaPlanItems = try c.decodeOrDefault([ILMetaPlanItem].self, forKey: .metaPlanItems, default: [])
        metaPlanSkips = try c.decodeOrDefault([ILMetaPlanSkip].self, forKey: .metaPlanSkips, default: [])
        workoutPlanTargets = try c.decodeOrDefault([ILWorkoutPlanTarget].self, forKey: .workoutPlanTargets, default: [])
        progressionSuggestions = try c.decodeOrDefault([ILProgressionSuggestion].self, forKey: .progressionSuggestions, default: [])
        readinessData = try c.decodeOrDefault(ILReadinessData.self, forKey: .readinessData, default: .empty)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(formatVersion, forKey: .formatVersion)
        try c.encode(schemaVersion, forKey: .schemaVersion)
        try c.encode(appVersion, forKey: .appVersion)
        try c.encode(exportedAtEpochMillis, forKey: .exportedAtEpochMillis)
        try c.encode(exercises, forKey: .exercises)
        try c.encode(workoutSessions, forKey: .workoutSessions)
        try c.encode(workoutSets, forKey: .workoutSets)
        try c.encode(trainingPlans, forKey: .trainingPlans)
        try c.encode(planExercises, forKey: .planExercises)
        try c.encode(personalRecords, forKey: .personalRecords)
        try c.encode(metaTrainingPlans, forKey: .metaTrainingPlans)
        try c.encode(metaPlanItems, forKey: .metaPlanItems)
        try c.encode(metaPlanSkips, forKey: .metaPlanSkips)
        try c.encode(workoutPlanTargets, forKey: .workoutPlanTargets)
        try c.encode(progressionSuggestions, forKey: .progressionSuggestions)
        try c.encode(readinessData, forKey: .readinessData)
    }

    var exportedAt: Date { ilDate(fromEpochMillis: exportedAtEpochMillis) }

    /// The payload format permits at most one active session; returning the
    /// first one keeps this helper safe even while a payload is being edited.
    var activeSession: ILWorkoutSession? {
        workoutSessions.first(where: { $0.endTime == nil })
    }

    var visibleExercises: [ILExercise] {
        exercises.filter { !$0.isArchived }
    }

    func sets(for sessionID: Int64) -> [ILWorkoutSet] {
        workoutSets.filter { $0.sessionId == sessionID }
    }

    func sets(for session: ILWorkoutSession) -> [ILWorkoutSet] {
        sets(for: session.id)
    }

    func sets(forSessionID sessionID: Int64) -> [ILWorkoutSet] {
        sets(for: sessionID)
    }

    func sets(for exercise: ILExercise) -> [ILWorkoutSet] {
        workoutSets.filter { $0.exerciseId == exercise.id }
    }

    func sets(forExerciseID exerciseID: Int64) -> [ILWorkoutSet] {
        workoutSets.filter { $0.exerciseId == exerciseID }
    }
}

// MARK: - Exercise and workout rows

struct ILExercise: Codable, Equatable, Identifiable {
    var id: Int64
    var name: String
    var primaryMuscleGroup: String
    var secondaryMuscleGroups: String
    var category: String
    var isCustom: Bool
    var notes: String
    var isArchived: Bool

    init(
        id: Int64 = 0,
        name: String = "",
        primaryMuscleGroup: String = "BRUST",
        secondaryMuscleGroups: String = "",
        category: String = "LANGHANTEL",
        isCustom: Bool = false,
        notes: String = "",
        isArchived: Bool = false
    ) {
        self.id = id
        self.name = name
        self.primaryMuscleGroup = primaryMuscleGroup
        self.secondaryMuscleGroups = secondaryMuscleGroups
        self.category = category
        self.isCustom = isCustom
        self.notes = notes
        self.isArchived = isArchived
    }

    private enum CodingKeys: String, CodingKey {
        case id, name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom, notes, isArchived
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        primaryMuscleGroup = try c.decode(String.self, forKey: .primaryMuscleGroup)
        secondaryMuscleGroups = try c.decode(String.self, forKey: .secondaryMuscleGroups)
        category = try c.decode(String.self, forKey: .category)
        isCustom = try c.decode(Bool.self, forKey: .isCustom)
        notes = try c.decodeOrDefault(String.self, forKey: .notes, default: "")
        isArchived = try c.decodeOrDefault(Bool.self, forKey: .isArchived, default: false)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(primaryMuscleGroup, forKey: .primaryMuscleGroup)
        try c.encode(secondaryMuscleGroups, forKey: .secondaryMuscleGroups)
        try c.encode(category, forKey: .category)
        try c.encode(isCustom, forKey: .isCustom)
        try c.encode(notes, forKey: .notes)
        try c.encode(isArchived, forKey: .isArchived)
    }

    var primaryMuscleGroupDisplayName: String { ILDisplayNames.muscle(primaryMuscleGroup) }

    var secondaryMuscleGroupDisplayNames: [String] {
        secondaryMuscleGroups
            .split(separator: ",")
            .map { ILDisplayNames.muscle($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
            .filter { !$0.isEmpty }
    }

    var secondaryMuscleGroupDisplayName: String {
        secondaryMuscleGroupDisplayNames.joined(separator: ", ")
    }

    var categoryDisplayName: String { ILDisplayNames.category(category) }

    // Short aliases keep the DTO convenient in small SwiftUI views.
    var displayPrimaryMuscleGroup: String { primaryMuscleGroupDisplayName }
    var displaySecondaryMuscleGroups: String { secondaryMuscleGroupDisplayName }
    var displayCategory: String { categoryDisplayName }
    var primaryMuscleGroupName: String { primaryMuscleGroupDisplayName }
    var categoryName: String { categoryDisplayName }
}

struct ILWorkoutSession: Codable, Equatable, Identifiable {
    var id: Int64
    var startTime: Int64
    var endTime: Int64?
    var durationSeconds: Int64
    var name: String
    var notes: String
    var planId: Int64?
    var metaPlanId: Int64?
    /// Schema-13 deload marker: `nil` for legacy sessions ("not recorded").
    var isDeload: Bool?

    init(
        id: Int64 = 0,
        startTime: Int64 = 0,
        endTime: Int64? = nil,
        durationSeconds: Int64 = 0,
        name: String = "",
        notes: String = "",
        planId: Int64? = nil,
        metaPlanId: Int64? = nil,
        isDeload: Bool? = nil
    ) {
        self.id = id
        self.startTime = startTime
        self.endTime = endTime
        self.durationSeconds = durationSeconds
        self.name = name
        self.notes = notes
        self.planId = planId
        self.metaPlanId = metaPlanId
        self.isDeload = isDeload
    }

    private enum CodingKeys: String, CodingKey {
        case id, startTime, endTime, durationSeconds, name, notes, planId, metaPlanId, isDeload
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        startTime = try c.decode(Int64.self, forKey: .startTime)
        // `endTime` is nullable but has no Kotlin default: an older payload
        // must still carry the key (with `null` for an active session).
        endTime = try c.decode(Int64?.self, forKey: .endTime)
        durationSeconds = try c.decode(Int64.self, forKey: .durationSeconds)
        name = try c.decode(String.self, forKey: .name)
        notes = try c.decode(String.self, forKey: .notes)
        planId = try c.decodeIfPresent(Int64.self, forKey: .planId)
        metaPlanId = try c.decodeIfPresent(Int64.self, forKey: .metaPlanId)
        isDeload = try c.decodeIfPresent(Bool.self, forKey: .isDeload)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(startTime, forKey: .startTime)
        try c.encode(endTime, forKey: .endTime)
        try c.encode(durationSeconds, forKey: .durationSeconds)
        try c.encode(name, forKey: .name)
        try c.encode(notes, forKey: .notes)
        try c.encode(planId, forKey: .planId)
        try c.encode(metaPlanId, forKey: .metaPlanId)
        try c.encode(isDeload, forKey: .isDeload)
    }

    var startDate: Date { ilDate(fromEpochMillis: startTime) }
    var endDate: Date? { ilOptionalDate(fromEpochMillis: endTime) }
}

struct ILWorkoutSet: Codable, Equatable, Identifiable {
    var id: Int64
    var sessionId: Int64
    var exerciseId: Int64
    var setNumber: Int
    var reps: Int
    var weightKg: Double
    var setType: String?
    var completedAt: Int64
    var rpe: Double?
    var planTargetSnapshotId: Int64?
    /// Schema 11 and earlier compatibility field. New exports leave it nil.
    var isWarmup: Bool?

    init(
        id: Int64 = 0,
        sessionId: Int64 = 0,
        exerciseId: Int64 = 0,
        setNumber: Int = 0,
        reps: Int = 0,
        weightKg: Double = 0,
        setType: String? = nil,
        completedAt: Int64 = 0,
        rpe: Double? = nil,
        planTargetSnapshotId: Int64? = nil,
        isWarmup: Bool? = nil
    ) {
        self.id = id
        self.sessionId = sessionId
        self.exerciseId = exerciseId
        self.setNumber = setNumber
        self.reps = reps
        self.weightKg = weightKg
        self.setType = setType
        self.completedAt = completedAt
        self.rpe = rpe
        self.planTargetSnapshotId = planTargetSnapshotId
        self.isWarmup = isWarmup
    }

    private enum CodingKeys: String, CodingKey {
        case id, sessionId, exerciseId, setNumber, reps, weightKg, setType, completedAt, rpe, planTargetSnapshotId, isWarmup
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        sessionId = try c.decode(Int64.self, forKey: .sessionId)
        exerciseId = try c.decode(Int64.self, forKey: .exerciseId)
        setNumber = try c.decode(Int.self, forKey: .setNumber)
        reps = try c.decode(Int.self, forKey: .reps)
        weightKg = try c.decode(Double.self, forKey: .weightKg)
        setType = try c.decodeIfPresent(String.self, forKey: .setType)
        completedAt = try c.decode(Int64.self, forKey: .completedAt)
        rpe = try c.decodeIfPresent(Double.self, forKey: .rpe)
        planTargetSnapshotId = try c.decodeIfPresent(Int64.self, forKey: .planTargetSnapshotId)
        isWarmup = try c.decodeIfPresent(Bool.self, forKey: .isWarmup)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(sessionId, forKey: .sessionId)
        try c.encode(exerciseId, forKey: .exerciseId)
        try c.encode(setNumber, forKey: .setNumber)
        try c.encode(reps, forKey: .reps)
        try c.encode(weightKg, forKey: .weightKg)
        try c.encode(setType, forKey: .setType)
        try c.encode(completedAt, forKey: .completedAt)
        try c.encode(rpe, forKey: .rpe)
        try c.encode(planTargetSnapshotId, forKey: .planTargetSnapshotId)
        // Kotlin's @EncodeDefault(NEVER) omits the nil legacy value.
        if let isWarmup { try c.encode(isWarmup, forKey: .isWarmup) }
    }

    var completedDate: Date { ilDate(fromEpochMillis: completedAt) }

    /// Resolve schema 11's warmup flag without flattening a current set type.
    var resolvedSetType: String {
        if setType == nil, isWarmup == true { return "WARMUP" }
        return setType ?? "NORMAL"
    }

    /// A legacy flag is only meaningful for NORMAL/WARMUP records.
    var hasConflictingWarmupFlag: Bool {
        switch setType {
        case nil: return false
        case "NORMAL": return isWarmup == true
        case "WARMUP": return isWarmup == false
        case "DROP_SET", "FAILURE": return isWarmup == true
        default: return isWarmup == true
        }
    }
}

// MARK: - Plans and progression transport rows

struct ILTrainingPlan: Codable, Equatable, Identifiable {
    var id: Int64
    var name: String
    var createdAt: Int64

    init(id: Int64 = 0, name: String = "", createdAt: Int64 = 0) {
        self.id = id
        self.name = name
        self.createdAt = createdAt
    }

    private enum CodingKeys: String, CodingKey { case id, name, createdAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(createdAt, forKey: .createdAt)
    }

    var createdDate: Date { ilDate(fromEpochMillis: createdAt) }
}

struct ILPlannedSet: Codable, Equatable {
    var kind: String = "NORMAL"
    var reps: Int = 10
    var weightKg: Double = 0
}

struct ILPlanExercise: Codable, Equatable, Identifiable {
    var id: Int64
    var planId: Int64
    var exerciseId: Int64
    var orderIndex: Int
    var supersetGroupId: Int?
    var targetSets: Int
    var targetReps: Int
    var targetWeightKg: Double
    var setTargets: [ILPlannedSet]
    var progression: ILProgressionConfig

    init(
        id: Int64 = 0,
        planId: Int64 = 0,
        exerciseId: Int64 = 0,
        orderIndex: Int = 0,
        supersetGroupId: Int? = nil,
        targetSets: Int = 3,
        targetReps: Int = 10,
        targetWeightKg: Double = 0,
        setTargets: [ILPlannedSet] = [],
        progression: ILProgressionConfig = ILProgressionConfig()
    ) {
        self.id = id
        self.planId = planId
        self.exerciseId = exerciseId
        self.orderIndex = orderIndex
        self.supersetGroupId = supersetGroupId
        self.targetSets = targetSets
        self.targetReps = targetReps
        self.targetWeightKg = targetWeightKg
        self.setTargets = setTargets
        self.progression = progression
    }

    private enum CodingKeys: String, CodingKey {
        case setTargets
        case id, planId, exerciseId, orderIndex, supersetGroupId, targetSets, targetReps, targetWeightKg, progression
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        setTargets = try c.decodeIfPresent([ILPlannedSet].self, forKey: .setTargets) ?? []
        id = try c.decode(Int64.self, forKey: .id)
        planId = try c.decode(Int64.self, forKey: .planId)
        exerciseId = try c.decode(Int64.self, forKey: .exerciseId)
        orderIndex = try c.decode(Int.self, forKey: .orderIndex)
        supersetGroupId = try c.decodeIfPresent(Int.self, forKey: .supersetGroupId)
        targetSets = try c.decode(Int.self, forKey: .targetSets)
        targetReps = try c.decode(Int.self, forKey: .targetReps)
        targetWeightKg = try c.decode(Double.self, forKey: .targetWeightKg)
        progression = try c.decodeOrDefault(ILProgressionConfig.self, forKey: .progression, default: ILProgressionConfig())
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(setTargets, forKey: .setTargets)
        try c.encode(id, forKey: .id)
        try c.encode(planId, forKey: .planId)
        try c.encode(exerciseId, forKey: .exerciseId)
        try c.encode(orderIndex, forKey: .orderIndex)
        try c.encode(supersetGroupId, forKey: .supersetGroupId)
        try c.encode(targetSets, forKey: .targetSets)
        try c.encode(targetReps, forKey: .targetReps)
        try c.encode(targetWeightKg, forKey: .targetWeightKg)
        try c.encode(progression, forKey: .progression)
    }
}

struct ILProgressionConfig: Codable, Equatable {
    var scheme: String
    var incrementValue: Double?
    var incrementUnit: String?
    var incrementKg: Double?
    var minReps: Int?
    var maxReps: Int?
    var targetTotalReps: Int64?
    var targetRpe: Double?
    var rpeTolerance: Double?
    var stallThreshold: Int
    var backoffPercent: Double
    var ruleRevision: Int

    init(
        scheme: String = "MANUAL",
        incrementValue: Double? = nil,
        incrementUnit: String? = nil,
        incrementKg: Double? = nil,
        minReps: Int? = nil,
        maxReps: Int? = nil,
        targetTotalReps: Int64? = nil,
        targetRpe: Double? = nil,
        rpeTolerance: Double? = nil,
        stallThreshold: Int = 2,
        backoffPercent: Double = 10.0,
        ruleRevision: Int = 1
    ) {
        self.scheme = scheme
        self.incrementValue = incrementValue
        self.incrementUnit = incrementUnit
        self.incrementKg = incrementKg
        self.minReps = minReps
        self.maxReps = maxReps
        self.targetTotalReps = targetTotalReps
        self.targetRpe = targetRpe
        self.rpeTolerance = rpeTolerance
        self.stallThreshold = stallThreshold
        self.backoffPercent = backoffPercent
        self.ruleRevision = ruleRevision
    }

    private enum CodingKeys: String, CodingKey {
        case scheme, incrementValue, incrementUnit, incrementKg, minReps, maxReps, targetTotalReps, targetRpe, rpeTolerance, stallThreshold, backoffPercent, ruleRevision
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        scheme = try c.decodeOrDefault(String.self, forKey: .scheme, default: "MANUAL")
        incrementValue = try c.decodeIfPresent(Double.self, forKey: .incrementValue)
        incrementUnit = try c.decodeIfPresent(String.self, forKey: .incrementUnit)
        incrementKg = try c.decodeIfPresent(Double.self, forKey: .incrementKg)
        minReps = try c.decodeIfPresent(Int.self, forKey: .minReps)
        maxReps = try c.decodeIfPresent(Int.self, forKey: .maxReps)
        targetTotalReps = try c.decodeIfPresent(Int64.self, forKey: .targetTotalReps)
        targetRpe = try c.decodeIfPresent(Double.self, forKey: .targetRpe)
        rpeTolerance = try c.decodeIfPresent(Double.self, forKey: .rpeTolerance)
        stallThreshold = try c.decodeOrDefault(Int.self, forKey: .stallThreshold, default: 2)
        backoffPercent = try c.decodeOrDefault(Double.self, forKey: .backoffPercent, default: 10.0)
        ruleRevision = try c.decodeOrDefault(Int.self, forKey: .ruleRevision, default: 1)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(scheme, forKey: .scheme)
        try c.encode(incrementValue, forKey: .incrementValue)
        try c.encode(incrementUnit, forKey: .incrementUnit)
        try c.encode(incrementKg, forKey: .incrementKg)
        try c.encode(minReps, forKey: .minReps)
        try c.encode(maxReps, forKey: .maxReps)
        try c.encode(targetTotalReps, forKey: .targetTotalReps)
        try c.encode(targetRpe, forKey: .targetRpe)
        try c.encode(rpeTolerance, forKey: .rpeTolerance)
        try c.encode(stallThreshold, forKey: .stallThreshold)
        try c.encode(backoffPercent, forKey: .backoffPercent)
        try c.encode(ruleRevision, forKey: .ruleRevision)
    }

    var schemeDisplayName: String { ILDisplayNames.scheme(scheme) }
    var displayScheme: String { schemeDisplayName }
    var schemeName: String { schemeDisplayName }
    var progressionSchemeDisplayName: String { schemeDisplayName }
    var displayName: String { schemeDisplayName }
}

struct ILProgressionTarget: Codable, Equatable {
    var sets: Int
    var reps: Int
    var weightKg: Double

    init(sets: Int = 0, reps: Int = 0, weightKg: Double = 0) {
        self.sets = sets
        self.reps = reps
        self.weightKg = weightKg
    }

    private enum CodingKeys: String, CodingKey { case sets, reps, weightKg }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sets = try c.decode(Int.self, forKey: .sets)
        reps = try c.decode(Int.self, forKey: .reps)
        weightKg = try c.decode(Double.self, forKey: .weightKg)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(sets, forKey: .sets)
        try c.encode(reps, forKey: .reps)
        try c.encode(weightKg, forKey: .weightKg)
    }
}

struct ILWorkoutPlanTarget: Codable, Equatable, Identifiable {
    var id: Int64
    var sessionId: Int64
    var planId: Int64
    var exerciseId: Int64
    var orderIndex: Int
    var supersetGroupId: Int?
    var target: ILProgressionTarget
    var setTargets: [ILPlannedSet]
    var progression: ILProgressionConfig

    init(
        id: Int64 = 0,
        sessionId: Int64 = 0,
        planId: Int64 = 0,
        exerciseId: Int64 = 0,
        orderIndex: Int = 0,
        supersetGroupId: Int? = nil,
        target: ILProgressionTarget = ILProgressionTarget(),
        setTargets: [ILPlannedSet] = [],
        progression: ILProgressionConfig = ILProgressionConfig()
    ) {
        self.id = id
        self.sessionId = sessionId
        self.planId = planId
        self.exerciseId = exerciseId
        self.orderIndex = orderIndex
        self.supersetGroupId = supersetGroupId
        self.target = target
        self.setTargets = setTargets
        self.progression = progression
    }

    private enum CodingKeys: String, CodingKey {
        case setTargets
        case id, sessionId, planId, exerciseId, orderIndex, supersetGroupId, target, progression
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        setTargets = try c.decodeIfPresent([ILPlannedSet].self, forKey: .setTargets) ?? []
        id = try c.decode(Int64.self, forKey: .id)
        sessionId = try c.decode(Int64.self, forKey: .sessionId)
        planId = try c.decode(Int64.self, forKey: .planId)
        exerciseId = try c.decode(Int64.self, forKey: .exerciseId)
        orderIndex = try c.decode(Int.self, forKey: .orderIndex)
        supersetGroupId = try c.decodeIfPresent(Int.self, forKey: .supersetGroupId)
        target = try c.decode(ILProgressionTarget.self, forKey: .target)
        progression = try c.decode(ILProgressionConfig.self, forKey: .progression)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(setTargets, forKey: .setTargets)
        try c.encode(id, forKey: .id)
        try c.encode(sessionId, forKey: .sessionId)
        try c.encode(planId, forKey: .planId)
        try c.encode(exerciseId, forKey: .exerciseId)
        try c.encode(orderIndex, forKey: .orderIndex)
        try c.encode(supersetGroupId, forKey: .supersetGroupId)
        try c.encode(target, forKey: .target)
        try c.encode(progression, forKey: .progression)
    }
}

struct ILProgressionSuggestion: Codable, Equatable, Identifiable {
    var id: Int64
    var sourceSessionId: Int64
    var sourceTargetSnapshotId: Int64
    var planId: Int64
    var exerciseId: Int64
    var orderIndex: Int
    var supersetGroupId: Int?
    var sourceTarget: ILProgressionTarget
    var sourceProgression: ILProgressionConfig
    var outcomeType: String
    var reasonCode: String
    var reasonArguments: [String: Double]
    var countedSetIds: [Int64]
    var streakEffect: String
    var suggestedTarget: ILProgressionTarget?
    var status: String
    var wasEdited: Bool
    var finalTarget: ILProgressionTarget?
    var createdAtEpochMillis: Int64
    var decidedAtEpochMillis: Int64?

    init(
        id: Int64 = 0,
        sourceSessionId: Int64 = 0,
        sourceTargetSnapshotId: Int64 = 0,
        planId: Int64 = 0,
        exerciseId: Int64 = 0,
        orderIndex: Int = 0,
        supersetGroupId: Int? = nil,
        sourceTarget: ILProgressionTarget = ILProgressionTarget(),
        sourceProgression: ILProgressionConfig = ILProgressionConfig(),
        outcomeType: String = "",
        reasonCode: String = "",
        reasonArguments: [String: Double] = [:],
        countedSetIds: [Int64] = [],
        streakEffect: String = "",
        suggestedTarget: ILProgressionTarget? = nil,
        status: String = "",
        wasEdited: Bool = false,
        finalTarget: ILProgressionTarget? = nil,
        createdAtEpochMillis: Int64 = 0,
        decidedAtEpochMillis: Int64? = nil
    ) {
        self.id = id
        self.sourceSessionId = sourceSessionId
        self.sourceTargetSnapshotId = sourceTargetSnapshotId
        self.planId = planId
        self.exerciseId = exerciseId
        self.orderIndex = orderIndex
        self.supersetGroupId = supersetGroupId
        self.sourceTarget = sourceTarget
        self.sourceProgression = sourceProgression
        self.outcomeType = outcomeType
        self.reasonCode = reasonCode
        self.reasonArguments = reasonArguments
        self.countedSetIds = countedSetIds
        self.streakEffect = streakEffect
        self.suggestedTarget = suggestedTarget
        self.status = status
        self.wasEdited = wasEdited
        self.finalTarget = finalTarget
        self.createdAtEpochMillis = createdAtEpochMillis
        self.decidedAtEpochMillis = decidedAtEpochMillis
    }

    private enum CodingKeys: String, CodingKey {
        case id, sourceSessionId, sourceTargetSnapshotId, planId, exerciseId, orderIndex, supersetGroupId
        case sourceTarget, sourceProgression, outcomeType, reasonCode, reasonArguments, countedSetIds, streakEffect
        case suggestedTarget, status, wasEdited, finalTarget, createdAtEpochMillis, decidedAtEpochMillis
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        sourceSessionId = try c.decode(Int64.self, forKey: .sourceSessionId)
        sourceTargetSnapshotId = try c.decode(Int64.self, forKey: .sourceTargetSnapshotId)
        planId = try c.decode(Int64.self, forKey: .planId)
        exerciseId = try c.decode(Int64.self, forKey: .exerciseId)
        orderIndex = try c.decode(Int.self, forKey: .orderIndex)
        supersetGroupId = try c.decodeIfPresent(Int.self, forKey: .supersetGroupId)
        sourceTarget = try c.decode(ILProgressionTarget.self, forKey: .sourceTarget)
        sourceProgression = try c.decode(ILProgressionConfig.self, forKey: .sourceProgression)
        outcomeType = try c.decode(String.self, forKey: .outcomeType)
        reasonCode = try c.decode(String.self, forKey: .reasonCode)
        reasonArguments = try c.decodeOrDefault([String: Double].self, forKey: .reasonArguments, default: [:])
        countedSetIds = try c.decodeOrDefault([Int64].self, forKey: .countedSetIds, default: [])
        streakEffect = try c.decode(String.self, forKey: .streakEffect)
        suggestedTarget = try c.decodeIfPresent(ILProgressionTarget.self, forKey: .suggestedTarget)
        status = try c.decode(String.self, forKey: .status)
        wasEdited = try c.decodeOrDefault(Bool.self, forKey: .wasEdited, default: false)
        finalTarget = try c.decodeIfPresent(ILProgressionTarget.self, forKey: .finalTarget)
        createdAtEpochMillis = try c.decode(Int64.self, forKey: .createdAtEpochMillis)
        decidedAtEpochMillis = try c.decodeIfPresent(Int64.self, forKey: .decidedAtEpochMillis)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(sourceSessionId, forKey: .sourceSessionId)
        try c.encode(sourceTargetSnapshotId, forKey: .sourceTargetSnapshotId)
        try c.encode(planId, forKey: .planId)
        try c.encode(exerciseId, forKey: .exerciseId)
        try c.encode(orderIndex, forKey: .orderIndex)
        try c.encode(supersetGroupId, forKey: .supersetGroupId)
        try c.encode(sourceTarget, forKey: .sourceTarget)
        try c.encode(sourceProgression, forKey: .sourceProgression)
        try c.encode(outcomeType, forKey: .outcomeType)
        try c.encode(reasonCode, forKey: .reasonCode)
        try c.encode(reasonArguments, forKey: .reasonArguments)
        try c.encode(countedSetIds, forKey: .countedSetIds)
        try c.encode(streakEffect, forKey: .streakEffect)
        try c.encode(suggestedTarget, forKey: .suggestedTarget)
        try c.encode(status, forKey: .status)
        try c.encode(wasEdited, forKey: .wasEdited)
        try c.encode(finalTarget, forKey: .finalTarget)
        try c.encode(createdAtEpochMillis, forKey: .createdAtEpochMillis)
        try c.encode(decidedAtEpochMillis, forKey: .decidedAtEpochMillis)
    }

    var createdAt: Date { ilDate(fromEpochMillis: createdAtEpochMillis) }
    var decidedAt: Date? { ilOptionalDate(fromEpochMillis: decidedAtEpochMillis) }
}

// MARK: - Records and meta plans

struct ILPersonalRecord: Codable, Equatable, Identifiable {
    var id: Int64
    var exerciseId: Int64
    var type: String
    var value: Double
    var achievedAt: Int64

    init(id: Int64 = 0, exerciseId: Int64 = 0, type: String = "", value: Double = 0, achievedAt: Int64 = 0) {
        self.id = id
        self.exerciseId = exerciseId
        self.type = type
        self.value = value
        self.achievedAt = achievedAt
    }

    private enum CodingKeys: String, CodingKey { case id, exerciseId, type, value, achievedAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        exerciseId = try c.decode(Int64.self, forKey: .exerciseId)
        type = try c.decode(String.self, forKey: .type)
        value = try c.decode(Double.self, forKey: .value)
        achievedAt = try c.decode(Int64.self, forKey: .achievedAt)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(exerciseId, forKey: .exerciseId)
        try c.encode(type, forKey: .type)
        try c.encode(value, forKey: .value)
        try c.encode(achievedAt, forKey: .achievedAt)
    }

    var achievedDate: Date { ilDate(fromEpochMillis: achievedAt) }
}

struct ILMetaTrainingPlan: Codable, Equatable, Identifiable {
    var id: Int64
    var name: String
    var createdAt: Int64

    init(id: Int64 = 0, name: String = "", createdAt: Int64 = 0) {
        self.id = id
        self.name = name
        self.createdAt = createdAt
    }

    private enum CodingKeys: String, CodingKey { case id, name, createdAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        createdAt = try c.decode(Int64.self, forKey: .createdAt)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(name, forKey: .name)
        try c.encode(createdAt, forKey: .createdAt)
    }

    var createdDate: Date { ilDate(fromEpochMillis: createdAt) }
}

struct ILMetaPlanItem: Codable, Equatable, Identifiable {
    var id: Int64
    var metaPlanId: Int64
    var trainingPlanId: Int64
    var orderIndex: Int

    init(id: Int64 = 0, metaPlanId: Int64 = 0, trainingPlanId: Int64 = 0, orderIndex: Int = 0) {
        self.id = id
        self.metaPlanId = metaPlanId
        self.trainingPlanId = trainingPlanId
        self.orderIndex = orderIndex
    }

    private enum CodingKeys: String, CodingKey { case id, metaPlanId, trainingPlanId, orderIndex }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        metaPlanId = try c.decode(Int64.self, forKey: .metaPlanId)
        trainingPlanId = try c.decode(Int64.self, forKey: .trainingPlanId)
        orderIndex = try c.decode(Int.self, forKey: .orderIndex)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(metaPlanId, forKey: .metaPlanId)
        try c.encode(trainingPlanId, forKey: .trainingPlanId)
        try c.encode(orderIndex, forKey: .orderIndex)
    }
}

struct ILMetaPlanSkip: Codable, Equatable, Identifiable {
    var id: Int64
    var metaPlanId: Int64
    var trainingPlanId: Int64
    var skippedAt: Int64

    init(id: Int64 = 0, metaPlanId: Int64 = 0, trainingPlanId: Int64 = 0, skippedAt: Int64 = 0) {
        self.id = id
        self.metaPlanId = metaPlanId
        self.trainingPlanId = trainingPlanId
        self.skippedAt = skippedAt
    }

    private enum CodingKeys: String, CodingKey { case id, metaPlanId, trainingPlanId, skippedAt }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int64.self, forKey: .id)
        metaPlanId = try c.decode(Int64.self, forKey: .metaPlanId)
        trainingPlanId = try c.decode(Int64.self, forKey: .trainingPlanId)
        skippedAt = try c.decode(Int64.self, forKey: .skippedAt)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(metaPlanId, forKey: .metaPlanId)
        try c.encode(trainingPlanId, forKey: .trainingPlanId)
        try c.encode(skippedAt, forKey: .skippedAt)
    }

    var skippedDate: Date { ilDate(fromEpochMillis: skippedAt) }
}
