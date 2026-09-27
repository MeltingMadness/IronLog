import Foundation
import XCTest
@testable import IronLogIOS

/// The Liquid Glass history groups trainings by ISO week (Monday first) and
/// sums the minutes per day for the mini bars.
final class HistoryWeekTests: XCTestCase {
    private func date(_ day: Int, hour: Int = 18) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 9, day: day, hour: hour))!
    }

    private func session(_ id: Int64, day: Int, hour: Int = 18, minutes: Int) -> ILWorkoutSession {
        let start = date(day, hour: hour)
        return ILWorkoutSession(
            id: id,
            startTime: Int64(start.timeIntervalSince1970 * 1000),
            endTime: Int64(start.addingTimeInterval(TimeInterval(minutes * 60)).timeIntervalSince1970 * 1000)
        )
    }

    func testGroupsByWeekNewestFirstAndSumsMinutes() {
        // 21.09.2026 is a Monday, 27.09.2026 a Sunday.
        let monday = session(1, day: 21, hour: 7, minutes: 60)
        let mondayEvening = session(2, day: 21, hour: 19, minutes: 30)
        let sunday = session(3, day: 27, minutes: 45)
        let previousSunday = session(4, day: 20, minutes: 50)
        let all = [monday, mondayEvening, sunday, previousSunday]

        let weeks = IOSHistoryWeek.group(visible: [sunday, monday, previousSunday], all: all)

        XCTAssertEqual(weeks.count, 2)
        XCTAssertEqual(weeks[0].minutesPerDay, [90, 0, 0, 0, 0, 0, 45])
        XCTAssertEqual(weeks[0].workoutCount, 3)
        XCTAssertEqual(weeks[0].sessions.map(\.id), [3, 1])
        XCTAssertEqual(weeks[1].minutesPerDay, [0, 0, 0, 0, 0, 0, 50])
    }

    func testShortTrainingCountsOneMinute() {
        let short = session(1, day: 23, minutes: 0)
        let weeks = IOSHistoryWeek.group(visible: [short], all: [short])
        XCTAssertEqual(weeks.first?.minutesPerDay, [0, 0, 1, 0, 0, 0, 0])
    }
}
