import Foundation
import XCTest
@testable import IronLogIOS

/// "−15 s" / "+30 s" on the Liquid Glass pause screen move the deadline, but a
/// shortened pause never ends before now.
final class RestTimerAdjustTests: XCTestCase {
    private let start = Date(timeIntervalSince1970: 1_800_000_000)

    private func timer(duration: Int) -> IOSWorkoutRestTimer {
        IOSWorkoutRestTimer.make(sessionID: 1, rowKey: "target-1", durationSeconds: duration, now: start)
    }

    func testPlusThirtyExtendsThePause() {
        let adjusted = timer(duration: 120).adjusted(by: 30, now: start.addingTimeInterval(10))
        XCTAssertEqual(adjusted.durationSeconds, 150)
        XCTAssertEqual(adjusted.remaining(at: start.addingTimeInterval(10)), 140)
    }

    func testMinusFifteenShortensThePause() {
        let adjusted = timer(duration: 120).adjusted(by: -15, now: start.addingTimeInterval(10))
        XCTAssertEqual(adjusted.durationSeconds, 105)
    }

    func testShorteningEndsAtTheLatestNow() {
        let now = start.addingTimeInterval(110)
        let adjusted = timer(duration: 120).adjusted(by: -60, now: now)
        XCTAssertEqual(adjusted.remaining(at: now), 0)
        XCTAssertEqual(adjusted.durationSeconds, 110)
    }

    func testElapsedTimerStaysUnchanged() {
        let elapsed = timer(duration: 0)
        XCTAssertEqual(elapsed.adjusted(by: 30, now: start.addingTimeInterval(5)), elapsed)
    }
}
