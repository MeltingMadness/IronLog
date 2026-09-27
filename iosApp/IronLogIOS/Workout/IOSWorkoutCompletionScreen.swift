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
            Text("Training beenden?").font(.geist(.title2, weight: .bold))
            Text(remaining > 0 ? "Noch \(remaining) \(remaining == 1 ? "geplanter Satz" : "geplante Sätze") offen. \(planned - remaining) von \(planned) absolviert." : "Deine bestätigten Sätze werden gespeichert.")
            ScrollView {
                VStack(spacing: 12) {
                    ForEach(rows.filter { ($0.remainingPlannedSets ?? 0) > 0 }) { row in
                        HStack { Text(row.exercise.name); Spacer(); Text("\(row.remainingPlannedSets ?? 0) offen").ironLogSecondaryText() }
                    }
                }
            }.frame(maxHeight: 170)
            if let error { Text(error).font(.geist(.footnote)).foregroundStyle(.red) }
            Button(action: onContinue) { Text("Weitertrainieren").frame(maxWidth: .infinity).padding(.vertical, 7) }
                .buttonStyle(.borderedProminent).ironLogButtonText().disabled(busy)
            Button(action: onFinish) { Text(busy ? "Speichert …" : remaining > 0 ? "Trotzdem beenden" : "Training beenden").frame(maxWidth: .infinity).padding(.vertical, 7) }
                .buttonStyle(.bordered).disabled(busy)
            Text("Nur absolvierte Sätze zählen zum Ergebnis.").font(.geist(.caption)).ironLogSecondaryText()
        }.padding(24).presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
    }
}

