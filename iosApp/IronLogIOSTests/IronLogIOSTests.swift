import Foundation
import XCTest
@testable import IronLogIOS

final class IronLogIOSTests: XCTestCase {
    func testSharedBootstrapStatusLineIsPresent() {
        XCTAssertFalse(SharedBootstrap.statusLine.isEmpty)
    }

    func testRpeRirPlanKeepsIntensityVisibleWhenGlobalTrackingIsOff() {
        let target = ILWorkoutPlanTarget(
            progression: ILProgressionConfig(scheme: "RPE_RIR", targetRpe: 8)
        )
        XCTAssertEqual(
            iosWorkoutEffectiveIntensitySystem(configuredSystem: "OFF", planTarget: target),
            "RPE"
        )
        XCTAssertEqual(
            iosWorkoutEffectiveIntensitySystem(configuredSystem: "OFF", planTarget: nil),
            "OFF"
        )
    }

    func testIntensitySavePreservesHiddenRpeButAllowsVisibleClear() throws {
        let manualSetRPE = try iosWorkoutStoredIntensityForSave(
            existingRPE: 9,
            inputText: "",
            configuredSystem: "OFF",
            planTarget: nil
        )
        XCTAssertEqual(manualSetRPE, 9)

        let rpePlan = ILWorkoutPlanTarget(
            progression: ILProgressionConfig(scheme: "RPE_RIR", targetRpe: 8)
        )
        let clearedPlanRPE = try iosWorkoutStoredIntensityForSave(
            existingRPE: 9,
            inputText: "",
            configuredSystem: "OFF",
            planTarget: rpePlan
        )
        XCTAssertNil(clearedPlanRPE)

        let enteredRIR = try iosWorkoutStoredIntensityForSave(
            existingRPE: nil,
            inputText: "2",
            configuredSystem: "RIR",
            planTarget: nil
        )
        XCTAssertEqual(enteredRIR, 8)
    }

    func testVisibleIntensityRejectsValuesOutsideScale() {
        XCTAssertThrowsError(
            try iosWorkoutStoredIntensityForSave(
                existingRPE: 9,
                inputText: "11",
                configuredSystem: "RPE",
                planTarget: nil
            )
        ) { error in
            XCTAssertEqual(error as? IOSWorkoutIntensityInputError, .invalidRPE)
        }
    }

    func testHistoryDateFiltersUseLocalCalendarMidnightBoundary() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        let now = date(
            year: 2026,
            month: 9,
            day: 11,
            hour: 15,
            minute: 0,
            calendar: calendar
        )
        let boundary30 = date(year: 2026, month: 8, day: 12, hour: 0, calendar: calendar)
        let boundary90 = date(year: 2026, month: 6, day: 13, hour: 0, calendar: calendar)

        XCTAssertTrue(IOSHistoryDateFilter.last30.includes(boundary30, now: now, calendar: calendar))
        XCTAssertFalse(
            IOSHistoryDateFilter.last30.includes(
                boundary30.addingTimeInterval(-1),
                now: now,
                calendar: calendar
            )
        )
        XCTAssertTrue(IOSHistoryDateFilter.last90.includes(boundary90, now: now, calendar: calendar))
        XCTAssertFalse(
            IOSHistoryDateFilter.last90.includes(
                boundary90.addingTimeInterval(-1),
                now: now,
                calendar: calendar
            )
        )
    }

    private func date(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
        calendar: Calendar
    ) -> Date {
        calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }
}
