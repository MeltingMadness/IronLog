import Foundation
import SwiftUI
import UIKit

private struct IOSProgressionPosition: Hashable {
    let planID: Int64
    let exerciseID: Int64
    let orderIndex: Int
}

/// Review of the durable progression suggestions emitted by the shared store.
///
/// A suggestion is source evidence plus a proposed target. The source fields
/// are rendered read-only. SwiftUI only sends accept/reject commands while the
/// persisted status is `PENDING`; it never hides a decision locally after a
/// command, so the next snapshot remains the source of truth.
struct IOSProgressionReviewScreen: View {
    let sessionId: Int64?

    @Environment(IOSTrainingStore.self) private var store
    @EnvironmentObject private var settings: IOSSettingsViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var editingSuggestion: ILProgressionSuggestion?
    @State private var alert: IOSProgressionAlert?

    init(sessionId: Int64? = nil) {
        self.sessionId = sessionId
    }

    private var suggestions: [ILProgressionSuggestion] {
        guard let data = store.data else { return [] }
        return data.progressionSuggestions
            .filter { sessionId == nil || $0.sourceSessionId == sessionId }
            .sorted {
                if $0.status.uppercased() != $1.status.uppercased() {
                    // Pending decisions stay above historical decisions.
                    return $0.status.uppercased() == "PENDING"
                }
                return $0.createdAtEpochMillis > $1.createdAtEpochMillis
            }
    }

    private var pendingCount: Int {
        safePendingSuggestions.count
    }

    /// Mirrors Android's `safePendingItems`: only pending proposals with an
    /// actual target are actionable, and only the newest source session for a
    /// plan/exercise/order position is sent in a bulk decision.
    private var safePendingSuggestions: [ILProgressionSuggestion] {
        var seen = Set<IOSProgressionPosition>()
        return suggestions
            .filter { $0.status.uppercased() == "PENDING" && $0.suggestedTarget != nil }
            .sorted { lhs, rhs in
                if lhs.sourceSessionId != rhs.sourceSessionId {
                    return lhs.sourceSessionId > rhs.sourceSessionId
                }
                return lhs.createdAtEpochMillis > rhs.createdAtEpochMillis
            }
            .filter { suggestion in
                seen.insert(
                    IOSProgressionPosition(
                        planID: suggestion.planId,
                        exerciseID: suggestion.exerciseId,
                        orderIndex: suggestion.orderIndex
                    )
                ).inserted
            }
    }

