import Foundation
import Shared
import SwiftUI
import UIKit

struct IOSWorkoutExerciseRow: Identifiable, Equatable {
    let id: String
    let exercise: ILExercise
    let target: ILWorkoutPlanTarget?
    /// The target shown to the athlete after the persisted deload mode is applied. The raw
    /// snapshot in `target` remains the identity used when sets are saved.
    let displayTarget: ILProgressionTarget?
    let sets: [ILWorkoutSet]
    let previousWorkWeightKg: Double?
    let nextSetRecommendation: IOSWorkoutNextSetRecommendation?

    var deloadMode: String = "NONE"

    var targetID: Int64? { target?.id }

    var loggingSlots: [ILPlannedSet] {
        guard let target else { return [] }
        if !target.setTargets.isEmpty {
            var remainingWork = displayTarget?.sets ?? target.target.sets
            return target.setTargets.compactMap { slot in
                if deloadMode == "HALVE_SET_VOLUME", slot.kind != "WARMUP" {
                    guard remainingWork > 0 else { return nil }
                    remainingWork -= 1
                }
                let adjusted = DeloadTargetAdjustment.shared.adjustByName(sets: 1, reps: Int32(slot.reps), weightKg: slot.weightKg, modeName: deloadMode)
                return ILPlannedSet(kind: slot.kind, reps: slot.reps, weightKg: adjusted.weightKg)
            }
        }
        return Array(repeating: ILPlannedSet(reps: displayTarget?.reps ?? target.target.reps, weightKg: displayTarget?.weightKg ?? target.target.weightKg), count: min(100, max(0, displayTarget?.sets ?? target.target.sets)))
    }
    var matchedSlots: [Int?] {
        var used = Set<Int>()
        return loggingSlots.map { slot in
            let kind = slot.kind == "BACKOFF" ? "NORMAL" : slot.kind
            guard let index = sets.indices.first(where: { !used.contains($0) && resolvedIOSWorkoutSetType(sets[$0]).rawValue == kind }) else { return nil }
            used.insert(index)
            return index
        }
    }
    var nextPlannedSet: ILPlannedSet? {
        guard let index = matchedSlots.firstIndex(where: { $0 == nil }) else { return nil }
        return loggingSlots[index]
    }
    var targetReps: Int {
        if target?.setTargets.isEmpty == false { return nextPlannedSet?.reps ?? displayTarget?.reps ?? 0 }
        return sets.last(where: { resolvedIOSWorkoutSetType($0) == .normal })?.reps ?? displayTarget?.reps ?? 0
    }

    var defaultWeightKg: Double {
        if target?.setTargets.isEmpty == false, let nextPlannedSet { return nextPlannedSet.weightKg }
        if let last = sets.last(where: { resolvedIOSWorkoutSetType($0) == .normal }) { return last.weightKg }
        guard let target else { return previousWorkWeightKg ?? 0 }
        let displayWeight = displayTarget?.weightKg ?? target.target.weightKg
        return displayWeight > 0 ? displayWeight : (previousWorkWeightKg ?? 0)
    }

    var nextSetNumber: Int {
        (sets.map(\.setNumber).max() ?? 0) + 1
    }

    var completedNormalSets: Int {
        sets.filter { resolvedIOSWorkoutSetType($0) == .normal }.count
    }

    var remainingPlannedSets: Int? {
        guard displayTarget != nil else { return nil }
        return matchedSlots.filter { $0 == nil }.count
    }

    var isComplete: Bool {
        guard let remainingPlannedSets else { return false }
        return remainingPlannedSets == 0
    }
}

/// A render-only grouping of adjacent workout rows. The group mirrors Android's
/// `buildExerciseRenderGroups`: a superset is rendered only when at least two
/// adjacent immutable target snapshots carry the same group id. Rows that carry
/// an isolated or interrupted group id stay ordinary cards.
struct IOSWorkoutExerciseRenderGroup: Identifiable {
    let id: String
    let supersetGroupID: Int?
    let rows: [IOSWorkoutExerciseRow]
}

struct IOSWorkoutScreen: View {
    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(IOSTrainingStore.self) private var store
    @EnvironmentObject private var settings: IOSSettingsViewModel
    @Environment(\.scenePhase) private var scenePhase

