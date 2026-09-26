import SwiftUI

struct IOSWorkoutFinishSheet: View {
    let rows: [IOSWorkoutExerciseRow]
    let busy: Bool
    let error: String?
    let onContinue: () -> Void
    let onFinish: () -> Void
    private var remaining: Int { rows.compactMap(\.remainingPlannedSets).reduce(0, +) }
    private var planned: Int { rows.reduce(0) { $0 + $1.loggingSlots.count } }
    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            Text("Training beenden?").font(.title2.bold())
            Text(remaining > 0 ? "Noch \(remaining) \(remaining == 1 ? "geplanter Satz" : "geplante Sätze") offen. \(planned - remaining) von \(planned) absolviert." : "Deine bestätigten Sätze werden gespeichert.")
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(rows.filter { ($0.remainingPlannedSets ?? 0) > 0 }) { row in
                        HStack { Text(row.exercise.name); Spacer(); Text("\(row.remainingPlannedSets ?? 0) offen").foregroundStyle(.secondary) }
                    }
                }
            }.frame(maxHeight: 170)
            if let error { Text(error).font(.footnote).foregroundStyle(.red) }
            Button(action: onContinue) { Text("Weitertrainieren").frame(maxWidth: .infinity).padding(.vertical, 7) }
                .buttonStyle(.borderedProminent).disabled(busy)
            Button(action: onFinish) { Text(busy ? "Speichert …" : remaining > 0 ? "Trotzdem beenden" : "Training beenden").frame(maxWidth: .infinity).padding(.vertical, 7) }
                .buttonStyle(.bordered).disabled(busy)
            Text("Nur absolvierte Sätze zählen zum Ergebnis.").font(.caption).foregroundStyle(.secondary)
        }.padding(24).presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
    }
}

struct IOSWorkoutCompletionScreen: View {
    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(IOSTrainingStore.self) private var store
    let session: ILWorkoutSession
    let rows: [IOSWorkoutExerciseRow]
    let unitSystem: String
    let onClose: () -> Void
    @State private var showPlanChanges = false
    @State private var editPlan = false
    @State private var planApplied = false
    private var sets: [ILWorkoutSet] { store.data?.workoutSets.filter { $0.sessionId == session.id && $0.reps > 0 } ?? [] }
    private var remaining: Int { rows.compactMap(\.remainingPlannedSets).reduce(0, +) }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Label(remaining > 0 ? "Vorzeitig beendet · \(sets.count) bestätigte Sätze" : "Training gespeichert", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green).padding().frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.green.opacity(0.1), in: RoundedRectangle(cornerRadius: 14))
                Text(remaining > 0 ? "Teiltraining gespeichert" : "Training geschafft").font(.title.bold())
                Text(session.name).foregroundStyle(.secondary)
                HStack(spacing: 12) {
                    metric("Dauer", "\(session.durationSeconds / 60) min")
                    metric("Sätze", "\(sets.count)")
                }
                metric("Volumen", iosWorkoutDisplayWeight(kilograms: sets.reduce(0) { $0 + $1.weightKg * Double($1.reps) }, unitSystem: unitSystem))
                ForEach(rows.filter { !$0.sets.isEmpty }) { row in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(row.exercise.name).font(.headline)
                        ForEach(row.sets.filter { $0.reps > 0 }) { set in
                            Text("Satz \(set.setNumber) · \(iosSetValueText(weightKg: set.weightKg, reps: set.reps, unitSystem: unitSystem))").font(.subheadline).monospacedDigit()
                        }
                    }.padding().frame(maxWidth: .infinity, alignment: .leading)
                        .background(theme.palette(for: colorScheme).surface, in: RoundedRectangle(cornerRadius: 14))
                }
                if session.planId != nil {
                    Button(planApplied ? "Satzwerte übernommen" : "Planänderungen prüfen") { showPlanChanges = true }
                        .buttonStyle(.bordered).disabled(planApplied)
                }
            }.padding(20)
        }
        .navigationTitle("Training gespeichert")
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig", action: onClose) } }
        .safeAreaInset(edge: .bottom) {
            NavigationLink { IOSWorkoutDetailScreen(sessionID: session.id) } label: {
                Text("Trainingsdetails öffnen").frame(maxWidth: .infinity).padding(.vertical, 7)
            }.buttonStyle(.borderedProminent).padding().background(.bar)
        }
        .sheet(isPresented: $showPlanChanges) {
            IOSWorkoutPlanChangesSheet(sessionID: session.id, rows: rows, unitSystem: unitSystem,
                onEditor: { showPlanChanges = false; editPlan = true }, onApplied: { planApplied = true })
        }
        .navigationDestination(isPresented: $editPlan) {
            IOSPlanEditorScreen(planID: session.planId, unitSystem: unitSystem)
        }
    }
    private func metric(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.title2.monospacedDigit().bold())
        }.padding().frame(maxWidth: .infinity, alignment: .leading)
            .background(theme.palette(for: colorScheme).surface, in: RoundedRectangle(cornerRadius: 14))
    }
}