    var body: some View {
        NavigationStack {
            Group {
                if let error = store.errorMessage, store.data == nil {
                    ContentUnavailableView("Progression nicht verfügbar", systemImage: "exclamationmark.triangle", description: Text(error))
                } else if store.data == nil {
                    ProgressView("Progression wird geladen …")
                } else {
                    reviewContent
                }
            }
            .navigationTitle("Progression prüfen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Schließen") { dismiss() }
                        .disabled(store.isBusy)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        generate()
                    } label: {
                        Label("Auswertung erzeugen", systemImage: "arrow.triangle.2.circlepath")
                    }
                    .disabled(store.isBusy)
                    .accessibilityHint("Speichert neue Progressionsvorschläge, falls eine Auswertung möglich ist")
                }
            }
        }
        .sheet(item: $editingSuggestion) { suggestion in
            IOSProgressionEditSheet(
                suggestion: suggestion,
                unitSystem: settings.state.unitSystem,
                isWorking: store.isBusy,
                onAccept: { target in accept(suggestion, target: target) },
                onCancel: { editingSuggestion = nil }
            )
        }
        .alert(item: $alert) { item in
            Alert(title: Text(item.title), message: Text(item.message), dismissButton: .default(Text("OK")))
        }
    }

    @ViewBuilder
    private var reviewContent: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 14) {
                if pendingCount > 0 {
                    IronLogCard(tone: .muted) {
                        Label("\(pendingCount) Entscheidung(en) ausstehend", systemImage: "checkmark.circle")
                            .font(.headline)
                        Text("Ein Vorschlag ändert dein Planziel erst nach einer bewussten Übernahme. Bearbeitete Quellen bleiben unverändert.")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }

                if suggestions.isEmpty {
                    ContentUnavailableView(
                        sessionId == nil ? "Keine Progressionshinweise" : "Keine Hinweise für dieses Training",
                        systemImage: "chart.line.uptrend.xyaxis",
                        description: Text(
                            sessionId == nil
                                ? "Nach einem abgeschlossenen Training mit aktivierter Progressionsregel erscheinen neue Hinweise hier."
                                : "Für dieses Training wurde noch keine auswertbare Progression gespeichert."
                        )
                    )
                    .padding(.top, 24)
                } else {
                    if !safePendingSuggestions.isEmpty {
                        IronLogCard(tone: .muted) {
                            Text("Sichere Vorschläge gesammelt übernehmen")
                                .font(.headline)
                            if editingSuggestion != nil {
                                Text("Schließe die Bearbeitung zuerst ab.")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                            }
                            Button {
                                acceptAllSafe()
                            } label: {
                                Label(
                                    "Alle sicheren übernehmen (\(safePendingSuggestions.count))",
                                    systemImage: "checkmark.circle"
                                )
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(store.isBusy || editingSuggestion != nil)
                            .accessibilityHint("Übernimmt je Position nur den neuesten ausstehenden Vorschlag")
                        }
                    }
                    ForEach(suggestions) { suggestion in
                        IOSProgressionSuggestionCard(
                            suggestion: suggestion,
                            exerciseName: exerciseName(for: suggestion),
                            planName: planName(for: suggestion),
                            evidence: evidence(for: suggestion),
                            evidenceIDsWerePresent: !suggestion.countedSetIds.isEmpty,
                            unitSystem: settings.state.unitSystem,
                            isWorking: store.isBusy,
                            onAccept: {
                                guard let target = suggestion.suggestedTarget,
                                      suggestion.status.uppercased() == "PENDING" else { return }
                                accept(suggestion, target: target)
                            },
                            onEdit: {
                                guard suggestion.status.uppercased() == "PENDING",
                                      suggestion.suggestedTarget != nil else { return }
                                editingSuggestion = suggestion
                            },
                            onReject: { reject(suggestion) }
                        )
                    }
                }
            }
            .padding(16)
        }
        .background(Color(uiColor: .systemGroupedBackground))
    }

    private func exerciseName(for suggestion: ILProgressionSuggestion) -> String? {
        store.data?.exercises.first(where: { $0.id == suggestion.exerciseId })?.name
    }

    private func planName(for suggestion: ILProgressionSuggestion) -> String? {
        store.data?.trainingPlans.first(where: { $0.id == suggestion.planId })?.name
    }

    private func evidence(for suggestion: ILProgressionSuggestion) -> [ILWorkoutSet] {
        guard let data = store.data else { return [] }
        let ids = Set(suggestion.countedSetIds)
        guard !ids.isEmpty else { return [] }
        return data.workoutSets
            .filter { ids.contains($0.id) }
            .sorted {
                if $0.setNumber != $1.setNumber { return $0.setNumber < $1.setNumber }
                return $0.completedAt < $1.completedAt
            }
    }

    private func generate() {
        Task { @MainActor in
            var fields: [String: Any] = [:]
            if let sessionId { fields["sessionId"] = sessionId }
            let success = await store.command("progression.generate", fields: fields)
            if !success {
                alert = IOSProgressionAlert(title: "Auswertung fehlgeschlagen", message: store.errorMessage ?? "Die Progression konnte nicht ausgewertet werden.")
            }
        }
    }

    private func accept(_ suggestion: ILProgressionSuggestion, target: ILProgressionTarget) {
        guard suggestion.status.uppercased() == "PENDING", suggestion.suggestedTarget != nil else { return }
        guard target.sets > 0, target.reps > 0, target.weightKg.isFinite, target.weightKg >= 0 else {
            alert = IOSProgressionAlert(title: "Ungültiges Ziel", message: "Sätze und Wiederholungen müssen positiv sein; das Gewicht darf nicht negativ sein.")
            return
        }
        Task { @MainActor in
            let targetPayload: [String: Any] = [
                "id": suggestion.id,
                "sets": target.sets,
                "reps": target.reps,
                "weightKg": target.weightKg
            ]
            let success = await store.command("progression.accept", fields: ["targets": [targetPayload]])
            if success {
                editingSuggestion = nil
            } else {
                alert = IOSProgressionAlert(title: "Vorschlag nicht übernommen", message: store.errorMessage ?? "Der Vorschlag ist möglicherweise nicht mehr aktuell.")
            }
        }
    }

    private func acceptAllSafe() {
        guard !store.isBusy, editingSuggestion == nil else { return }
        let selected = safePendingSuggestions.compactMap { suggestion -> [String: Any]? in
            guard let target = suggestion.suggestedTarget else { return nil }
            return [
                "id": suggestion.id,
                "sets": target.sets,
                "reps": target.reps,
                "weightKg": target.weightKg
            ]
        }
        guard !selected.isEmpty else { return }
        Task { @MainActor in
            let success = await store.command("progression.accept", fields: ["targets": selected])
            if success {
                editingSuggestion = nil
            } else {
                alert = IOSProgressionAlert(
                    title: "Vorschläge nicht übernommen",
                    message: store.errorMessage ?? "Die Vorschläge sind möglicherweise nicht mehr aktuell."
                )
            }
        }
    }

    private func reject(_ suggestion: ILProgressionSuggestion) {
        guard suggestion.status.uppercased() == "PENDING", suggestion.suggestedTarget != nil else { return }
        Task { @MainActor in
            let success = await store.command("progression.reject", fields: ["id": suggestion.id])
            if !success {
                alert = IOSProgressionAlert(title: "Vorschlag nicht verworfen", message: store.errorMessage ?? "Der Vorschlag ist möglicherweise nicht mehr aktuell.")
            }
        }
    }
}