    @SceneStorage("ironlog.workout.completedSessionID") private var completedSessionID = 0
    @State private var isFinishing = false
    @State private var showingExercisePicker = false
    @State private var showingNotes = false
    @State private var showingFinishConfirmation = false
    @State private var showingCancelConfirmation = false
    @State private var editingContext: IOSWorkoutSetEditorContext?
    @State private var adHocExerciseIDs: [Int64] = []
    /// Rest timers are keyed by the immutable exercise row identity, matching
    /// Android's `Map<WorkoutExerciseKey, RestTimerUi>` so supersets can count
    /// down independently.
    @State private var restTimers: [String: IOSWorkoutRestTimer] = [:]
    @State private var localErrorMessage: String?

    private var data: ILTrainingData? { store.data }

    private var activeSession: ILWorkoutSession? { data?.activeSession }

    var body: some View {
        screenContent
            .ironLogScreenBackground(
                ember: theme.palette(for: colorScheme).background,
                emberNavigationBar: theme.palette(for: colorScheme).background
            )
            .navigationTitle(screenTitle)
            .navigationBarTitleDisplayMode(.inline)
            .sheet(isPresented: $showingExercisePicker) {
                exercisePickerSheet
            }
            .sheet(item: $editingContext) { context in
                setEditorSheet(context: context)
            }
            .sheet(isPresented: $showingNotes) {
                notesSheet
            }
            .alert(item: errorAlertBinding) { alert in
                Alert(
                    title: Text("Workout"),
                    message: Text(alert.message),
                    dismissButton: .default(Text("OK"))
                )
            }
            .sheet(isPresented: $showingFinishConfirmation) {
                if let session = activeSession, let data {
                    IOSWorkoutFinishSheet(rows: exerciseRows(session: session, data: data), busy: isFinishing || store.isBusy, error: localErrorMessage,
                        onContinue: { showingFinishConfirmation = false }, onFinish: finishWorkout)
                        .interactiveDismissDisabled(isFinishing)
                }
            }
            .confirmationDialog(
                "Workout verwerfen?",
                isPresented: $showingCancelConfirmation,
                titleVisibility: .visible
            ) {
                Button("Workout verwerfen", role: .destructive) { cancelWorkout() }
                Button("Weiter trainieren", role: .cancel) { }
            } message: {
                Text("Die laufende Session und ihre Sätze werden verworfen.")
            }
            .task(id: activeSession?.id) {
                await restorePresentationState()
            }
            .onAppear { updateIdleTimer() }
            .onDisappear {
                UIApplication.shared.isIdleTimerDisabled = false
            }
            .onChange(of: settings.state.timerKeepScreenOn) { _, _ in updateIdleTimer() }
            .onChange(of: settings.state.deloadMode) { _, _ in reconcileRestTimerAfterTargetChange() }
            .onChange(of: activeSession?.id) { _, _ in updateIdleTimer() }
            .onChange(of: scenePhase) { _, _ in updateIdleTimer() }
    }

