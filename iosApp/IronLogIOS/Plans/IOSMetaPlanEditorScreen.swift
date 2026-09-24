import SwiftUI

struct IOSMetaPlanEditorScreen: View {
    @Environment(IOSTrainingStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    let metaPlanID: Int64?
    @State private var name = ""
    @State private var items: [IOSMetaPlanItemDraft] = []
    @State private var hasLoaded = false
    @State private var errorMessage: String?
    @State private var planPicker: IOSPlanPickerPresentation?
    @State private var isSaving = false
    @State private var isDeleting = false
    @State private var isStarting = false
    @State private var showDeleteConfirmation = false

    private var data: ILTrainingData? { store.data }
    private var existingPlan: ILMetaTrainingPlan? { data?.metaTrainingPlans.first(where: { $0.id == metaPlanID }) }
    private var availablePlans: [ILTrainingPlan] {
        (data?.trainingPlans ?? []).sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    var body: some View {
        Form {
            Section("Meta-Plan") {
                TextField("Name", text: $name)
                    .textInputAutocapitalization(.words)
                if let plan = existingPlan {
                    LabeledContent("Erstellt", value: plan.createdDate.formatted(date: .abbreviated, time: .omitted))
                }
            }

            Section {
                if items.isEmpty {
                    ContentUnavailableView("Keine Teilpläne", systemImage: "rectangle.stack.badge.plus", description: Text("Füge mindestens einen Trainingsplan hinzu. Die Reihenfolge bestimmt die Rotation."))
                        .listRowBackground(Color.clear)
                } else {
                    ForEach($items) { item in
                        IOSMetaPlanItemRow(
                            item: item,
                            index: items.firstIndex(where: { $0.id == item.wrappedValue.id }) ?? 0,
                            planName: planName(for: item.wrappedValue.trainingPlanID),
                            isFirst: item.wrappedValue.id == items.first?.id,
                            isLast: item.wrappedValue.id == items.last?.id,
                            onMoveUp: { move(item.wrappedValue, by: -1) },
                            onMoveDown: { move(item.wrappedValue, by: 1) },
                            onRemove: { remove(item.wrappedValue) }
                        )
                    }
                    .onMove(perform: moveItems)
                }

                Button {
                    planPicker = IOSPlanPickerPresentation()
                } label: {
                    Label("Trainingsplan hinzufügen", systemImage: "plus.circle.fill")
                }
                .disabled(availablePlans.isEmpty)
            } header: {
                HStack {
                    Text("Rotation")
                    Spacer()
                    Text("\(items.count)")
                        .foregroundStyle(.secondary)
                }
            } footer: {
                Text("Teilpläne dürfen wiederholt werden. Beim Start wird der nächste gültige Eintrag aus der Rotation gewählt.")
            }

            if let errorMessage {
                Section {
                    Label(errorMessage, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle(metaPlanID == nil ? "Neuer Meta-Plan" : "Meta-Plan bearbeiten")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Abbrechen") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button(isSaving ? "Speichert …" : "Sichern") {
                    Task { await save() }
                }
                .disabled(isSaving || store.isBusy || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            if let existingPlan {
                ToolbarItem(placement: .bottomBar) {
                    Button("Rotation starten", systemImage: "play.fill") {
                        Task { await start(plan: existingPlan) }
                    }
                    .disabled(isStarting || store.isBusy)
                }
                ToolbarItem(placement: .bottomBar) {
                    Button("Meta-Plan löschen", systemImage: "trash", role: .destructive) {
                        showDeleteConfirmation = true
                    }
                    .disabled(isDeleting || store.isBusy)
                }
            }
        }
        .overlay {
            if isSaving || isDeleting || isStarting {
                ProgressView()
                    .padding(22)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
        .task { loadIfNeeded() }
        .sheet(item: $planPicker) { _ in
            NavigationStack {
                IOSPlanPickerSheet(plans: availablePlans) { plan in
                    items.append(IOSMetaPlanItemDraft(trainingPlanID: plan.id))
                }
            }
        }
        .confirmationDialog("Meta-Plan löschen?", isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
            Button("Löschen", role: .destructive) {
                Task { await delete() }
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Die Rotation wird entfernt. Die enthaltenen Trainingspläne bleiben erhalten.")
        }
    }

    private func loadIfNeeded() {
        guard !hasLoaded else { return }
        hasLoaded = true
        name = existingPlan?.name ?? ""
        items = (data?.metaPlanItems ?? [])
            .filter { $0.metaPlanId == (metaPlanID ?? 0) }
            .sorted { $0.orderIndex == $1.orderIndex ? $0.id < $1.id : $0.orderIndex < $1.orderIndex }
            .map(IOSMetaPlanItemDraft.init(record:))
    }

    private func planName(for id: Int64) -> String {
        data?.trainingPlans.first(where: { $0.id == id })?.name ?? "Plan nicht gefunden (ID \(id))"
    }

    private func remove(_ item: IOSMetaPlanItemDraft) {
        items.removeAll { $0.id == item.id }
    }

    private func move(_ item: IOSMetaPlanItemDraft, by offset: Int) {
        guard let index = items.firstIndex(where: { $0.id == item.id }) else { return }
        let target = index + offset
        guard items.indices.contains(target) else { return }
        items.swapAt(index, target)
    }

    private func moveItems(from offsets: IndexSet, to destination: Int) {
        items.move(fromOffsets: offsets, toOffset: destination)
    }

    private func save() async {
        guard !isSaving else { return }
        errorMessage = nil
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            errorMessage = "Bitte gib einen Namen ein."
            return
        }
        guard !items.isEmpty else {
            errorMessage = "Füge mindestens einen Trainingsplan hinzu."
            return
        }
        guard items.allSatisfy({ item in availablePlans.contains(where: { plan in plan.id == item.trainingPlanID }) }) else {
            errorMessage = "Ein Teilplan ist nicht mehr verfügbar. Entferne ihn und wähle einen gültigen Plan."
            return
        }

        let savedItems = items.enumerated().map { index, item in
            ILMetaPlanItem(
                id: item.persistedID,
                metaPlanId: metaPlanID ?? 0,
                trainingPlanId: item.trainingPlanID,
                orderIndex: index
            )
        }
        let plan = ILMetaTrainingPlan(id: metaPlanID ?? 0, name: trimmedName, createdAt: existingPlan?.createdAt ?? 0)
        isSaving = true
        defer { isSaving = false }
        if await store.saveMetaPlan(plan, items: savedItems) {
            dismiss()
        } else {
            errorMessage = store.errorMessage ?? "Der Meta-Plan konnte nicht gespeichert werden."
        }
    }

    private func start(plan: ILMetaTrainingPlan) async {
        guard !isStarting else { return }
        isStarting = true
        defer { isStarting = false }
        _ = await store.startWorkout(name: plan.name, metaPlanId: plan.id)
    }

    private func delete() async {
        guard let metaPlanID, !isDeleting else { return }
        isDeleting = true
        defer { isDeleting = false }
        if await store.command("metaplan.delete", fields: ["id": metaPlanID]) {
            dismiss()
        } else {
            errorMessage = store.errorMessage ?? "Der Meta-Plan konnte nicht gelöscht werden."
        }
    }
}

private struct IOSMetaPlanItemRow: View {
    @Binding var item: IOSMetaPlanItemDraft
    let index: Int
    let planName: String
    let isFirst: Bool
    let isLast: Bool
    let onMoveUp: () -> Void
    let onMoveDown: () -> Void
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Text("\(index + 1)")
                .font(.headline.monospacedDigit())
                .foregroundStyle(.secondary)
                .frame(width: 28)
            VStack(alignment: .leading, spacing: 3) {
                Text(planName)
                    .font(.body.weight(.semibold))
                Text("Teilplan")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Menu {
                Button("Nach oben", systemImage: "arrow.up", action: onMoveUp)
                    .disabled(isFirst)
                Button("Nach unten", systemImage: "arrow.down", action: onMoveDown)
                    .disabled(isLast)
                Divider()
                Button("Entfernen", systemImage: "trash", role: .destructive, action: onRemove)
            } label: {
                Image(systemName: "ellipsis.circle")
                    .font(.title3)
            }
        }
        .padding(.vertical, 4)
    }
}

private struct IOSPlanPickerPresentation: Identifiable {
    let id = UUID()
}

private struct IOSPlanPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let plans: [ILTrainingPlan]
    let onSelect: (ILTrainingPlan) -> Void
    @State private var searchText = ""

    private var filtered: [ILTrainingPlan] {
        guard !searchText.isEmpty else { return plans }
        return plans.filter { $0.name.localizedCaseInsensitiveContains(searchText) }
    }

    var body: some View {
        List(filtered) { plan in
            Button {
                onSelect(plan)
                dismiss()
            } label: {
                Label(plan.name, systemImage: "list.bullet.rectangle")
                    .foregroundStyle(.primary)
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Plan wählen")
        .navigationBarTitleDisplayMode(.inline)
        .searchable(text: $searchText, prompt: "Pläne suchen")
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Schließen") { dismiss() }
            }
        }
    }
}
