import Foundation
import XCTest
@testable import ThrymIOS

/// Checks the string catalogs as written in the repository, and that the app
/// bundle ships what they say.
///
/// The `.xcstrings` sources are not part of the built app (they compile to
/// `.strings`/`.stringsdict` files), so the tests read them next to this file.
/// Bundle lookups use the `en.lproj` and `de.lproj` bundles explicitly, so the
/// result does not depend on the language the tests run in.
final class StringCatalogTests: XCTestCase {
    private static let languages = ["de", "en"]

    // MARK: - Catalog sources

    private struct Catalog {
        let name: String
        let strings: [String: [String: Any]]
    }

    private func catalog(_ fileName: String) throws -> Catalog {
        let url = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("ThrymIOS/\(fileName).xcstrings")
        let data = try Data(contentsOf: url)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(root["sourceLanguage"] as? String, "de", "\(fileName): German literals are the keys")
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])
        return Catalog(name: fileName, strings: strings)
    }

    private func languageBundle(_ language: String) throws -> Bundle {
        let path = try XCTUnwrap(
            Bundle.main.path(forResource: language, ofType: "lproj"),
            "\(language).lproj is missing from the app bundle"
        )
        return try XCTUnwrap(Bundle(path: path))
    }

    // MARK: - Format specifiers

    /// One printf argument: its 1-based position and its conversion ("@", "lld", …).
    private struct Argument: Hashable, CustomStringConvertible {
        let index: Int
        let type: String
        var description: String { "%\(index)$\(type)" }
    }

    /// `%`, optional position, optional flags/width/precision/length, conversion.
    /// A space is not accepted as a flag: German keys such as "zwischen 1 und 30 %
    /// liegen" contain a literal percent sign followed by a space.
    private static let specifier = try! NSRegularExpression(
        pattern: #"%(?:(\d+)\$)?(?:#@(\w+)@|[-+0#']*\d*(?:\.\d+)?((?:hh|h|ll|l|q|z|t|j|L)?[@dDiuUxXoOfFeEgGcCsSpaA]))"#
    )

    /// Arguments a format string consumes. `%#@name@` resolves through the
    /// substitution of that name; `%arg` inside a substitution is its own argument.
    private func arguments(
        in format: String,
        substitutions: [String: [String: Any]] = [:],
        ownArgument: Argument? = nil
    ) -> [Argument] {
        var text = format.replacingOccurrences(of: "%%", with: "")
        if let ownArgument {
            text = text.replacingOccurrences(of: "%arg", with: "%\(ownArgument.index)$\(ownArgument.type)")
        }
        let range = NSRange(text.startIndex..., in: text)
        var next = 1
        return Self.specifier.matches(in: text, range: range).map { match in
            func group(_ i: Int) -> String? {
                Range(match.range(at: i), in: text).map { String(text[$0]) }
            }
            if let name = group(2) {
                let substitution = substitutions[name] ?? [:]
                let index = substitution["argNum"] as? Int ?? next
                next = index + 1
                return Argument(index: index, type: substitution["formatSpecifier"] as? String ?? "?\(name)")
            }
            let index = group(1).flatMap(Int.init) ?? next
            next = index + 1
            return Argument(index: index, type: group(3) ?? "?")
        }
    }

    /// Every format string of one localization with the arguments it consumes:
    /// the plain value, each plural/device variant and each substitution variant.
    private func formats(of localization: [String: Any]) -> [(path: String, arguments: [Argument])] {
        var result: [(String, [Argument])] = []
        let substitutions = localization["substitutions"] as? [String: [String: Any]] ?? [:]

        func collect(_ node: [String: Any], path: String, ownArgument: Argument?) {
            if let unit = node["stringUnit"] as? [String: Any], let value = unit["value"] as? String {
                result.append((path, arguments(in: value, substitutions: substitutions, ownArgument: ownArgument)))
            }
            for (kind, cases) in node["variations"] as? [String: [String: [String: Any]]] ?? [:] {
                for (name, variant) in cases {
                    collect(variant, path: "\(path)/\(kind).\(name)", ownArgument: ownArgument)
                }
            }
        }

        collect(localization, path: "value", ownArgument: nil)
        for (name, substitution) in substitutions {
            let own = Argument(
                index: substitution["argNum"] as? Int ?? 0,
                type: substitution["formatSpecifier"] as? String ?? "?"
            )
            collect(substitution, path: "substitution \(name)", ownArgument: own)
        }
        return result
    }

    private func checkSpecifiers(_ catalog: Catalog) {
        for (key, entry) in catalog.strings {
            let expected = Set(arguments(in: key))
            let localizations = entry["localizations"] as? [String: [String: Any]] ?? [:]
            for (language, localization) in localizations {
                let found = formats(of: localization)
                XCTAssertFalse(found.isEmpty, "\(catalog.name) [\(language)] \(key): no value")
                var used = Set<Argument>()
                for (path, arguments) in found {
                    used.formUnion(arguments)
                    for argument in arguments where !expected.contains(argument) {
                        XCTFail("\(catalog.name) [\(language)] \(key) (\(path)): \(argument) is not an argument of the key \(expected.sorted { $0.index < $1.index })")
                    }
                }
                XCTAssertEqual(
                    used.sorted { $0.index < $1.index }, expected.sorted { $0.index < $1.index },
                    "\(catalog.name) [\(language)] \(key): arguments differ from the key"
                )
            }
        }
    }

    private func checkLanguages(_ catalog: Catalog) {
        for (key, entry) in catalog.strings {
            let localizations = entry["localizations"] as? [String: Any] ?? [:]
            if entry["shouldTranslate"] as? Bool == false {
                // Neutral keys ("%lld min", "RPE", theme names) read the same in every
                // language; the lookup falls back to the key itself.
                XCTAssertTrue(
                    localizations["en"] == nil,
                    "\(catalog.name) \(key): marked neutral but has an English value"
                )
                continue
            }
            for language in Self.languages {
                XCTAssertNotNil(localizations[language], "\(catalog.name) \(key): no \(language) entry")
            }
        }
    }

    func testLocalizableCatalogHasGermanAndEnglishWithMatchingSpecifiers() throws {
        let catalog = try catalog("Localizable")
        XCTAssertGreaterThan(catalog.strings.count, 900)
        checkLanguages(catalog)
        checkSpecifiers(catalog)
    }

    func testExerciseCatalogHasGermanAndEnglishForEveryBuiltInName() throws {
        let catalog = try catalog("Exercises")
        // DefaultExerciseCatalog seeds 79 built-in exercises.
        XCTAssertEqual(catalog.strings.count, 79)
        checkLanguages(catalog)
        checkSpecifiers(catalog)
        for (key, entry) in catalog.strings {
            XCTAssertEqual(entry["extractionState"] as? String, "manual", "\(key) is data, not a code literal")
            let localizations = entry["localizations"] as? [String: [String: Any]] ?? [:]
            let german = (localizations["de"]?["stringUnit"] as? [String: Any])?["value"] as? String
            XCTAssertEqual(german, key, "The German name is the stored canonical name")
        }
        let english = catalog.strings.values.compactMap {
            (($0["localizations"] as? [String: [String: Any]])?["en"]?["stringUnit"] as? [String: Any])?["value"] as? String
        }
        XCTAssertEqual(Set(english).count, catalog.strings.count, "Two built-in exercises share an English name")
    }

    // MARK: - Built bundle

    func testBundleShipsTheCatalogValuesForBothLanguages() throws {
        for (fileName, table) in [("Localizable", nil as String?), ("Exercises", "Exercises")] {
            let catalog = try catalog(fileName)
            for language in Self.languages {
                let bundle = try languageBundle(language)
                var checked = 0
                for (key, entry) in catalog.strings {
                    let localization = (entry["localizations"] as? [String: [String: Any]])?[language]
                    // Plural and substitution entries compile to .stringsdict; the
                    // LocalizationTests render those through String(localized:).
                    guard let localization, localization["variations"] == nil, localization["substitutions"] == nil,
                          let value = (localization["stringUnit"] as? [String: Any])?["value"] as? String
                    else { continue }
                    XCTAssertEqual(
                        bundle.localizedString(forKey: key, value: "<missing>", table: table), value,
                        "\(language).lproj/\(table ?? "Localizable"): \(key)"
                    )
                    checked += 1
                }
                XCTAssertGreaterThan(checked, 0, "\(fileName) [\(language)]")
            }
        }
    }

    func testSampleLookupsInEachLanguage() throws {
        let english = try languageBundle("en")
        let german = try languageBundle("de")
        XCTAssertEqual(english.localizedString(forKey: "Abbrechen", value: nil, table: nil), "Cancel")
        XCTAssertEqual(german.localizedString(forKey: "Abbrechen", value: nil, table: nil), "Abbrechen")
        XCTAssertEqual(english.localizedString(forKey: "Einstellungen", value: nil, table: nil), "Settings")
        XCTAssertEqual(english.localizedString(forKey: "Kreuzheben", value: nil, table: "Exercises"), "Deadlift")
        XCTAssertEqual(german.localizedString(forKey: "Kreuzheben", value: nil, table: "Exercises"), "Kreuzheben")
        XCTAssertEqual(
            english.localizedString(forKey: "Bankdrücken (Langhantel)", value: nil, table: "Exercises"),
            "Bench Press (Barbell)"
        )
    }
}