    /// Keeping the data branch separate from the sheet/lifecycle modifiers makes each
    /// expression small enough for Swift's result-builder type checker. This also keeps the
    /// active workout's identity stable when a sheet is presented.
    @ViewBuilder
    private var screenContent: some View {
        if let data {
            if data.activeSession == nil, let session = data.workoutSessions.first(where: { $0.id == Int64(completedSessionID) && $0.endTime != nil }) {
                IOSWorkoutCompletionScreen(session: session, rows: exerciseRows(session: session, data: data), unitSystem: settings.state.unitSystem,
                    onClose: { completedSessionID = 0 })
            } else if let session = data.activeSession {
                activeWorkout(session: session, data: data)
            } else {
                IOSWorkoutStartView(
                    plans: data.trainingPlans.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending },
                    onStartFree: startFreeWorkout,
                    onStartPlan: startPlan
                )
            }
        } else {
            unavailableView
        }
    }

    private var exercisePickerSheet: some View {
        IOSWorkoutExercisePicker(
            exercises: data?.visibleExercises.sorted {
                $0.name.localizedStandardCompare($1.name) == .orderedAscending
            } ?? [],
            excludedIDs: Set(adHocExerciseIDs),
            onSelect: addAdHocExercise
        )
    }

    private func setEditorSheet(context: IOSWorkoutSetEditorContext) -> some View {
        IOSWorkoutSetEditor(context: context, onSave: saveSet)
    }

    @ViewBuilder
    private var notesSheet: some View {
        if let session = activeSession {
            IOSWorkoutNotesSheet(initialNotes: session.notes, onSave: saveNotes)
        }
    }

    private var errorAlertBinding: Binding<IOSWorkoutAlert?> {
        Binding(
            get: { localErrorMessage.map(IOSWorkoutAlert.init(message:)) },
            set: { _ in localErrorMessage = nil }
        )
    }

    private func restorePresentationState() async {
        guard let activeSession else {
            adHocExerciseIDs = []
            restTimers = [:]
            IOSWorkoutRestTimerPersistence.clear()
            updateIdleTimer()
            return
        }

        loadAdHocExercises(for: activeSession.id)
        if let data {
            restoreRestTimers(for: activeSession, data: data)
        } else {
            restTimers = [:]
            IOSWorkoutRestTimerPersistence.clear()
        }
        updateIdleTimer()
    }

    private var unavailableView: some View {
        ContentUnavailableView {
            Label("Trainingsdaten nicht verfügbar", systemImage: "externaldrive.badge.questionmark")
        } description: {
            Text(store.errorMessage ?? "Die lokale Trainingsdatei konnte nicht geladen werden.")
        }
        .padding()
    }

    private var screenTitle: String {
        guard let activeSession else { return "Workout" }
        return activeSession.name.isEmpty ? "Workout" : activeSession.name
    }

    @ViewBuilder
    private func activeWorkout(session: ILWorkoutSession, data: ILTrainingData) -> some View {
        let rows = exerciseRows(session: session, data: data)
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 16) {
                workoutHeader(session: session)

                restTimersView(rows: rows)

                if let message = store.errorMessage ?? localErrorMessage {
                    Label(message, systemImage: "exclamationmark.triangle.fill")
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .padding(.horizontal)
                }

                if rows.isEmpty {
                    ContentUnavailableView {
                        Label("Noch keine Übung", systemImage: "figure.strengthtraining.traditional")
                    } description: {
                        Text("Füge eine Übung hinzu, um den ersten Satz zu loggen.")
                    } actions: {
                        Button("Übung hinzufügen") { showingExercisePicker = true }
                            .buttonStyle(.borderedProminent)
                    }
                    .padding(.vertical, 30)
                } else {
                    ForEach(exerciseRenderGroups(rows)) { group in
                        exerciseRenderGroup(group, sessionID: session.id)
                    }
                }

                Button {
                    showingExercisePicker = true
                } label: {
                    Label("Übung hinzufügen", systemImage: "plus.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .padding(.horizontal)
                .padding(.bottom, 24)
                .accessibilityHint("Öffnet die Suche nach einer weiteren Übung")
            }
            .padding(.top, 8)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: 12) {
                Button {
                    showingCancelConfirmation = true
                } label: {
                    Label("Verwerfen", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
                .tint(.red)

                Button {
                    showingFinishConfirmation = true
                } label: {
                    Label("Beenden", systemImage: "checkmark.circle.fill")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
            .padding(.horizontal)
            .padding(.vertical, 10)
            .background(.thinMaterial)
        }
    }

    @ViewBuilder
    private func restTimersView(rows: [IOSWorkoutExerciseRow]) -> some View {
        if !restTimers.isEmpty {
            let groups = exerciseRenderGroups(rows)
            VStack(spacing: 8) {
                ForEach(orderedRestTimers) { timer in
                    restTimerView(timer, rows: rows, groups: groups)
                }
            }
            .padding(.horizontal)
        }
    }

    @ViewBuilder
    private func exerciseRenderGroup(
        _ group: IOSWorkoutExerciseRenderGroup,
        sessionID: Int64
    ) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if let groupID = group.supersetGroupID {
                IOSWorkoutSupersetHeader(
                    groupID: groupID,
                    exerciseCount: group.rows.count,
                    exerciseNames: group.rows.map { $0.exercise.name }.joined(separator: " • ")
                )
                .padding(.horizontal)
            }

            ForEach(group.rows.indices, id: \.self) { index in
                let row = group.rows[index]
                IOSWorkoutExerciseCard(
                    row: row,
                    unitSystem: settings.state.unitSystem,
                    intensitySystem: settings.state.intensitySystem,
                    supersetTint: IOSWorkoutSupersetTint.color(
                        for: group.supersetGroupID,
                        index: index
                    ),
                    onAddSet: { presentNewSet(for: row, sessionID: sessionID) },
                    onApplyCoachWeight: { weightKg in
                        presentNewSet(
                            for: row,
                            sessionID: sessionID,
                            suggestedWeightKg: weightKg,
                            defaultSetType: .normal
                        )
                    },
                    onEditSet: { set in presentEditSet(set, row: row, sessionID: sessionID) },
                    onDeleteSet: deleteSet,
                    sessionID: sessionID,
                    onSaveSet: saveSet
                )
                .padding(.horizontal)
            }
        }
    }

    private func workoutHeader(session: ILWorkoutSession) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(session.name.isEmpty ? "Freies Workout" : session.name)
                        .font(.title2.weight(.bold))
                    Text(session.planId == nil ? "Freie Session" : "Plan-Session")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                IOSWorkoutElapsedView(startDate: session.startDate)
            }

            HStack(spacing: 10) {
                Label("Seit \(session.startDate.formatted(date: .omitted, time: .shortened))", systemImage: "clock")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Spacer()

                Button {
                    showingNotes = true
                } label: {
                    Label(session.notes.isEmpty ? "Notiz" : "Notiz bearbeiten", systemImage: "note.text")
                }
                .buttonStyle(.bordered)
                .controlSize(.small)
            }
            .accessibilityElement(children: .contain)
        }
        .padding(.horizontal)
    }

    private func exerciseRows(session: ILWorkoutSession, data: ILTrainingData) -> [IOSWorkoutExerciseRow] {
        // A plan snapshot remains renderable even if its catalog exercise was archived after
        // the session started. The picker still uses `visibleExercises` above, but active rows
        // resolve against the complete catalog to preserve historical identity.
        let exerciseByID = Dictionary(uniqueKeysWithValues: data.exercises.map { ($0.id, $0) })
        let sessionSets = data.sets(for: session)
        let targets = data.workoutPlanTargets
            .filter { $0.sessionId == session.id }
            .sorted { lhs, rhs in
                lhs.orderIndex == rhs.orderIndex ? lhs.id < rhs.id : lhs.orderIndex < rhs.orderIndex
            }

        var rows: [IOSWorkoutExerciseRow] = []
        for target in targets {
            guard let exercise = exerciseByID[target.exerciseId] else { continue }
            let sets = sessionSets
                .filter { $0.planTargetSnapshotId == target.id }
                .sorted { lhs, rhs in lhs.setNumber == rhs.setNumber ? lhs.id < rhs.id : lhs.setNumber < rhs.setNumber }
            rows.append(IOSWorkoutExerciseRow(
                id: "target-\(target.id)",
                exercise: exercise,
                target: target,
                displayTarget: session.endTime == nil ? deloadAdjustedTarget(target.target) : target.target,
                sets: sets,
                previousWorkWeightKg: previousWorkWeight(exerciseID: exercise.id, currentSession: session, data: data),
                nextSetRecommendation: IOSWorkoutNextSetCoach.evaluate(
                    sets: sets,
                    target: target,
                    intensitySystem: settings.state.intensitySystem
                ),
                deloadMode: session.endTime == nil ? settings.state.deloadMode : "NONE"
            ))
        }

        let adHocIDs = Set(adHocExerciseIDs).union(
            sessionSets.filter { $0.planTargetSnapshotId == nil }.map(\.exerciseId)
        )
        for exerciseID in adHocIDs.sorted() {
            guard let exercise = exerciseByID[exerciseID] else { continue }
            let sets = sessionSets
                .filter { $0.planTargetSnapshotId == nil && $0.exerciseId == exerciseID }
                .sorted { lhs, rhs in lhs.setNumber == rhs.setNumber ? lhs.id < rhs.id : lhs.setNumber < rhs.setNumber }
            rows.append(IOSWorkoutExerciseRow(
                id: "adhoc-\(exerciseID)",
                exercise: exercise,
                target: nil,
                displayTarget: nil,
                sets: sets,
                previousWorkWeightKg: previousWorkWeight(exerciseID: exercise.id, currentSession: session, data: data),
                nextSetRecommendation: IOSWorkoutNextSetCoach.evaluate(
                    sets: sets,
                    target: nil,
                    intensitySystem: settings.state.intensitySystem
                ),
                deloadMode: session.endTime == nil ? settings.state.deloadMode : "NONE"
            ))
        }
        return rows
    }

    /// Builds contiguous superset runs without changing the order or identity of
    /// the target snapshots. This deliberately operates after the rows have been
    /// resolved, so ad-hoc exercises can never be accidentally folded into a plan
    /// superset and repeated planned exercises remain separate rows.
    private func exerciseRenderGroups(_ rows: [IOSWorkoutExerciseRow]) -> [IOSWorkoutExerciseRenderGroup] {
        guard !rows.isEmpty else { return [] }

        var groups: [IOSWorkoutExerciseRenderGroup] = []
        var cursor = 0

        while cursor < rows.count {
            let groupID = rows[cursor].target?.supersetGroupId
            guard let groupID else {
                let row = rows[cursor]
                groups.append(
                    IOSWorkoutExerciseRenderGroup(
                        id: "single-\(row.id)",
                        supersetGroupID: nil,
                        rows: [row]
                    )
                )
                cursor += 1
                continue
            }

            var endExclusive = cursor + 1
            while endExclusive < rows.count,
                  rows[endExclusive].target?.supersetGroupId == groupID {
                endExclusive += 1
            }

            let run = Array(rows[cursor..<endExclusive])
            if run.count < 2 {
                let row = run[0]
                groups.append(
                    IOSWorkoutExerciseRenderGroup(
                        id: "single-\(row.id)",
                        supersetGroupID: nil,
                        rows: [row]
                    )
                )
            } else {
                groups.append(
                    IOSWorkoutExerciseRenderGroup(
                        id: "superset-\(groupID)-\(run.map(\.id).joined(separator: "-"))",
                        supersetGroupID: groupID,
                        rows: run
                    )
                )
            }
            cursor = endExclusive
        }

        return groups
    }

    /// Applies the shared display-only deload helper. The returned DTO is deliberately a fresh
    /// local value: `target` remains the immutable persisted snapshot used for set identity and
    /// all backend writes.
    private func deloadAdjustedTarget(_ target: ILProgressionTarget) -> ILProgressionTarget {
        let adjusted = DeloadTargetAdjustment.shared.adjustByName(
            sets: Int32(target.sets),
            reps: Int32(target.reps),
            weightKg: target.weightKg,
            modeName: settings.state.deloadMode
        )
        return ILProgressionTarget(
            sets: Int(adjusted.sets),
            reps: Int(adjusted.reps),
            weightKg: adjusted.weightKg
        )
    }

    private func previousWorkWeight(
        exerciseID: Int64,
        currentSession: ILWorkoutSession,
        data: ILTrainingData
    ) -> Double? {
        let candidates = data.workoutSessions
            .filter { session in
                guard session.id != currentSession.id, session.endTime != nil else { return false }
                if settings.state.shareWeightHistoryAcrossContexts {
                    // Android's SharedPlan scope keeps history within the selected plan;
                    // genuinely free sessions use the global scope.
                    return currentSession.planId == nil || session.planId == currentSession.planId
                }
                if let planID = currentSession.planId {
                    if let metaPlanID = currentSession.metaPlanId {
                        return session.planId == planID && session.metaPlanId == metaPlanID
                    }
                    return session.planId == planID && session.metaPlanId == nil
                }
                // Free sessions have no context and therefore use global history.
                return true
            }
            .sorted {
                $0.startTime == $1.startTime ? $0.id > $1.id : $0.startTime > $1.startTime
            }

        for session in candidates {
            let exerciseSets = data.sets(for: session)
                .filter { $0.exerciseId == exerciseID }
            // Android selects the most recent completed session that contains the
            // exercise. A warmup-only session therefore intentionally suppresses
            // an older work-set hint instead of leaking across sessions.
            guard !exerciseSets.isEmpty else { continue }
            return exerciseSets
                .filter { resolvedIOSWorkoutSetType($0) == .normal }
                .sorted {
                    $0.completedAt == $1.completedAt ? $0.id > $1.id : $0.completedAt > $1.completedAt
                }
                .first?.weightKg
        }
        return nil
    }

    private func startFreeWorkout(name: String) {
        Task {
            let success = await store.startWorkout(name: name.isEmpty ? "Freies Workout" : name)
            if !success {
                localErrorMessage = store.errorMessage ?? "Workout konnte nicht gestartet werden."
            } else {
                store.errorMessage = nil
            }
        }
    }

    private func startPlan(_ plan: ILTrainingPlan) {
        Task {
            let success = await store.startWorkout(name: plan.name, planId: plan.id)
            if !success {
                localErrorMessage = store.errorMessage ?? "Plan konnte nicht gestartet werden."
            } else {
                store.errorMessage = nil
            }
        }
    }

    private func addAdHocExercise(_ exercise: ILExercise) {
        guard let session = activeSession, !adHocExerciseIDs.contains(exercise.id) else { return }
        adHocExerciseIDs.append(exercise.id)
        saveAdHocExercises(for: session.id)
    }

    private func presentNewSet(
        for row: IOSWorkoutExerciseRow,
        sessionID: Int64,
        suggestedWeightKg: Double? = nil,
        defaultSetType: IOSWorkoutSetType? = nil
    ) {
        editingContext = IOSWorkoutSetEditorContext(
            id: "new-\(sessionID)-\(row.id)-\(UUID().uuidString)",
            sessionID: sessionID,
            row: row,
            existingSet: nil,
            defaultSetType: defaultSetType ?? (settings.state.defaultWarmupFlag ? .warmup : .normal),
            suggestedWeightKg: suggestedWeightKg
        )
    }

    private func presentEditSet(_ set: ILWorkoutSet, row: IOSWorkoutExerciseRow, sessionID: Int64) {
        editingContext = IOSWorkoutSetEditorContext(
            id: "edit-\(set.id)",
            sessionID: sessionID,
            row: row,
            existingSet: set,
            defaultSetType: resolvedIOSWorkoutSetType(set),
            intention: store.intention(forSetId: set.id)
        )
    }

    private func saveSet(_ set: ILWorkoutSet, intention: ILSetIntention) async -> Bool {
        let success = await store.saveSet(set, intention: intention)
        guard success else {
            localErrorMessage = store.errorMessage ?? "Der Satz konnte nicht gespeichert werden."
            return false
        }
        store.errorMessage = nil

        // Android keeps one timer per exercise key. A warmup or the final planned
        // work set removes only that row's timer; another superset member keeps
        // counting independently.
        if set.id == 0, let session = activeSession {
            let rowKey = iosWorkoutRestTimerRowKey(for: set)
            if resolvedIOSWorkoutSetType(set) == .warmup {
                dismissRestTimer(rowKey: rowKey)
            } else if let targetID = set.planTargetSnapshotId,
                      let target = store.data?.workoutPlanTargets.first(where: { $0.id == targetID }),
                      let savedSets = store.data?.sets(for: session) {
                let completedNormalSets = savedSets.filter {
                    $0.planTargetSnapshotId == targetID && resolvedIOSWorkoutSetType($0) == .normal
                }.count
                if completedNormalSets >= deloadAdjustedTarget(target.target).sets {
                    dismissRestTimer(rowKey: rowKey)
                } else {
                    startRestTimer(sessionID: session.id, rowKey: rowKey)
                }
            } else if resolvedIOSWorkoutSetType(set) != .warmup {
                // Ad-hoc exercises have no immutable target to complete, so their
                // timer remains elapsed/countdown until the athlete dismisses it.
                // Android starts a timer for every non-warmup submission too.
                startRestTimer(sessionID: session.id, rowKey: rowKey)
            }
        }
        return true
    }

    private func deleteSet(_ set: ILWorkoutSet) {
        Task {
            let success = await store.command("set.delete", fields: ["id": set.id])
            if !success {
                localErrorMessage = store.errorMessage ?? "Der Satz konnte nicht gelöscht werden."
            } else {
                store.errorMessage = nil
            }
        }
    }

    private func finishWorkout() {
        guard let session = activeSession, !isFinishing, !store.isBusy else { return }
        isFinishing = true
        Task {
            defer { isFinishing = false }
            let success = await store.command("workout.finish", fields: ["sessionId": session.id])
            if success {
                completedSessionID = Int(session.id)
                showingFinishConfirmation = false
                store.errorMessage = nil
                restTimers = [:]
                IOSWorkoutRestTimerPersistence.clear(sessionID: session.id)
                adHocExerciseIDs = []
                UserDefaults.standard.removeObject(forKey: adHocKey(for: session.id))
            } else {
                localErrorMessage = store.errorMessage ?? "Workout konnte nicht beendet werden."
            }
        }
    }

    private func cancelWorkout() {
        guard let session = activeSession else { return }
        Task {
            let success = await store.command("workout.cancel", fields: ["sessionId": session.id])
            if success {
                store.errorMessage = nil
                restTimers = [:]
                IOSWorkoutRestTimerPersistence.clear(sessionID: session.id)
                adHocExerciseIDs = []
                UserDefaults.standard.removeObject(forKey: adHocKey(for: session.id))
            } else {
                localErrorMessage = store.errorMessage ?? "Workout konnte nicht verworfen werden."
            }
        }
    }

    private func saveNotes(_ notes: String) {
        guard let session = activeSession else { return }
        let updated = ILWorkoutSession(
            id: session.id,
            startTime: session.startTime,
            endTime: session.endTime,
            durationSeconds: session.durationSeconds,
            name: session.name,
            notes: notes,
            planId: session.planId,
            metaPlanId: session.metaPlanId
        )
        Task {
            do {
                let success = await store.command("workout.update", fields: ["session": try store.encoded(updated)])
                if !success {
                    localErrorMessage = store.errorMessage ?? "Notiz konnte nicht gespeichert werden."
                } else {
                    store.errorMessage = nil
                }
            } catch {
                localErrorMessage = error.localizedDescription
            }
        }
    }

    private var orderedRestTimers: [IOSWorkoutRestTimer] {
        restTimers.values.sorted {
            $0.startedAtEpochMillis == $1.startedAtEpochMillis
                ? $0.rowKey < $1.rowKey
                : $0.startedAtEpochMillis < $1.startedAtEpochMillis
        }
    }

    @ViewBuilder
    private func restTimerView(
        _ timer: IOSWorkoutRestTimer,
        rows: [IOSWorkoutExerciseRow],
        groups: [IOSWorkoutExerciseRenderGroup]
    ) -> some View {
        let row = rows.first { $0.id == timer.rowKey }
        IOSWorkoutRestTimerView(
            timer: timer,
            title: row?.exercise.name,
            accentColor: restTimerTint(for: timer.rowKey, groups: groups),
            onDismiss: { dismissRestTimer(rowKey: timer.rowKey) },
            onComplete: { dismissRestTimer(rowKey: timer.rowKey) }
        )
    }

    private func restTimerTint(
        for rowKey: String,
        groups: [IOSWorkoutExerciseRenderGroup]
    ) -> Color? {
        guard let group = groups.first(where: { $0.rows.contains { $0.id == rowKey } }),
              let groupID = group.supersetGroupID,
              let index = group.rows.firstIndex(where: { $0.id == rowKey })
        else { return nil }
        return IOSWorkoutSupersetTint.color(for: groupID, index: index)
    }

    private func iosWorkoutRestTimerRowKey(for set: ILWorkoutSet) -> String {
        if let targetID = set.planTargetSnapshotId {
            return "target-\(targetID)"
        }
        return "adhoc-\(set.exerciseId)"
    }

    private func startRestTimer(sessionID: Int64, rowKey: String) {
        let timer = IOSWorkoutRestTimer.make(
            sessionID: sessionID,
            rowKey: rowKey,
            durationSeconds: max(
                0,
                settings.state.autoRestTimerEnabled ? settings.state.defaultRestTimeSeconds : 0
            )
        )
        restTimers[rowKey] = timer
        IOSWorkoutRestTimerPersistence.upsert(timer)
    }

    private func dismissRestTimer(rowKey: String) {
        let sessionID = restTimers[rowKey]?.sessionID ?? activeSession?.id
        restTimers.removeValue(forKey: rowKey)
        if let sessionID {
            IOSWorkoutRestTimerPersistence.remove(sessionID: sessionID, rowKey: rowKey)
        }
    }

    private func restoreRestTimers(for session: ILWorkoutSession, data: ILTrainingData) {
        let rows = exerciseRows(session: session, data: data)
        let persisted = IOSWorkoutRestTimerPersistence.load(sessionID: session.id)
        let validRowIDs = Set(rows.map(\.id))
        let validTimers = persisted.filter { rowKey, timer in
            guard timer.sessionID == session.id, validRowIDs.contains(rowKey) else { return false }
            guard let row = rows.first(where: { $0.id == rowKey }) else { return false }
            guard let remaining = row.remainingPlannedSets else {
                // Ad-hoc rows have no fixed final set and keep their timer until
                // the user dismisses it.
                return row.target == nil
            }
            return remaining > 0
        }
        restTimers = validTimers
        IOSWorkoutRestTimerPersistence.saveAll(Array(validTimers.values))
    }

    private func reconcileRestTimerAfterTargetChange() {
        guard let session = activeSession, let data else {
            restTimers = [:]
            IOSWorkoutRestTimerPersistence.clear()
            return
        }
        restoreRestTimers(for: session, data: data)
    }

    private func adHocKey(for sessionID: Int64) -> String {
        "ironlog.workout.adhocExercises.\(sessionID)"
    }

    private func loadAdHocExercises(for sessionID: Int64) {
        let raw = UserDefaults.standard.string(forKey: adHocKey(for: sessionID)) ?? ""
        adHocExerciseIDs = raw
            .split(separator: ",")
            .compactMap { Int64($0) }
    }

    private func saveAdHocExercises(for sessionID: Int64) {
        UserDefaults.standard.set(
            adHocExerciseIDs.map(String.init).joined(separator: ","),
            forKey: adHocKey(for: sessionID)
        )
    }

    private func updateIdleTimer() {
        UIApplication.shared.isIdleTimerDisabled = scenePhase == .active &&
            activeSession != nil &&
            settings.state.timerKeepScreenOn
    }
}

