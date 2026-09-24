import Foundation
import XCTest
@testable import IronLogIOS

/// The dashboard must notice a day change on its own, because a suspended app wakes
/// up on whatever day it is then. These tests pin the guard's contract: report the
/// change exactly once, and keep the day the UI is currently presenting.
final class LocalDayGuardTests: XCTestCase {
    func testObservingTheSameDayReportsNoChange() {
        var guardValue = ILLocalDayGuard(currentDate: "2026-09-11")

        XCTAssertFalse(guardValue.observe("2026-09-11"))
        XCTAssertEqual(guardValue.currentDate, "2026-09-11")
    }

    func testObservingANewDayReportsChangeExactlyOnce() {
        var guardValue = ILLocalDayGuard(currentDate: "2026-09-11")

        XCTAssertTrue(guardValue.observe("2026-09-12"))
        XCTAssertEqual(guardValue.currentDate, "2026-09-12")
        // Repeated ticks after midnight must not re-trigger the refresh work.
        XCTAssertFalse(guardValue.observe("2026-09-12"))
    }

    func testBackwardsDayChangeIsReportedAsChange() {
        // A time-zone move or clock correction can move "today" backwards; the guard
        // still has to tell the dashboard that its date string is no longer valid.
        var guardValue = ILLocalDayGuard(currentDate: "2026-09-12")

        XCTAssertTrue(guardValue.observe("2026-09-11"))
        XCTAssertEqual(guardValue.currentDate, "2026-09-11")
    }

    func testDefaultObservationUsesTheSuppliedCalendar() {
        let instant = Date(timeIntervalSince1970: 1_700_000_000) // 2023-11-14T22:13:20Z
        var utcCalendar = Calendar(identifier: .gregorian)
        utcCalendar.timeZone = TimeZone(identifier: "UTC")!
        // A fixed +02:00 zone keeps the example independent of the time-zone database.
        var plusTwoCalendar = Calendar(identifier: .gregorian)
        plusTwoCalendar.timeZone = TimeZone(secondsFromGMT: 2 * 60 * 60)!

        var guardValue = ILLocalDayGuard(currentDate: ilCurrentLocalDate(instant, calendar: utcCalendar))
        XCTAssertEqual(guardValue.currentDate, "2023-11-14")
        // The same instant is already the next day at +02:00, so the guard reports it.
        XCTAssertTrue(guardValue.observe(ilCurrentLocalDate(instant, calendar: plusTwoCalendar)))
        XCTAssertEqual(guardValue.currentDate, "2023-11-15")
    }
}
