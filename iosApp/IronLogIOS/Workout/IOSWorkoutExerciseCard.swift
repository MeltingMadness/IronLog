import SwiftUI

struct IOSWorkoutExerciseCard: View {
    let row: IOSWorkoutExerciseRow
    let unitSystem: String
    let intensitySystem: String
    let supersetTint: Color?
    let onAddSet: () -> Void
    let onApplyCoachWeight: (Double) -> Void
    let onEditSet: (ILWorkoutSet) -> Void
    let onDeleteSet: (ILWorkoutSet) -> Void
    let sessionID: Int64
    let onSaveSet: (ILWorkoutSet, ILSetIntention) async -> Bool

    @Environment(\.ironLogTheme) private var theme
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let palette = theme.palette(for: colorScheme)
        let effectiveIntensitySystem = iosWorkoutEffectiveIntensitySystem(
            configuredSystem: intensitySystem,
            planTarget: row.target
        )

        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 10) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(row.exercise.name)
                            .font(.headline.weight(.semibold))
                        if row.target != nil {
                            Text("Plan")
                                .font(.caption2.weight(.bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(palette.primary.opacity(0.12), in: Capsule())
                                .foregroundStyle(palette.primary)
                        } else {
                            Text("Ad-hoc")
                                .font(.caption2.weight(.bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(palette.secondary.opacity(0.12), in: Capsule())
                                .foregroundStyle(palette.secondary)
                        }
                    }
                    Text("\(row.exercise.primaryMuscleGroupDisplayName) · \(row.exercise.categoryDisplayName)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 8)
                Menu {
                    Button("Satz hinzufügen", systemImage: "plus") { onAddSet() }
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.title3)
                }
                .accessibilityLabel("Aktionen für \(row.exercise.name)")
            }

            if row.target != nil, let displayTarget = row.displayTarget {
                HStack(spacing: 12) {
                    Label("Ziel \(displayTarget.sets) × \(displayTarget.reps)", systemImage: "target")
                    Text("@ \(iosWorkoutDisplayWeight(kilograms: displayTarget.weightKg, unitSystem: unitSystem))")
                    Spacer()
                    Text("\(row.completedNormalSets)/\(displayTarget.sets)")
                        .font(.subheadline.monospacedDigit().weight(.semibold))
                        .foregroundStyle(row.isComplete ? palette.success : palette.textSecondary)
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                .accessibilityElement(children: .combine)
                .accessibilityLabel("Ziel und Fortschritt")
                .accessibilityValue("\(row.completedNormalSets) von \(displayTarget.sets) Arbeitssätzen")
            }

            if let previousWeight = row.previousWorkWeightKg {
                Label("Letztes passendes Workout: \(iosWorkoutDisplayWeight(kilograms: previousWeight, unitSystem: unitSystem))", systemImage: "clock.arrow.circlepath")
                    .font(.caption)
                    .foregroundStyle(palette.information)
                    .accessibilityLabel("Letztes passendes Gewicht \(iosWorkoutDisplayWeight(kilograms: previousWeight, unitSystem: unitSystem))")
            }

            if let recommendation = row.nextSetRecommendation {
                IOSWorkoutNextSetRecommendationView(
                    recommendation: recommendation,
                    unitSystem: unitSystem,
                    onApplyWeight: onApplyCoachWeight,
                    onApplyBackoff: onApplyCoachWeight
                )
            }

            if row.sets.isEmpty {
                Text("Noch kein Satz geloggt.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 4)
            } else {
                VStack(spacing: 0) {
                    ForEach(row.sets) { set in
                        IOSWorkoutSetRow(
                            set: set,
                            unitSystem: unitSystem,
                            intensitySystem: effectiveIntensitySystem,
                            onEdit: { onEditSet(set) },
                            onDelete: { onDeleteSet(set) }
                        )
                        if set.id != row.sets.last?.id {
                            Divider()
                        }
                    }
                }
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                .overlay {
                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                        .stroke(palette.separator, lineWidth: 1)
                }
            }

            if row.target == nil || !row.isComplete {
                IOSWorkoutInlineSetEntry(row: row, sessionID: sessionID, unitSystem: unitSystem, onSave: onSaveSet)
                    .id("\(row.id)-\(row.nextSetNumber)-\(unitSystem)")
            } else {
                Label("Alle geplanten Sätze bestätigt", systemImage: "checkmark.circle.fill")
                    .foregroundStyle(palette.success).font(.subheadline)
            }
            Button("Zusätzlichen Satz hinzufügen", action: onAddSet).font(.subheadline)

        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(palette.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(
                    supersetTint?.opacity(0.72) ?? palette.separator,
                    lineWidth: supersetTint == nil ? 1 : 1.5
                )
        }
        .overlay(alignment: .leading) {
            if let supersetTint {
                RoundedRectangle(cornerRadius: 2, style: .continuous)
                    .fill(supersetTint)
                    .frame(width: 4)
                    .padding(.vertical, 5)
                    .padding(.leading, 1)
            }
        }
    }
}

private struct IOSWorkoutSetRow: View {
    let set: ILWorkoutSet
    let unitSystem: String
    let intensitySystem: String
    let onEdit: () -> Void
    let onDelete: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            Text("\(set.setNumber)")
                .font(.subheadline.monospacedDigit().weight(.semibold))
                .frame(width: 24, alignment: .leading)
                .foregroundStyle(.secondary)

            Text(resolvedIOSWorkoutSetType(set).shortName)
                .font(.caption.weight(.semibold))
                .foregroundStyle(setTypeColor)
                .frame(width: 58, alignment: .leading)

            VStack(alignment: .leading, spacing: 2) {
                Text(iosSetValueText(weightKg: set.weightKg, reps: set.reps, unitSystem: unitSystem))
                    .font(.body.weight(.medium))
                if let intensity = iosWorkoutFormatIntensity(storedRPE: set.rpe, intensitySystem: intensitySystem) {
                    Text("\(intensitySystem.uppercased()) \(intensity)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer(minLength: 4)

            Menu {
                Button("Bearbeiten", systemImage: "pencil") { onEdit() }
                Button("Löschen", systemImage: "trash", role: .destructive) { onDelete() }
            } label: {
                Image(systemName: "ellipsis")
                    .frame(width: 28, height: 32)
                    .contentShape(Rectangle())
            }
            .accessibilityLabel("Satz \(set.setNumber) bearbeiten oder löschen")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Satz \(set.setNumber), \(resolvedIOSWorkoutSetType(set).displayName)")
        .accessibilityValue("\(set.reps) Wiederholungen, \(iosWorkoutDisplayWeight(kilograms: set.weightKg, unitSystem: unitSystem))")
        .contextMenu {
            Button("Bearbeiten", systemImage: "pencil") { onEdit() }
            Button("Löschen", systemImage: "trash", role: .destructive) { onDelete() }
        }
    }

    private var setTypeColor: Color {
        switch resolvedIOSWorkoutSetType(set) {
        case .normal: return .primary
        case .warmup: return .orange
        case .dropSet: return .purple
        case .failure: return .red
        }
    }
}

private struct IOSWorkoutInlineSetEntry: View {
    @EnvironmentObject private var settings: IOSSettingsViewModel
    let row: IOSWorkoutExerciseRow
    let sessionID: Int64
    let unitSystem: String
    let onSave: (ILWorkoutSet, ILSetIntention) async -> Bool
    @State private var weight: String
    @State private var reps: String
    @State private var intensity = ""
    @State private var intention: ILSetIntention = .unknown
    @State private var kind: IOSWorkoutSetType
    @State private var busy = false
    @State private var error: String?

    init(row: IOSWorkoutExerciseRow, sessionID: Int64, unitSystem: String, onSave: @escaping (ILWorkoutSet, ILSetIntention) async -> Bool) {
        self.row = row; self.sessionID = sessionID; self.unitSystem = unitSystem; self.onSave = onSave
        _weight = State(initialValue: IOSNumber.format(IOSWeight.display(kg: row.defaultWeightKg, unit: unitSystem)))
        _reps = State(initialValue: row.targetReps > 0 ? String(row.targetReps) : "")
        _kind = State(initialValue: row.nextPlannedSet?.kind == "WARMUP" ? .warmup : .normal)
    }
    private var source: String {
        if row.target?.setTargets.isEmpty == false { return "Satzvorgabe aus dem Plan · editierbar" }
        if row.sets.contains(where: { resolvedIOSWorkoutSetType($0) == .normal }) { return "Werte aus dem letzten Satz übernommen · editierbar" }
        if (row.displayTarget?.weightKg ?? 0) <= 0, row.previousWorkWeightKg != nil { return "Gewicht aus dem letzten Training · editierbar" }
        return row.target == nil ? "Werte für diesen Satz" : "Werte aus dem Plan · editierbar"
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Satz \(row.nextSetNumber)").font(.headline)
            Text(source).font(.caption).foregroundStyle(.tint)
            HStack(spacing: 12) {
                field("Gewicht (\(IOSWeight.label(unitSystem)))", text: $weight, keyboard: .decimalPad)
                field("Wiederholungen", text: $reps, keyboard: .numberPad)
            }
            DisclosureGroup("RPE / RIR · Satztyp · Absicht") {
                VStack(alignment: .leading, spacing: 12) {
                    Picker("Satztyp", selection: $kind) {
                        ForEach(IOSWorkoutSetType.allCases) { kind in Text(kind.displayName).tag(kind) }
                    }
                    let scale = iosWorkoutEffectiveIntensitySystem(configuredSystem: settings.state.intensitySystem, planTarget: row.target)
                    if scale != "OFF" { field(scale, text: $intensity, keyboard: .decimalPad) }
                    Picker("Absicht", selection: $intention) {
                        Text("Nicht angegeben").tag(ILSetIntention.unknown)
                        Text("Geplant bis zum Versagen").tag(ILSetIntention.plannedFailure)
                        Text("Ziel unerwartet verfehlt").tag(ILSetIntention.unexpectedTargetMiss)
                    }
                    if settings.state.plateCalculatorEnabled, let value = IOSNumber.parse(weight) {
                        IOSWorkoutPlateVisualizer(targetWeightKg: IOSWeight.kilograms(value: value, unit: unitSystem), barbellWeightKg: settings.state.barbellWeightKg, availablePlates: settings.state.availablePlates, unitSystem: unitSystem)
                    }
                }.padding(.top, 8)
            }.font(.subheadline)
            if let error { Text(error).font(.caption).foregroundStyle(.red) }
            Button { Task { await save() } } label: {
                Label(busy ? "Speichert …" : "Satz \(row.nextSetNumber) bestätigen", systemImage: "checkmark")
                    .frame(maxWidth: .infinity).padding(.vertical, 6)
            }.buttonStyle(.borderedProminent).disabled(busy)
            if row.loggingSlots.count > 0 {
                let open = max(0, (row.remainingPlannedSets ?? 0) - 1)
                if open > 0 { Text("\(open) weitere geplante Sätze offen").font(.caption).foregroundStyle(.secondary) }
            }
        }.padding(14)
        .background(Color.accentColor.opacity(0.08), in: RoundedRectangle(cornerRadius: 14))
        .overlay { RoundedRectangle(cornerRadius: 14).stroke(Color.accentColor.opacity(0.6)) }
    }
    private func field(_ title: String, text: Binding<String>, keyboard: UIKeyboardType) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            TextField(title, text: text).keyboardType(keyboard).textFieldStyle(.roundedBorder)
                .font(.title3.monospacedDigit()).frame(minHeight: 44).accessibilityLabel(title)
        }
    }
    private func save() async {
        guard !busy, let count = IOSNumber.parseInt(reps), count > 0,
              let value = IOSNumber.parse(weight), value >= 0 else { error = "Bitte Gewicht und Wiederholungen prüfen."; return }
        let kg = IOSWeight.kilograms(value: value, unit: unitSystem)
        guard kg.isFinite else { error = "Gewicht ist ungültig."; return }
        let rpe: Double?
        do {
            rpe = try iosWorkoutStoredIntensityForSave(existingRPE: nil, inputText: intensity, configuredSystem: settings.state.intensitySystem, planTarget: row.target)
        } catch { self.error = "Bitte Intensität unter RPE / RIR prüfen."; return }
        busy = true
        defer { busy = false }
        let set = ILWorkoutSet(sessionId: sessionID, exerciseId: row.exercise.id, setNumber: row.nextSetNumber, reps: count, weightKg: kg,
            setType: kind.rawValue, completedAt: Int64(Date().timeIntervalSince1970 * 1000), rpe: rpe, planTargetSnapshotId: row.targetID)
        if !(await onSave(set, intention)) { error = "Satz konnte nicht gespeichert werden. Deine Eingaben bleiben erhalten." }
    }
}