private struct IOSWorkoutAlert: Identifiable {
    let id = UUID()
    let message: String
}

private struct IOSWorkoutElapsedView: View {
    let startDate: Date

    var body: some View {
        TimelineView(.periodic(from: Date(), by: 1)) { context in
            VStack(alignment: .trailing, spacing: 2) {
                Text(iosWorkoutFormatDuration(max(0, Int(context.date.timeIntervalSince(startDate)))))
                    .font(.title3.monospacedDigit().weight(.semibold))
                Text("Workout-Zeit")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
            .accessibilityLabel("Workout-Zeit")
        }
    }
}

private struct IOSWorkoutNotesSheet: View {
    @Environment(\.dismiss) private var dismiss
    let initialNotes: String
    let onSave: (String) -> Void

    @State private var notes: String

    init(initialNotes: String, onSave: @escaping (String) -> Void) {
        self.initialNotes = initialNotes
        self.onSave = onSave
        _notes = State(initialValue: initialNotes)
    }

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 12) {
                Text("Notizen zum laufenden Workout")
                    .font(.headline)
                TextEditor(text: $notes)
                    .frame(minHeight: 180)
                    .padding(8)
                    .overlay {
                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                            .stroke(.quaternary)
                    }
                    .accessibilityLabel("Workout-Notizen")
                Spacer()
            }
            .padding()
            .navigationTitle("Notizen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Speichern") {
                        onSave(notes.trimmingCharacters(in: .whitespacesAndNewlines))
                        dismiss()
                    }
                }
            }
        }
    }
}
