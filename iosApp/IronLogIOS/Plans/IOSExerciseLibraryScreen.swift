import SwiftUI

struct IOSExerciseLibraryScreen: View {
    enum Filter: String, CaseIterable, Identifiable {
        case active
        case archived
        case all

        var id: String { rawValue }
        var title: String {
            switch self {
            case .active: "Aktiv"
            case .archived: "Archiv"
            case .all: "Alle"
            }
        }
    }

    @Environment(IOSTrainingStore.self) private var store
    @State private var filter: Filter = .active
    @State private var muscleGroupFilter: String?
    @State private var searchText = ""
    @State private var editor: IOSExerciseEditorPresentation?
    @State private var pendingDelete: ILExercise?
    @State private var isWorking = false

    private var exercises: [ILExercise] {
        let all = store.data?.exercises ?? []
        let filtered: [ILExercise]
        switch filter {
        case .active: filtered = all.filter { !$0.isArchived }
        case .archived: filtered = all.filter(\.isArchived)
        case .all: filtered = all
        }
        let muscleFiltered = muscleGroupFilter.map { muscle in
            filtered.filter { $0.primaryMuscleGroup == muscle }
        } ?? filtered
        guard !searchText.isEmpty else {
            return muscleFiltered.sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
        }
        return muscleFiltered
            .filter {
                $0.name.localizedCaseInsensitiveContains(searchText) ||
                    $0.primaryMuscleGroupDisplayName.localizedCaseInsensitiveContains(searchText) ||
                    $0.categoryDisplayName.localizedCaseInsensitiveContains(searchText)
            }
            .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    var body: some View {
        NavigationStack {
            List {
                Picker("Filter", selection: $filter) {
                    ForEach(Filter.allCases) { filter in
                        Text(filter.title).tag(filter)
                    }
                }
                .pickerStyle(.segmented)
                .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 8, trailing: 16))

                Menu {
                    Button("Alle Muskelgruppen") { muscleGroupFilter = nil }
                    Divider()
                    ForEach(IOSExerciseLibraryMuscles.all, id: \.self) { muscle in
                        Button {
                            muscleGroupFilter = muscle
                        } label: {
                            if muscleGroupFilter == muscle {
                                Label(IOSExerciseLibraryMuscles.displayName(muscle), systemImage: "checkmark")
                            } else {
                                Text(IOSExerciseLibraryMuscles.displayName(muscle))
                            }
                        }
                    }
                } label: {
                    Label(
                        muscleGroupFilter.map(IOSExerciseLibraryMuscles.displayName) ?? "Alle Muskelgruppen",
                        systemImage: "line.3.horizontal.decrease.circle"
                    )
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 16, bottom: 8, trailing: 16))

                if exercises.isEmpty {
                    ContentUnavailableView(
                        searchText.isEmpty ? "Keine Übungen" : "Keine Treffer",
                        systemImage: searchText.isEmpty ? "figure.strengthtraining.traditional" : "magnifyingglass",
                        description: Text(searchText.isEmpty ? "Erstelle eine eigene Übung oder importiere Trainingsdaten." : "Passe Suche oder Filter an.")
                    )
                    .listRowBackground(Color.clear)
                } else {
                    Section("Übungen") {
                        ForEach(exercises) { exercise in
                            IOSExerciseListRow(
                                exercise: exercise,
                                onOpen: { editor = IOSExerciseEditorPresentation(exercise: exercise) },
                                onArchive: { Task { await toggleArchive(exercise) } },
                                onDelete: { pendingDelete = exercise }
                            )
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("Übungen")
            .searchable(text: $searchText, prompt: "Übungen, Muskelgruppe oder Kategorie")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        editor = IOSExerciseEditorPresentation(exercise: nil)
                    } label: {
                        Label("Neue Übung", systemImage: "plus")
                    }
                }
            }
            .overlay {
                if isWorking {
                    ProgressView()
                        .padding(22)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
            }
        }
        .sheet(item: $editor) { destination in
            NavigationStack {
                IOSExerciseEditorScreen(exercise: destination.exercise)
            }
        }
        .confirmationDialog(
            pendingDelete.map { "\($0.name) löschen?" } ?? "Übung löschen?",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Löschen", role: .destructive) {
                guard let exercise = pendingDelete else { return }
                pendingDelete = nil
                Task { await delete(exercise) }
            }
            Button("Abbrechen", role: .cancel) { pendingDelete = nil }
        } message: {
            Text("Eine Übung kann nur gelöscht werden, wenn sie nicht mehr von Plänen oder Workouts referenziert wird. Archivieren ist jederzeit möglich.")
        }
    }

    private func toggleArchive(_ exercise: ILExercise) async {
        guard !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        let updated = ILExercise(
            id: exercise.id,
            name: exercise.name,
            primaryMuscleGroup: exercise.primaryMuscleGroup,
            secondaryMuscleGroups: exercise.secondaryMuscleGroups,
            category: exercise.category,
            isCustom: exercise.isCustom,
            notes: exercise.notes,
            isArchived: !exercise.isArchived
        )
        _ = await store.saveExercise(updated)
    }

    private func delete(_ exercise: ILExercise) async {
        guard exercise.isCustom, !isWorking else { return }
        isWorking = true
        defer { isWorking = false }
        _ = await store.command("exercise.delete", fields: ["id": exercise.id])
    }
}