private struct IOSWorkoutPlanChangesSheet: View {
    @Environment(IOSTrainingStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    let sessionID: Int64
    let rows: [IOSWorkoutExerciseRow]
    let unitSystem: String
    let onEditor: () -> Void
    let onApplied: () -> Void
    @State private var choice = 0
    @State private var busy = false
    @State private var error: String?
    private let titles = ["Nur dieses Training", "Satzwerte übernehmen", "Plan im Editor anpassen"]
    private let subtitles = ["Plan unverändert lassen", "Nur ausgeführte Sätze aktualisieren · individuelle Vorgaben, manuelle Progression", "Übungen und Vorgaben selbst bearbeiten"]
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Wähle bewusst").font(.title.bold())
                    Text("Das Training ist gespeichert. Was soll für das nächste Training gelten?").foregroundStyle(.secondary)
                    ForEach(rows.filter { $0.target != nil }) { row in
                        let target = row.target!
                        let slots = target.setTargets.isEmpty ? Array(repeating: ILPlannedSet(reps: target.target.reps, weightKg: target.target.weightKg), count: min(100, max(0, target.target.sets))) : target.setTargets
                        ForEach(Array(row.matchedSlots.enumerated()), id: \.offset) { index, match in
                            if let match, slots.indices.contains(index), row.sets.indices.contains(match) {
                                let set = row.sets[match]
                                if set.reps != slots[index].reps || set.weightKg != slots[index].weightKg {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text("\(row.exercise.name) · Satz \(index + 1)").font(.headline)
                                        Text("Plan   \(iosWorkoutDisplayWeight(kilograms: slots[index].weightKg, unitSystem: unitSystem)) × \(slots[index].reps)")
                                        Text("Heute  \(iosWorkoutDisplayWeight(kilograms: set.weightKg, unitSystem: unitSystem)) × \(set.reps)").foregroundStyle(.tint)
                                    }.padding().frame(maxWidth: .infinity, alignment: .leading)
                                        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
                                }
                            }
                        }
                    }
                    ForEach(0..<3, id: \.self) { index in
                        Button { choice = index } label: {
                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: choice == index ? "largecircle.fill.circle" : "circle").font(.title2)
                                VStack(alignment: .leading, spacing: 6) {
                                    Text(titles[index]).font(.headline).foregroundStyle(.primary)
                                    Text(subtitles[index]).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 0)
                            }.padding().frame(maxWidth: .infinity, alignment: .leading)
                                .background(choice == index ? Color.accentColor.opacity(0.12) : Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
                                .overlay { RoundedRectangle(cornerRadius: 14).stroke(choice == index ? Color.accentColor : .secondary.opacity(0.3)) }
                        }.buttonStyle(.plain).disabled(busy).accessibilityValue(choice == index ? "Ausgewählt" : "Nicht ausgewählt")
                    }
                    Text("Offene Übungen und Sätze bleiben im Plan.").font(.caption)
                    if let error { Text(error).foregroundStyle(.red) }
                }.padding(20)
            }
            .navigationTitle("Planänderungen").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Später") { dismiss() }.disabled(busy) } }
            .safeAreaInset(edge: .bottom) {
                Button {
                    switch choice {
                    case 0: dismiss()
                    case 2: dismiss(); onEditor()
                    default: Task {
                        busy = true
                        let success = await store.command("plan.applyPerformedSets", fields: ["sessionId": sessionID])
                        busy = false
                        if success { onApplied(); dismiss() } else { error = store.errorMessage ?? "Plan konnte nicht geändert werden." }
                    }
                    }
                } label: {
                    Text(busy ? "Speichert …" : ["Plan unverändert lassen", "Satzwerte übernehmen", "Planeditor öffnen"][choice]).frame(maxWidth: .infinity).padding(.vertical, 7)
                }.buttonStyle(.borderedProminent).disabled(busy).padding().background(.bar)
            }
            .interactiveDismissDisabled(busy)
        }
    }
}