struct IOSWorkoutCompletionScreen: View {
    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme
    @Environment(IOSTrainingStore.self) private var store
    @Environment(\.ironLogAppearance) private var appearance
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
        if appearance == .liquidGlass {
            glassBody
        } else {
            emberBody
        }
    }

    /// Records reached during this workout, grouped per exercise (same rule as Android).
    private var sessionRecords: [(exercise: String, types: [String])] {
        guard let data = store.data else { return [] }
        let end = session.endTime ?? Int64(Date().timeIntervalSince1970 * 1_000)
        let exerciseIDs = Set(sets.map(\.exerciseId))
        let names = Dictionary(uniqueKeysWithValues: data.exercises.map { ($0.id, $0.name) })
        let order = ["MAX_WEIGHT", "MAX_REPS", "MAX_VOLUME", "MAX_E1RM"]
        return Dictionary(grouping: data.personalRecords.filter {
            exerciseIDs.contains($0.exerciseId) && $0.achievedAt >= session.startTime && $0.achievedAt <= end
        }, by: \.exerciseId)
        .map { id, records in
            let types = Array(Set(records.map { $0.type.uppercased() }))
                .sorted { (order.firstIndex(of: $0) ?? 9) < (order.firstIndex(of: $1) ?? 9) }
            return (exercise: names[id] ?? "Übung", types: types)
        }
        .sorted { $0.exercise < $1.exercise }
    }

    /// Liquid Glass: "Geschafft.", three lenses, records on tinted glass, the
    /// logged sets and "Fertig" as the bright main action.
    private var glassBody: some View {
        let volume = sets.reduce(0) { $0 + $1.weightKg * Double($1.reps) }
        let volumeText = ilVolumeText(volume.rounded(), unitSystem: unitSystem)
        let trained = rows.filter { row in row.sets.contains { $0.reps > 0 } }
        let records = sessionRecords
        let dark = colorScheme == .dark
        return ScrollView {
            VStack(spacing: 20) {
                VStack(spacing: 6) {
                    Text([session.startDate.formatted(.dateTime.weekday(.wide).day().month(.twoDigits).locale(Locale(identifier: "de_DE"))), session.name.isEmpty ? nil : session.name]
                        .compactMap { $0 }.joined(separator: " · ").uppercased())
                        .font(.geist(.caption, weight: .bold))
                        .tracking(0.8)
                        .ironLogSecondaryText()
                        .multilineTextAlignment(.center)
                    Text(remaining > 0 ? "Teiltraining gespeichert" : "Geschafft.")
                        .font(.geist(size: remaining > 0 ? 34 : 54, weight: .bold))
                        .tracking(-1.5)
                        .multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                    if remaining > 0 {
                        Text("Vorzeitig beendet · \(ilCount(sets.count, "bestätigter Satz", "bestätigte Sätze"))")
                            .ironLogSecondaryText()
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 20)

                HStack(spacing: 8) {
                    lens(session.durationSeconds < 60 ? "< 1" : "\(session.durationSeconds / 60)", unit: "min", label: "Dauer")
                    lens(String(volumeText.split(separator: " ").dropLast().joined(separator: " ")),
                         unit: String(volumeText.split(separator: " ").last ?? ""), label: "Volumen")
                    lens("\(trained.count)", unit: trained.count == 1 ? "Übung" : "Übungen", label: "Übungen")
                }

                if !records.isEmpty {
                    HStack(spacing: 14) {
                        Image(systemName: "trophy.fill")
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(dark ? Color(red: 11 / 255, green: 13 / 255, blue: 18 / 255) : .white)
                            .frame(width: 48, height: 48)
                            .background(dark ? Color.white : Color(red: 11 / 255, green: 13 / 255, blue: 18 / 255), in: Circle())
                        VStack(alignment: .leading, spacing: 2) {
                            Text(records.count == 1 ? "NEUER REKORD" : "NEUE REKORDE")
                                .font(.geist(.caption, weight: .bold))
                                .tracking(0.8)
                            ForEach(records, id: \.exercise) { record in
                                Text("\(record.exercise): \(record.types.map(ilRecordTypeText).joined(separator: ", "))")
                                    .font(.geist(.body, weight: .heavy))
                            }
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(18)
                    .liquidGlass(.tint, in: RoundedRectangle(cornerRadius: 30, style: .continuous))
                    .accessibilityElement(children: .combine)
                }

                ForEach(trained) { row in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(row.exercise.name).font(.geist(.headline, weight: .heavy))
                        ForEach(row.sets.filter { $0.reps > 0 }) { set in
                            Text("Satz \(set.setNumber) · \(iosSetValueText(weightKg: set.weightKg, reps: set.reps, unitSystem: unitSystem))")
                                .font(.geist(.subheadline))
                                .monospacedDigit()
                                .ironLogSecondaryText()
                        }
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .liquidGlass(in: RoundedRectangle(cornerRadius: 26, style: .continuous))
                }

                if session.planId != nil {
                    Button(planApplied ? "Satzwerte übernommen" : "Planänderungen prüfen") { showPlanChanges = true }
                        .buttonStyle(.bordered).disabled(planApplied)
                }
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 20)
        }
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 10) {
                NavigationLink { IOSWorkoutDetailScreen(sessionID: session.id) } label: {
                    Text("Trainingsdetails öffnen")
                        .font(.geist(.body, weight: .heavy))
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .liquidGlass(in: Capsule())
                }
                .buttonStyle(.plain)
                Button("Fertig", action: onClose)
                    .buttonStyle(IOSWorkoutGlassPrimaryButtonStyle())
            }
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
        }
        .sheet(isPresented: $showPlanChanges) {
            IOSWorkoutPlanChangesSheet(sessionID: session.id, rows: rows, unitSystem: unitSystem,
                onEditor: { showPlanChanges = false; editPlan = true }, onApplied: { planApplied = true })
        }
        .navigationDestination(isPresented: $editPlan) {
            IOSPlanEditorScreen(planID: session.planId, unitSystem: unitSystem)
        }
    }

    private func lens(_ value: String, unit: String, label: String) -> some View {
        VStack(spacing: 8) {
            // A clear square sets the lens size to the full column width.
            Color.clear
                .aspectRatio(1, contentMode: .fit)
                .overlay {
                    VStack(spacing: 0) {
                        Text(value)
                            .font(.geist(size: value.count > 5 ? 22 : 28, weight: .bold))
                            .monospacedDigit()
                            .lineLimit(1)
                            .minimumScaleFactor(0.6)
                        Text(unit)
                            .font(.geist(.caption, weight: .bold))
                            .ironLogSecondaryText()
                            .lineLimit(1)
                    }
                    .padding(8)
                }
                .liquidGlass(in: Circle())
            Text(label.uppercased())
                .font(.geist(.caption, weight: .bold))
                .tracking(0.8)
                .ironLogSecondaryText()
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): \(value) \(unit)")
    }

    private var emberBody: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Label(remaining > 0 ? "Vorzeitig beendet · \(sets.count) bestätigte Sätze" : "Training gespeichert", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(.green).padding().frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.green.opacity(0.1), in: RoundedRectangle(cornerRadius: 14))
                Text(remaining > 0 ? "Teiltraining gespeichert" : "Training geschafft").font(.geist(.title, weight: .bold))
                Text(session.name).ironLogSecondaryText()
                HStack(spacing: 12) {
                    metric("Dauer", "\(session.durationSeconds / 60) min")
                    metric("Sätze", "\(sets.count)")
                }
                metric("Volumen", iosWorkoutDisplayWeight(kilograms: sets.reduce(0) { $0 + $1.weightKg * Double($1.reps) }, unitSystem: unitSystem))
                ForEach(rows.filter { !$0.sets.isEmpty }) { row in
                    VStack(alignment: .leading, spacing: 10) {
                        Text(row.exercise.name).font(.geist(.headline))
                        ForEach(row.sets.filter { $0.reps > 0 }) { set in
                            Text("Satz \(set.setNumber) · \(iosSetValueText(weightKg: set.weightKg, reps: set.reps, unitSystem: unitSystem))").font(.geist(.subheadline)).monospacedDigit()
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
            }.buttonStyle(.borderedProminent).ironLogButtonText().padding().background(.bar)
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
            Text(title).font(.geist(.caption)).ironLogSecondaryText()
            Text(value).font(.geist(.title2, weight: .bold).monospacedDigit())
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
                    Text("Wähle bewusst").font(.geist(.title, weight: .bold))
                    Text("Das Training ist gespeichert. Was soll für das nächste Training gelten?").ironLogSecondaryText()
                    ForEach(rows.filter { $0.target != nil }) { row in
                        let target = row.target!
                        let slots = target.setTargets.isEmpty ? Array(repeating: ILPlannedSet(reps: target.target.reps, weightKg: target.target.weightKg), count: min(100, max(0, target.target.sets))) : target.setTargets
                        ForEach(Array(row.matchedSlots.enumerated()), id: \.offset) { index, match in
                            if let match, slots.indices.contains(index), row.sets.indices.contains(match) {
                                let set = row.sets[match]
                                if set.reps != slots[index].reps || set.weightKg != slots[index].weightKg {
                                    VStack(alignment: .leading, spacing: 6) {
                                        Text("\(row.exercise.name) · Satz \(index + 1)").font(.geist(.headline))
                                        Text("Plan   \(iosWorkoutDisplayWeight(kilograms: slots[index].weightKg, unitSystem: unitSystem)) × \(slots[index].reps)")
                                        Text("Heute  \(iosWorkoutDisplayWeight(kilograms: set.weightKg, unitSystem: unitSystem)) × \(set.reps)").ironLogAccentText()
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
                                    Text(titles[index]).font(.geist(.headline)).foregroundStyle(.primary)
                                    Text(subtitles[index]).font(.geist(.caption)).ironLogSecondaryText()
                                }
                                Spacer(minLength: 0)
                            }.padding().frame(maxWidth: .infinity, alignment: .leading)
                                .background(choice == index ? Color.accentColor.opacity(0.12) : Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 14))
                                .overlay { RoundedRectangle(cornerRadius: 14).stroke(choice == index ? Color.accentColor : .secondary.opacity(0.3)) }
                        }.buttonStyle(.plain).disabled(busy).accessibilityValue(choice == index ? "Ausgewählt" : "Nicht ausgewählt")
                    }
                    Text("Offene Übungen und Sätze bleiben im Plan.").font(.geist(.caption))
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
                }.buttonStyle(.borderedProminent).ironLogButtonText().disabled(busy).padding().background(.bar)
            }
            .interactiveDismissDisabled(busy)
        }
    }
}