private struct IOSProgressionSuggestionCard: View {
    let suggestion: ILProgressionSuggestion
    let exerciseName: String?
    let planName: String?
    let evidence: [ILWorkoutSet]
    let evidenceIDsWerePresent: Bool
    let unitSystem: String
    let isWorking: Bool
    let onAccept: () -> Void
    let onEdit: () -> Void
    let onReject: () -> Void

    private var isPending: Bool { suggestion.status.uppercased() == "PENDING" }

    var body: some View {
        IronLogCard(tone: isPending ? .elevated : .muted) {
            HStack(alignment: .firstTextBaseline) {
                Text(exerciseName ?? "Übung \(suggestion.exerciseId)")
                    .font(.headline)
                Spacer(minLength: 8)
                IOSProgressionStatusBadge(status: suggestion.status)
            }
            Text("Schema: \(suggestion.sourceProgression.schemeDisplayName)")
                .font(.subheadline)
                .foregroundStyle(.tint)
            if let planName {
                Text("Plan: \(planName)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Text("Bewertung: \(IOSProgressionFormatting.date(suggestion.createdAt))")
                .font(.caption)
                .foregroundStyle(.secondary)
            if let decidedAt = suggestion.decidedAt {
                Text("Entschieden: \(IOSProgressionFormatting.date(decidedAt))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            IOSProgressionTargetBlock(
                title: "Quelle (unverändert)",
                target: suggestion.sourceTarget,
                unitSystem: unitSystem,
                tone: .secondary
            )

            IOSProgressionEvidenceBlock(
                sets: evidence,
                idsWerePresent: evidenceIDsWerePresent,
                unitSystem: unitSystem
            )

            Text(IOSProgressionReasonText.text(for: suggestion, unitSystem: unitSystem))
                .font(.body)

            if let suggestedTarget = suggestion.suggestedTarget {
                IOSProgressionTargetBlock(title: "Vorgeschlagen", target: suggestedTarget, unitSystem: unitSystem, tone: .primary)
            } else {
                Text("Kein neuer Zielwert vorgeschlagen; dieser Hinweis bleibt informativ.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            if let finalTarget = suggestion.finalTarget {
                IOSProgressionTargetBlock(title: suggestion.wasEdited ? "Final (bearbeitet)" : "Final übernommen", target: finalTarget, unitSystem: unitSystem, tone: .success)
            }

            if isPending, suggestion.suggestedTarget != nil {
                HStack(spacing: 8) {
                    Button("Übernehmen", action: onAccept)
                        .buttonStyle(.borderedProminent)
                        .disabled(isWorking)
                    Button("Bearbeiten", action: onEdit)
                        .buttonStyle(.bordered)
                        .disabled(isWorking)
                    Button("Verwerfen", role: .destructive, action: onReject)
                        .buttonStyle(.borderless)
                        .disabled(isWorking)
                }
                .controlSize(.small)
                .accessibilityElement(children: .contain)
            }
        }
    }
}

private struct IOSProgressionStatusBadge: View {
    let status: String

    var body: some View {
        Text(IOSProgressionFormatting.status(status))
            .font(.caption.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(background, in: Capsule())
            .accessibilityLabel("Status: \(IOSProgressionFormatting.status(status))")
    }

    private var background: Color {
        switch status.uppercased() {
        case "PENDING": return .orange.opacity(0.18)
        case "ACCEPTED": return .green.opacity(0.18)
        case "REJECTED": return .red.opacity(0.15)
        default: return .secondary.opacity(0.15)
        }
    }
}

private struct IOSProgressionTargetBlock: View {
    let title: String
    let target: ILProgressionTarget
    let unitSystem: String
    let tone: IOSProgressionTargetTone

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.subheadline.weight(.semibold))
            Text("\(target.sets) Sätze × \(target.reps) Wdh · \(IOSWeightFormatter.format(target.weightKg, unitSystem: unitSystem))")
                .font(.body.monospacedDigit())
                .foregroundStyle(tone.color)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title): \(target.sets) Sätze, \(target.reps) Wiederholungen, \(IOSWeightFormatter.format(target.weightKg, unitSystem: unitSystem))")
    }
}

private enum IOSProgressionTargetTone {
    case primary
    case secondary
    case success

    var color: Color {
        switch self {
        case .primary: return .accentColor
        case .secondary: return .primary
        case .success: return .green
        }
    }
}

private struct IOSProgressionEvidenceBlock: View {
    let sets: [ILWorkoutSet]
    let idsWerePresent: Bool
    let unitSystem: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Tatsächliche Evidenz").font(.subheadline.weight(.semibold))
            if sets.isEmpty {
                Text(idsWerePresent ? "Die gespeicherte Evidenz ist nicht verfügbar." : "Keine verwertbaren Arbeitssätze gespeichert.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                ForEach(sets) { set in
                    HStack(spacing: 6) {
                        Text("Satz \(set.setNumber):")
                        Text("\(IOSWeightFormatter.format(set.weightKg, unitSystem: unitSystem)) × \(set.reps) Wdh")
                        if let rpe = set.rpe, rpe.isFinite {
                            Text("· RPE \(IOSProgressionFormatting.number(rpe))")
                        }
                    }
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            }
        }
        .accessibilityElement(children: .contain)
    }
}

private struct IOSProgressionEditSheet: View {
    let suggestion: ILProgressionSuggestion
    let unitSystem: String
    let isWorking: Bool
    let onAccept: (ILProgressionTarget) -> Void
    let onCancel: () -> Void

