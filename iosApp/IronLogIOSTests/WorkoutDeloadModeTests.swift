import XCTest
@testable import IronLogIOS

/// The deload mode is authoritative for every workout start: the store always records a concrete
/// deload state, and a caller may only escalate to `true`, never downgrade.
final class WorkoutDeloadModeTests: XCTestCase {
    func testStartWithoutModeSendsExplicitFalse() {
        let fields = iosWorkoutStartFields(
            name: "Push",
            planId: 7,
            metaPlanId: nil,
            explicitDeload: nil,
            deloadModeActive: false
        )

        XCTAssertEqual(fields["name"] as? String, "Push")
        XCTAssertEqual(fields["planId"] as? Int64, 7)
        XCTAssertNil(fields["metaPlanId"])
        XCTAssertEqual(fields["isDeload"] as? Bool, false)
    }

    func testActiveDeloadModeMarksEveryStart() {
        let fields = iosWorkoutStartFields(
            name: "",
            planId: nil,
            metaPlanId: 3,
            explicitDeload: nil,
            deloadModeActive: true
        )

        XCTAssertEqual(fields["metaPlanId"] as? Int64, 3)
        XCTAssertEqual(fields["isDeload"] as? Bool, true)
    }

    func testExplicitDeloadEscalatesWithoutMode() {
        let fields = iosWorkoutStartFields(
            name: "",
            planId: nil,
            metaPlanId: nil,
            explicitDeload: true,
            deloadModeActive: false
        )

        XCTAssertEqual(fields["isDeload"] as? Bool, true)
    }

    func testDeloadFlagIsEscalateOnly() {
        XCTAssertFalse(iosWorkoutDeloadFlag(explicit: nil, modeActive: false))
        XCTAssertTrue(iosWorkoutDeloadFlag(explicit: nil, modeActive: true))
        XCTAssertTrue(iosWorkoutDeloadFlag(explicit: true, modeActive: false))
        // An about-to-start or running session is never downgraded back to normal.
        XCTAssertTrue(iosWorkoutDeloadFlag(explicit: false, modeActive: true))
    }
}
