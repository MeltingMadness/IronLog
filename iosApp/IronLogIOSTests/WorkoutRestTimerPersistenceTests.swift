import Foundation
import XCTest
@testable import IronLogIOS

final class WorkoutRestTimerPersistenceTests: XCTestCase {
    private let defaults = UserDefaults.standard

    override func setUp() {
        super.setUp()
        IOSWorkoutRestTimerPersistence.clear()
    }

    override func tearDown() {
        IOSWorkoutRestTimerPersistence.clear()
        super.tearDown()
    }

    func testLegacySingleTimerMigratesToRowMapAndClearsLegacyKeys() throws {
        let sessionID: Int64 = 41
        defaults.set(sessionID, forKey: IOSWorkoutRestTimerPersistence.sessionIDKey)
        defaults.set("target-7", forKey: IOSWorkoutRestTimerPersistence.rowKeyKey)
        defaults.set(Int64(1_000_000), forKey: IOSWorkoutRestTimerPersistence.startedAtEpochMillisKey)
        defaults.set(Int64(1_120_000), forKey: IOSWorkoutRestTimerPersistence.deadlineEpochMillisKey)
        defaults.set(120, forKey: IOSWorkoutRestTimerPersistence.durationSecondsKey)

        let restored = IOSWorkoutRestTimerPersistence.load(sessionID: sessionID)
        let timer = try XCTUnwrap(restored["target-7"])
        XCTAssertEqual(timer.sessionID, sessionID)
        XCTAssertEqual(timer.deadlineEpochMillis, 1_120_000)
        XCTAssertNil(defaults.object(forKey: IOSWorkoutRestTimerPersistence.rowKeyKey))
        XCTAssertFalse(restored.isEmpty)

        // A second load reads the new array format and does not depend on the
        // removed legacy fields.
        XCTAssertEqual(IOSWorkoutRestTimerPersistence.load(sessionID: sessionID), restored)
    }

    func testMultipleRowsRoundTripAndRemoveOnlyOneRow() throws {
        let sessionID: Int64 = 52
        let first = IOSWorkoutRestTimer.make(
            sessionID: sessionID,
            rowKey: "target-1",
            durationSeconds: 90,
            now: Date(timeIntervalSince1970: 2_000)
        )
        let second = IOSWorkoutRestTimer.make(
            sessionID: sessionID,
            rowKey: "target-2",
            durationSeconds: 0,
            now: Date(timeIntervalSince1970: 2_010)
        )

        IOSWorkoutRestTimerPersistence.saveAll([first, second])
        let restored = IOSWorkoutRestTimerPersistence.load(sessionID: sessionID)
        XCTAssertEqual(restored.count, 2)
        XCTAssertEqual(restored["target-1"], first)
        XCTAssertEqual(restored["target-2"], second)

        IOSWorkoutRestTimerPersistence.remove(sessionID: sessionID, rowKey: "target-1")
        let afterRemoval = IOSWorkoutRestTimerPersistence.load(sessionID: sessionID)
        XCTAssertNil(afterRemoval["target-1"])
        XCTAssertEqual(afterRemoval["target-2"], second)
    }

    func testCountdownDeadlineSurvivesReloadAndKeepsOtherSessionRows() throws {
        let sessionID: Int64 = 63
        let otherSessionID: Int64 = 64
        let countdown = IOSWorkoutRestTimer.make(
            sessionID: sessionID,
            rowKey: "target-3",
            durationSeconds: 120,
            now: Date(timeIntervalSince1970: 3_000)
        )
        let other = IOSWorkoutRestTimer.make(
            sessionID: otherSessionID,
            rowKey: "adhoc-8",
            durationSeconds: 0,
            now: Date(timeIntervalSince1970: 3_001)
        )

        IOSWorkoutRestTimerPersistence.saveAll([countdown, other])
        let reloaded = IOSWorkoutRestTimerPersistence.load(sessionID: sessionID)
        let reloadedTimer = try XCTUnwrap(reloaded["target-3"])
        XCTAssertEqual(reloadedTimer.deadlineEpochMillis, countdown.deadlineEpochMillis)
        XCTAssertEqual(reloadedTimer.remaining(at: Date(timeIntervalSince1970: 3_030)), 90)

        IOSWorkoutRestTimerPersistence.clear(sessionID: sessionID)
        XCTAssertEqual(IOSWorkoutRestTimerPersistence.load(sessionID: otherSessionID)["adhoc-8"], other)
    }
}
