import XCTest
@testable import IronLogIOS

final class ProgressionDraftTests: XCTestCase {
    func testSuccessThresholdPersistsAndRejectsInvalidCounts() throws {
        var draft = IOSProgressionDraft(record: ILProgressionConfig(scheme: "LINEAR", successThreshold: 2))
        XCTAssertEqual(draft.successThreshold, "2")
        draft.successThreshold = "3"
        let config = try draft.validatedConfig().get()
        XCTAssertEqual(config.successThreshold, 3)
        XCTAssertEqual(config.ruleRevision, 2)
        let restored = try JSONDecoder().decode(ILProgressionConfig.self, from: JSONEncoder().encode(config))
        XCTAssertEqual(restored, config)
        for count in ["0", "7", "1.5", ""] {
            draft.successThreshold = count
            XCTAssertThrowsError(try draft.validatedConfig().get(), count)
        }
    }

    func testLegacyConfigDefaultsAndFailurePolicyMatchSharedValidation() throws {
        let legacy = try JSONDecoder().decode(ILProgressionConfig.self, from: Data(#"{"scheme":"LINEAR"}"#.utf8))
        XCTAssertEqual(legacy.successThreshold, 1)
        XCTAssertEqual(legacy.ruleRevision, 1)
        var draft = IOSProgressionDraft(record: legacy)
        draft.stallThreshold = "7"
        XCTAssertThrowsError(try draft.validatedConfig().get())
        draft.stallThreshold = "2"
        draft.backoffPercent = "31"
        XCTAssertThrowsError(try draft.validatedConfig().get())
    }

    func testDecimalDumbbellIncrementUsesBackupUnitContract() throws {
        var draft = IOSProgressionDraft()
        draft.scheme = .linear
        draft.stepValue = "1,5"
        draft.stepUnit = "KG"

        let config = try draft.validatedConfig().get()
        XCTAssertEqual(config.incrementUnit, "METRIC")
        XCTAssertEqual(try XCTUnwrap(config.incrementKg), 1.5, accuracy: 0.000001)
        XCTAssertEqual(try XCTUnwrap(config.incrementValue), 1.5, accuracy: 0.000001)
    }

    func testPoundIncrementKeepsOriginalValueAndCanonicalKilograms() throws {
        var draft = IOSProgressionDraft(unitSystem: "IMPERIAL")
        draft.scheme = .linear
        draft.stepValue = "2.5"
        draft.stepUnit = "LB"

        let config = try draft.validatedConfig().get()
        XCTAssertEqual(config.incrementUnit, "IMPERIAL")
        XCTAssertEqual(try XCTUnwrap(config.incrementValue), 2.5, accuracy: 0.000001)
        XCTAssertEqual(try XCTUnwrap(config.incrementKg), 1.133980925, accuracy: 0.000001)
    }
}