private enum IOSExerciseLibraryMuscles {
    static let all = ["BRUST", "RUECKEN", "BEINE", "SCHULTERN", "BIZEPS", "TRIZEPS", "GESAESS", "CORE", "UNTERARME", "WADEN"]

    static func displayName(_ raw: String) -> String {
        switch raw {
        case "BRUST": "Brust"
        case "RUECKEN": "Rücken"
        case "BEINE": "Beine"
        case "SCHULTERN": "Schultern"
        case "BIZEPS": "Bizeps"
        case "TRIZEPS": "Trizeps"
        case "GESAESS": "Gesäß"
        case "CORE": "Core"
        case "UNTERARME": "Unterarme"
        case "WADEN": "Waden"
        default: raw
        }
    }
}

private struct IOSExerciseListRow: View {
    let exercise: ILExercise
    let onOpen: () -> Void
    let onArchive: () -> Void
    let onDelete: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 12) {
                Image(systemName: exercise.isCustom ? "person.crop.circle.badge.plus" : "figure.strengthtraining.traditional")
                    .font(.title3)
                    .foregroundStyle(exercise.isArchived ? Color.secondary : Color.accentColor)
                    .frame(width: 30)
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 7) {
                        Text(exercise.name)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.primary)
                        if exercise.isArchived {
                            Text("Archiv")
                                .font(.caption2.weight(.semibold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(.secondary.opacity(0.16), in: Capsule())
                        }
                    }
                    Text("\(exercise.primaryMuscleGroupDisplayName) · \(exercise.categoryDisplayName)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer(minLength: 6)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
        .contextMenu {
            Button("Bearbeiten", systemImage: "pencil", action: onOpen)
            Button(exercise.isArchived ? "Wieder aktivieren" : "Archivieren", systemImage: exercise.isArchived ? "archivebox.fill" : "archivebox", action: onArchive)
            if exercise.isCustom {
                Button("Löschen", systemImage: "trash", role: .destructive, action: onDelete)
            }
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(action: onArchive) {
                Label(exercise.isArchived ? "Aktivieren" : "Archivieren", systemImage: exercise.isArchived ? "archivebox.fill" : "archivebox")
            }
            if exercise.isCustom {
                Button(role: .destructive, action: onDelete) {
                    Label("Löschen", systemImage: "trash")
                }
            }
        }
    }
}

private struct IOSExerciseEditorPresentation: Identifiable {
    let id: String
    let exercise: ILExercise?

    init(exercise: ILExercise?) {
        self.exercise = exercise
        id = exercise.map { "exercise-\($0.id)" } ?? "exercise-new"
    }
}

