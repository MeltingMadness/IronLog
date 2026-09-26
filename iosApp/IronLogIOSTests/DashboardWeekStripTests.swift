import Foundation
import XCTest
@testable import IronLogIOS

/// The Liquid Glass week strip marks a day as trained only for completed
/// workouts, the same rule as the week counter next to it.
final class DashboardWeekStripTests: XCTestCase {
    private func date(_ day: Int, hour: Int = 18) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour))!
    }

    private func millis(_ date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 * 1000)
    }

    func testWeekHasSevenDaysAndMarksCompletedWorkoutsAndToday() {
        let completed = ILWorkoutSession(id: 1, startTime: millis(date(22)), endTime: millis(date(22, hour: 19)))
        let active = ILWorkoutSession(id: 2, startTime: millis(date(24)), endTime: nil)

        let days = IOSDashboardWeekDay.week(startingAt: "2026-09-21", sessions: [completed, active], now: date(26))

        XCTAssertEqual(days.count, 7)
        XCTAssertEqual(days.filter(\.trained).map { Calendar.current.component(.day, from: $0.date) }, [22])
        XCTAssertEqual(days.filter(\.isToday).map { Calendar.current.component(.day, from: $0.date) }, [26])
    }

    func testInvalidWeekStartGivesNoDays() {
        XCTAssertTrue(IOSDashboardWeekDay.week(startingAt: "", sessions: []).isEmpty)
    }
}