    @State private var setsText: String
    @State private var repsText: String
    @State private var weightText: String
    @State private var errorMessage: String?

    init(suggestion: ILProgressionSuggestion, unitSystem: String, isWorking: Bool, onAccept: @escaping (ILProgressionTarget) -> Void, onCancel: @escaping () -> Void) {
        self.suggestion = suggestion
        self.unitSystem = unitSystem
        self.isWorking = isWorking
        self.onAccept = onAccept
        self.onCancel = onCancel
        let target = suggestion.suggestedTarget ?? suggestion.sourceTarget
        _setsText = State(initialValue: "\(target.sets)")
        _repsText = State(initialValue: "\(target.reps)")
        _weightText = State(initialValue: IOSProgressionFormatting.displayedWeight(target.weightKg, unitSystem: unitSystem))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Ziel bearbeiten") {
                    TextField("Sätze", text: $setsText).keyboardType(.numberPad)
                    TextField("Wiederholungen", text: $repsText).keyboardType(.numberPad)
                    TextField("Gewicht (\(IOSWeightFormatter.unitLabel(for: unitSystem)))", text: $weightText).keyboardType(.decimalPad)
                    if let errorMessage {
                        Text(errorMessage).font(.footnote).foregroundStyle(.red)
                    }
                }
                Section {
                    Text("Die Quelle und ihre Evidenz bleiben unverändert. Deine Eingabe wird als finales Ziel gespeichert.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Ziel bearbeiten")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen", action: onCancel)
                        .disabled(isWorking)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Übernehmen", action: save)
                        .disabled(isWorking)
                }
            }
        }
    }

    private func save() {
        guard let sets = Int(setsText), sets > 0 else {
            errorMessage = "Gib eine positive Satzzahl ein."
            return
        }
        guard let reps = Int(repsText), reps > 0 else {
            errorMessage = "Gib eine positive Wiederholungszahl ein."
            return
        }
        guard let displayedWeight = Double(weightText.replacingOccurrences(of: ",", with: ".")), displayedWeight.isFinite, displayedWeight >= 0 else {
            errorMessage = "Gib ein gültiges Gewicht ein."
            return
        }
        onAccept(
            ILProgressionTarget(
                sets: sets,
                reps: reps,
                weightKg: IOSWeightFormatter.kilograms(fromDisplayedValue: displayedWeight, unitSystem: unitSystem)
            )
        )
    }
}

