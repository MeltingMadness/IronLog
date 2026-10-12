import Foundation
import XCTest
@testable import ThrymIOS

/// Built-in exercises keep their German canonical name in data and backups and
/// are translated only for display.
final class ExerciseDisplayNameTests: XCTestCase {
    private func languageBundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
        return try XCTUnwrap(Bundle(path: path))
    }

    private let deadlift = ILExercise(id: 22, name: "Kreuzheben", primaryMuscleGroup: "RUECKEN", category: "LANGHANTEL", isCustom: false)

    func testBuiltInNameIsTranslatedPerLanguage() throws {
        // The wording itself is under test, so the language bundles are pinned.
        XCTAssertEqual(ilExerciseDisplayName(name: "Kreuzheben", isCustom: false, bundle: try languageBundle("en")), "Deadlift")
        XCTAssertEqual(ilExerciseDisplayName(name: "Kreuzheben", isCustom: false, bundle: try languageBundle("de")), "Kreuzheben")
        XCTAssertEqual(
            ilExerciseDisplayName(name: "Bankdrücken (Langhantel)", isCustom: false, bundle: try languageBundle("en")),
            "Bench Press (Barbell)"
        )
        // The app language decides at runtime; the model property follows Bundle.main.
        XCTAssertEqual(
            deadlift.displayName,
            Bundle.main.localizedString(forKey: "Kreuzheben", value: nil, table: "Exercises")
        )
    }

    func testCustomAndRenamedExercisesKeepTheirName() throws {
        let english = try languageBundle("en")
        // A custom exercise may carry a built-in name; it is still the user's text.
        XCTAssertEqual(ilExerciseDisplayName(name: "Kreuzheben", isCustom: true, bundle: english), "Kreuzheben")
        // A built-in exercise the user renamed has no catalog entry.
        XCTAssertEqual(ilExerciseDisplayName(name: "Kreuzheben (Trap Bar)", isCustom: false, bundle: english), "Kreuzheben (Trap Bar)")
        XCTAssertEqual(ilExerciseDisplayName(name: "", isCustom: false, bundle: english), "")

        let custom = ILExercise(id: 100, name: "Kreuzheben", isCustom: true)
        XCTAssertEqual(custom.displayName, "Kreuzheben")
    }

    func testDisplayNameDoesNotChangeTheStoredName() throws {
        let data = ILTrainingData(exercises: [deadlift])
        let encoded = try JSONEncoder().encode(data)
        let decoded = try JSONDecoder().decode(ILTrainingData.self, from: encoded)
        XCTAssertEqual(decoded.exercises.first?.name, "Kreuzheben")
        XCTAssertTrue(String(decoding: encoded, as: UTF8.self).contains(#""name":"Kreuzheben""#))
    }

    func testProjectionNamesResolveThroughTheExerciseRow() {
        let custom = ILExercise(id: 100, name: "Kreuzheben", isCustom: true)
        let data = ILTrainingData(exercises: [deadlift, custom])

        XCTAssertEqual(ilExerciseDisplayName(id: 22, storedName: "Kreuzheben", in: data), deadlift.displayName)
        XCTAssertEqual(ilExerciseDisplayName(id: 100, storedName: "Kreuzheben", in: data), "Kreuzheben")
        // Without a row the projection's stored name is all there is.
        XCTAssertEqual(ilExerciseDisplayName(id: 7, storedName: "Cable Fly", in: data), "Cable Fly")
        XCTAssertNil(ilExerciseDisplayName(id: 7, storedName: nil, in: data))
        XCTAssertEqual(ilExerciseDisplayName(id: 22, storedName: "Kreuzheben", in: nil), "Kreuzheben")
    }

    func testSearchMatchesDisplayedAndStoredName() {
        XCTAssertTrue(deadlift.nameMatches("kreuz"))
        XCTAssertTrue(deadlift.nameMatches(String(deadlift.displayName.prefix(4)).lowercased()))
        XCTAssertFalse(deadlift.nameMatches("Bizeps"))
    }

    func testSortingFollowsTheDisplayedName() {
        let exercises = [
            ILExercise(id: 15, name: "Klimmzug", isCustom: false),
            deadlift,
            ILExercise(id: 30, name: "Beinpresse", isCustom: false),
            ILExercise(id: 101, name: "Zercher Squat 2", isCustom: true),
            ILExercise(id: 102, name: "Zercher Squat 10", isCustom: true)
        ]
        let sorted = exercises.sortedByDisplayName()
        let expected = exercises.map(\.displayName).sorted { $0.localizedStandardCompare($1) == .orderedAscending }
        XCTAssertEqual(sorted.map(\.displayName), expected)
        // Natural order for numbers, as in Finder.
        XCTAssertLessThan(
            sorted.firstIndex { $0.id == 101 } ?? .max,
            sorted.firstIndex { $0.id == 102 } ?? .max
        )
    }
}