private struct IOSExerciseEditorScreen: View {
    @Environment(IOSTrainingStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    let exercise: ILExercise?
    @State private var name = ""
    @State private var primaryMuscle = "BRUST"
    @State private var selectedSecondaryMuscles: Set<String> = []
    @State private var category = "LANGHANTEL"
    @State private var notes = ""
    @State private var isArchived = false
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var hasLoaded = false
    @State private var showDeleteConfirmation = false

    private let muscleGroups = IOSExerciseLibraryMuscles.all
    private let categories = ["LANGHANTEL", "KURZHANTEL", "MASCHINE", "KABEL", "EIGENGEWICHT"]

    var body: some View {
        Form {
            Section("Übung") {
                TextField("Name", text: $name)
                    .textInputAutocapitalization(.words)
                Picker("Kategorie", selection: $category) {
                    ForEach(categories, id: \.self) { category in
                        Text(categoryDisplayName(category)).tag(category)
                    }
                }
                Picker("Primäre Muskelgruppe", selection: $primaryMuscle) {
                    ForEach(muscleGroups, id: \.self) { muscle in
                        Text(muscleDisplayName(muscle)).tag(muscle)
                    }
                }
            }

            Section("Sekundäre Muskelgruppen") {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 118), alignment: .leading)],
                    alignment: .leading,
                    spacing: 8
                ) {
                    ForEach(muscleGroups.filter { $0 != primaryMuscle }, id: \.self) { muscle in
                        let isSelected = selectedSecondaryMuscles.contains(muscle)
                        Button {
                            toggleSecondaryMuscle(muscle)
                        } label: {
                            Label(
                                muscleDisplayName(muscle),
                                systemImage: isSelected ? "checkmark.circle.fill" : "circle"
                            )
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .buttonStyle(.bordered)
                        .tint(isSelected ? Color.accentColor : Color.secondary)
                        .disabled(!isSelected && selectedSecondaryMuscles.count >= 3)
                    }
                }
                Text("Wähle bis zu 3 weitere Muskelgruppen. Die primäre Muskelgruppe ist hier ausgeschlossen.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }

            Section("Notizen") {
                TextEditor(text: $notes)
                    .frame(minHeight: 90)
            }

            Section {
                Toggle("Archiviert", isOn: $isArchived)
            } footer: {
                Text("Archivierte Übungen bleiben in der Historie erhalten und werden bei der Plan-Auswahl ausgeblendet.")
            }

            if let errorMessage {
                Section {
                    Label(errorMessage, systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }
        }
        .navigationTitle(exercise == nil ? "Neue Übung" : "Übung bearbeiten")
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: primaryMuscle) { _, newPrimary in
            selectedSecondaryMuscles.remove(newPrimary)
        }
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
            if let exercise, exercise.isCustom {
                ToolbarItem(placement: .bottomBar) {
                    Button("Löschen", systemImage: "trash", role: .destructive) {
                        showDeleteConfirmation = true
                    }
                }
            }
        }
        .overlay {
            if isSaving {
                ProgressView()
                    .padding(22)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
        .task { loadIfNeeded() }
        .confirmationDialog("Übung löschen?", isPresented: $showDeleteConfirmation, titleVisibility: .visible) {
            Button("Löschen", role: .destructive) {
                Task { await delete() }
            }
            Button("Abbrechen", role: .cancel) {}
        } message: {
            Text("Die Übung wird nur gelöscht, wenn keine gespeicherten Daten darauf verweisen.")
        }
    }

    private func loadIfNeeded() {
        guard !hasLoaded else { return }
        hasLoaded = true
        guard let exercise else { return }
        name = exercise.name
        primaryMuscle = muscleGroups.contains(exercise.primaryMuscleGroup) ? exercise.primaryMuscleGroup : "BRUST"
        selectedSecondaryMuscles = Set(
            exercise.secondaryMuscleGroups
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
                .filter { muscleGroups.contains($0) && $0 != primaryMuscle }
        )
        category = exercise.category
        notes = exercise.notes
        isArchived = exercise.isArchived
    }

    private func save() async {
        guard !isSaving else { return }
        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedName.isEmpty else {
            errorMessage = "Bitte gib einen Namen ein."
            return
        }
        let secondary = muscleGroups
            .filter { selectedSecondaryMuscles.contains($0) && $0 != primaryMuscle }
            .prefix(3)
            .joined(separator: ",")
        let saved = ILExercise(
            id: exercise?.id ?? 0,
            name: trimmedName,
            primaryMuscleGroup: primaryMuscle,
            secondaryMuscleGroups: secondary,
            category: category,
            isCustom: exercise?.isCustom ?? true,
            notes: notes,
            isArchived: isArchived
        )
        isSaving = true
        defer { isSaving = false }
        if await store.saveExercise(saved) {
            dismiss()
        } else {
            errorMessage = store.errorMessage ?? "Die Übung konnte nicht gespeichert werden."
        }
    }

    private func delete() async {
        guard let exercise, exercise.isCustom, !isSaving else { return }
        isSaving = true
        defer { isSaving = false }
        if await store.command("exercise.delete", fields: ["id": exercise.id]) {
            dismiss()
        } else {
            errorMessage = store.errorMessage ?? "Die Übung konnte nicht gelöscht werden."
        }
    }

    private func muscleDisplayName(_ raw: String) -> String {
        IOSExerciseLibraryMuscles.displayName(raw)
    }

    private func toggleSecondaryMuscle(_ muscle: String) {
        guard muscle != primaryMuscle else { return }
        if selectedSecondaryMuscles.contains(muscle) {
            selectedSecondaryMuscles.remove(muscle)
        } else if selectedSecondaryMuscles.count < 3 {
            selectedSecondaryMuscles.insert(muscle)
        }
    }

    private func categoryDisplayName(_ raw: String) -> String {
        switch raw {
        case "LANGHANTEL": "Langhantel"
        case "KURZHANTEL": "Kurzhantel"
        case "MASCHINE": "Maschine"
        case "KABEL": "Kabel"
        case "EIGENGEWICHT": "Eigengewicht"
        default: raw
        }
    }
}