private enum IOSProgressionFormatting {
    static func status(_ raw: String) -> String {
        switch raw.uppercased() {
        case "PENDING": return "Ausstehend"
        case "INFORMATIONAL": return "Hinweis"
        case "ACCEPTED": return "Übernommen"
        case "REJECTED": return "Verworfen"
        case "STALE": return "Nicht mehr aktuell"
        default: return raw
        }
    }

    static func date(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = .current
        formatter.dateStyle = .medium
        return formatter.string(from: date)
    }

    static func number(_ value: Double) -> String {
        IOSHistoryFormatting.number(value, maximumFractionDigits: 2)
    }

    static func displayedWeight(_ kilograms: Double, unitSystem: String) -> String {
        let value = unitSystem.uppercased() == "IMPERIAL" ? kilograms * IOSWeightFormatter.poundsPerKilogram : kilograms
        return number(value)
    }
}

private enum IOSProgressionReasonText {
    static func text(for suggestion: ILProgressionSuggestion, unitSystem: String) -> String {
        let args = suggestion.reasonArguments
        switch suggestion.reasonCode.uppercased() {
        case "REP_TARGET_ADVANCED":
            return "Das Wiederholungsziel wurde erreicht und wird erhöht."
        case "LOAD_ADVANCED":
            guard let step = configuredStep(for: suggestion) else { return unavailable }
            return "Das Zielgewicht steigt um \(IOSProgressionFormatting.number(step.value)) \(step.unit)."
        case "TOTAL_REPS_COMPLETED":
            guard let achieved = whole(args["achievedTotalReps"]), achieved >= 0,
                  let target = whole(args["targetTotalReps"]), target > 0,
                  let step = configuredStep(for: suggestion) else { return unavailable }
            return "\(achieved) von \(target) Zielwiederholungen wurden erreicht; das Gewicht steigt um \(IOSProgressionFormatting.number(step.value)) \(step.unit)."
        case "RPE_WITHIN_TARGET":
            guard let highest = bounded(args["highestRpe"], 1...10),
                  let target = bounded(args["targetRpe"], 1...10),
                  let tolerance = bounded(args["tolerance"], 0...2),
                  let step = configuredStep(for: suggestion) else { return unavailable }
            return "Die höchste RPE war \(IOSProgressionFormatting.number(highest)) bei Ziel \(IOSProgressionFormatting.number(target)) ± \(IOSProgressionFormatting.number(tolerance)); das Gewicht steigt um \(IOSProgressionFormatting.number(step.value)) \(step.unit)."
        case "REPEAT_TARGET":
            if let highest = bounded(args["highestRpe"], 1...10) {
                return "Die höchste RPE war \(IOSProgressionFormatting.number(highest)). Das Wiederholungsziel bleibt gleich; das trainierte Gewicht wird beibehalten."
            }
            if let target = whole(args["targetReps"]), target > 0,
               let actual = whole(args["actualReps"]), actual >= 0 {
                return "Mindestens \(target) Wiederholungen waren erwartet, erreicht wurden \(actual). Das Wiederholungsziel bleibt gleich; das trainierte Gewicht wird beibehalten."
            }
            if let achieved = whole(args["achievedTotalReps"]), achieved >= 0,
               let target = whole(args["targetTotalReps"]), target > 0 {
                return "\(achieved) von \(target) Zielwiederholungen wurden erreicht. Das Wiederholungsziel bleibt gleich; das trainierte Gewicht wird beibehalten."
            }
            return unavailable
        case "STALL_BACKOFF":
            guard let percent = bounded(args["backoffPercent"], 1...30) else { return unavailable }
            return "Nach wiederholten Fehlversuchen sinkt das Gewicht um \(IOSProgressionFormatting.number(percent)) Prozent."
        case "MANUAL_WEIGHT_DEVIATION":
            guard let expected = finite(args["expectedWeightKg"]), expected >= 0,
                  let actual = finite(args["actualWeightKg"]), actual >= 0 else { return unavailable }
            return "Die gewerteten Sätze haben unterschiedliche Gewichte (\(IOSWeightFormatter.format(expected, unitSystem: unitSystem)) und \(IOSWeightFormatter.format(actual, unitSystem: unitSystem))); deshalb wird nichts automatisch geändert."
        case "TOO_FEW_WORK_SETS":
            guard let target = whole(args["targetSets"]), target > 0,
                  let actual = whole(args["actualWorkSets"]), actual >= 0 else { return unavailable }
            return "Für die Auswertung waren \(target) Arbeitssätze nötig, vorhanden waren \(actual)."
        case "RPE_MISSING": return "Mindestens einem gewerteten Satz fehlt die RPE."
        case "RPE_INVALID": return "Mindestens eine RPE liegt außerhalb des gültigen Bereichs."
        case "CONFIG_INVALID": return "Die gespeicherte Progressionskonfiguration ist ungültig."
        case "RULE_REVISION_UNSUPPORTED": return "Die gespeicherte Progressionsregel wird von dieser App-Version nicht unterstützt."
        case "MANUAL_SCHEME": return "Für dieses Ziel ist keine automatische Progression aktiv."
        case "SET_NUMBER_INVALID": return "Die Satznummern lassen keine sichere Auswertung zu."
        case "SET_VALUE_INVALID": return "Mindestens ein Satz enthält ungültige Werte."
        case "BACKOFF_FLOOR_REACHED":
            guard let percent = bounded(args["backoffPercent"], 1...30) else { return unavailable }
            return "Ein Backoff um \(IOSProgressionFormatting.number(percent)) Prozent würde das Gewicht nicht weiter sicher senken."
        default:
            return unavailable
        }
    }

