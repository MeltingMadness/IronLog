import XCTest
@testable import IronLogIOS

final class ProgressionDraftTests: XCTestCase {
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
