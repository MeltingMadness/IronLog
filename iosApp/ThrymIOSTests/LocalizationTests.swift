import Foundation
import XCTest
@testable import ThrymIOS

/// English is the development language and German a translation. The plural
/// tests pin the language explicitly because the wording itself is under test;
/// everything else resolves its expectation from the app's own resources.
final class LocalizationTests: XCTestCase {
    private func languageBundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(
            Bundle.main.path(forResource: language, ofType: "lproj"),
            "\(language).lproj is missing from the app bundle"
        )
        return try XCTUnwrap(Bundle(path: path))
    }

    private func text(_ value: String.LocalizationValue, _ language: String) throws -> String {
        String(localized: value, bundle: try languageBundle(language), locale: Locale(identifier: language))
    }

    func testAppShipsEnglishAndGermanWithEnglishFallback() throws {
        XCTAssertEqual(Bundle.main.developmentLocalization, "en")
        XCTAssertTrue(Bundle.main.localizations.contains("en"))
        XCTAssertTrue(Bundle.main.localizations.contains("de"))
        let declared = try XCTUnwrap(Bundle.main.object(forInfoDictionaryKey: "CFBundleLocalizations") as? [String])
        XCTAssertEqual(Set(declared), ["en", "de"])
    }

    func testCountKeysUsePluralVariations() throws {
        XCTAssertEqual(try text("\(1) Trainings", "en"), "1 workout")
        XCTAssertEqual(try text("\(3) Trainings", "en"), "3 workouts")
        XCTAssertEqual(try text("\(1) Trainings", "de"), "1 Training")
        XCTAssertEqual(try text("\(3) Trainings", "de"), "3 Trainings")

        XCTAssertEqual(try text("\(1) Vorschläge warten auf deine Entscheidung.", "de"), "1 Vorschlag wartet auf deine Entscheidung.")
        XCTAssertEqual(try text("\(2) Vorschläge warten auf deine Entscheidung.", "de"), "2 Vorschläge warten auf deine Entscheidung.")
        XCTAssertEqual(try text("\(1) Entscheidungen ausstehend", "en"), "1 decision pending")
    }

    func testSubstitutionsPluralizeTheRightArgument() throws {
        XCTAssertEqual(try text("\(0) / \(1) Sätze", "en"), "0 / 1 set")
        XCTAssertEqual(try text("\(2) / \(5) Sätze", "en"), "2 / 5 sets")
        XCTAssertEqual(try text("\(0) / \(1) Sätze", "de"), "0 / 1 Satz")

        XCTAssertEqual(try text("Noch \(1) Minuten \(1) Sekunden", "en"), "1 minute 1 second left")
        XCTAssertEqual(try text("Noch \(2) Minuten \(30) Sekunden", "de"), "Noch 2 Minuten 30 Sekunden")
        XCTAssertEqual(try text("Noch \(1) Minuten \(1) Sekunden", "de"), "Noch 1 Minute 1 Sekunde")

        XCTAssertEqual(try text("\(1) Sätze × \(8) Wdh · \("80 kg")", "en"), "1 set × 8 reps · 80 kg")
        XCTAssertEqual(try text("\(3) Sätze × \(1) Wdh · \("80 kg")", "de"), "3 Sätze × 1 Wdh · 80 kg")
    }

    func testHistoryDayLettersFollowTheDate() throws {
        var calendar = IOSHistoryWeek.calendar
        calendar.locale = .current
        let symbols = calendar.veryShortStandaloneWeekdaySymbols
        // 12.10.2026 is a Monday, 18.10.2026 a Sunday.
        let monday = try XCTUnwrap(calendar.date(from: DateComponents(year: 2026, month: 10, day: 12, hour: 12)))
        let letters = (0..<7).map { offset in
            IOSHistoryGlassMiniBars.dayLetter(for: calendar.date(byAdding: .day, value: offset, to: monday)!)
        }
        // Calendar weekday symbols start on Sunday; the history week starts on Monday.
        XCTAssertEqual(letters, Array(symbols[1...]) + [symbols[0]])
        XCTAssertTrue(letters.allSatisfy { !$0.isEmpty })
    }
}