    private static let unavailable = "Die Begründung kann wegen unvollständiger Auswertungsdaten nicht sicher angezeigt werden."

    private static func finite(_ value: Double?) -> Double? {
        guard let value, value.isFinite else { return nil }
        return value
    }

    private static func bounded(_ value: Double?, _ range: ClosedRange<Double>) -> Double? {
        guard let value = finite(value), range.contains(value) else { return nil }
        return value
    }

    private static func whole(_ value: Double?) -> Int64? {
        guard let value = finite(value), value.rounded() == value,
              value >= Double(Int64.min), value <= Double(Int64.max) else { return nil }
        return Int64(value)
    }

    private static func configuredStep(for suggestion: ILProgressionSuggestion) -> (value: Double, unit: String)? {
        guard let argument = finite(suggestion.reasonArguments["stepOriginalValue"]), argument > 0 else { return nil }
        if let configured = suggestion.sourceProgression.incrementValue,
           configured.isFinite, configured > 0, abs(configured - argument) <= 0.000001 {
            let unit = suggestion.sourceProgression.incrementUnit?.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let unit, !unit.isEmpty else { return nil }
            return (argument, displayUnit(unit))
        }
        if let configuredKg = suggestion.sourceProgression.incrementKg,
           configuredKg.isFinite, configuredKg > 0, abs(configuredKg - argument) <= 0.000001 {
            return (argument, "kg")
        }
        return nil
    }

    private static func displayUnit(_ raw: String) -> String {
        switch raw.uppercased() {
        case "METRIC", "KG", "KILOGRAM", "KILOGRAMS": return "kg"
        case "IMPERIAL", "LB", "LBS", "POUND", "POUNDS": return "lb"
        case "PERCENT", "%": return "Prozent"
        default: return raw
        }
    }
}

private struct IOSProgressionAlert: Identifiable {
    let id = UUID()
    let title: String
    let message: String
}
