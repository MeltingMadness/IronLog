import Foundation

/// Name of an exercise as the UI shows it.
///
/// Built-in exercises are stored under their German canonical name from
/// `DefaultExerciseCatalog`. That name travels in app data and backups and must
/// not change, so the UI translates it at display time through the
/// `Exercises.xcstrings` table. Custom exercises, and built-in ones the user
/// renamed, show the stored name unchanged.
func ilExerciseDisplayName(name: String, isCustom: Bool, bundle: Bundle = .main) -> String {
    guard !isCustom, !name.isEmpty else { return name }
    return bundle.localizedString(forKey: name, value: name, table: "Exercises")
}

/// Display name for an exercise that a projection of the shared core refers to
/// by ID and stored name only. The exercise row decides whether it is built in;
/// without one the stored name is shown as it is.
func ilExerciseDisplayName(id: Int64, storedName: String?, in data: ILTrainingData?) -> String? {
    data?.exercises.first(where: { $0.id == id })?.displayName ?? storedName
}

extension ILExercise {
    var displayName: String { ilExerciseDisplayName(name: name, isCustom: isCustom) }

    /// Search matches the displayed and the stored name, so a German name still
    /// finds a built-in exercise when the app runs in English.
    func nameMatches(_ query: String) -> Bool {
        displayName.localizedCaseInsensitiveContains(query) || name.localizedCaseInsensitiveContains(query)
    }
}

extension Sequence where Element == ILExercise {
    /// Sorted the way the list reads: by displayed name, numbers in natural order.
    func sortedByDisplayName() -> [ILExercise] {
        map { ($0, $0.displayName) }
            .sorted { $0.1.localizedStandardCompare($1.1) == .orderedAscending }
            .map(\.0)
    }
}
