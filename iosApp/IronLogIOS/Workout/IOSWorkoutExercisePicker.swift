import SwiftUI

struct IOSWorkoutExercisePicker: View {
    @Environment(\.dismiss) private var dismiss
    let exercises: [ILExercise]
    let excludedIDs: Set<Int64>
    let onSelect: (ILExercise) -> Void
    @State private var searchText = ""
    @State private var group: String?
    @State private var selected: [Int64] = []
    private var filtered: [ILExercise] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return exercises.filter {
            (group == nil || $0.primaryMuscleGroupDisplayName == group) &&
            (query.isEmpty || $0.name.localizedCaseInsensitiveContains(query) || $0.categoryDisplayName.localizedCaseInsensitiveContains(query))
        }
    }
    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        filter("Alle", value: nil)
                        ForEach(Array(Set(exercises.map(\.primaryMuscleGroupDisplayName))).sorted(), id: \.self) { name in filter(name, value: name) }
                    }.padding(.horizontal)
                }
                HStack {
                    Text("\(selected.count) ausgewählt").foregroundStyle(.tint).bold()
                    Spacer()
                    Button("Auswahl leeren") { selected = [] }
                }.font(.subheadline).padding(.horizontal)
                List(filtered) { exercise in
                    Button {
                        if selected.contains(exercise.id) { selected.removeAll { $0 == exercise.id } }
                        else { selected.append(exercise.id) }
                    } label: {
                        HStack(spacing: 12) {
                            VStack(alignment: .leading, spacing: 5) {
                                Text(exercise.name).font(.headline).foregroundStyle(.primary)
                                Text("\(exercise.primaryMuscleGroupDisplayName) · \(exercise.categoryDisplayName)").font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: selected.contains(exercise.id) || excludedIDs.contains(exercise.id) ? "checkmark.square.fill" : "square")
                                .font(.title2).foregroundStyle(.tint)
                        }.frame(minHeight: 48).contentShape(Rectangle())
                    }
                    .disabled(excludedIDs.contains(exercise.id))
                    .listRowBackground(selected.contains(exercise.id) ? Color.accentColor.opacity(0.12) : Color(uiColor: .secondarySystemGroupedBackground))
                    .accessibilityValue(selected.contains(exercise.id) ? "Ausgewählt" : excludedIDs.contains(exercise.id) ? "Bereits hinzugefügt" : "Nicht ausgewählt")
                }.listStyle(.insetGrouped)
            }
            .searchable(text: $searchText, prompt: "Übung suchen")
            .navigationTitle("Übungen wählen")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { dismiss() } } }
            .safeAreaInset(edge: .bottom) {
                Button {
                    selected.compactMap { id in exercises.first { $0.id == id } }.forEach(onSelect)
                    dismiss()
                } label: { Text("\(ilCount(selected.count, "Übung", "Übungen")) hinzufügen").frame(maxWidth: .infinity).padding(.vertical, 6) }
                .buttonStyle(.borderedProminent).disabled(selected.isEmpty).padding().background(.bar)
            }
        }
    }
    private func filter(_ name: String, value: String?) -> some View {
        Button(name) { group = value }.buttonStyle(.bordered).tint(group == value ? .accentColor : .secondary)
    }
}
