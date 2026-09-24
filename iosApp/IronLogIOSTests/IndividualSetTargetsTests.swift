import XCTest
@testable import IronLogIOS

final class IndividualSetTargetsTests: XCTestCase {
    func testLegacyPlanAndIndividualTargetsRoundTrip() throws {
        let legacy = Data(#"{"id":1,"planId":1,"exerciseId":1,"orderIndex":0,"targetSets":3,"targetReps":10,"targetWeightKg":60}"#.utf8)
        XCTAssertTrue(try JSONDecoder().decode(ILPlanExercise.self, from: legacy).setTargets.isEmpty)
        let plan = ILPlanExercise(id: 1, setTargets: [ILPlannedSet(kind: "WARMUP", reps: 10, weightKg: 20), ILPlannedSet(kind: "BACKOFF", reps: 8, weightKg: 55)])
        XCTAssertEqual(try JSONDecoder().decode(ILPlanExercise.self, from: JSONEncoder().encode(plan)), plan)
    }

    func testPrefillAndMatchingKeepWarmupSeparateFromWork() {
        let row = makeRow(sets: [ILWorkoutSet(id: 1, reps: 9, weightKg: 21, setType: "WARMUP")])
        XCTAssertEqual(row.remainingPlannedSets, 3)
        XCTAssertEqual(row.defaultWeightKg, 60)
        XCTAssertEqual(row.targetReps, 8)
        XCTAssertEqual(row.nextPlannedSet?.kind, "NORMAL")
    }

    func testIndividualTargetsRespectDeloadWithoutChangingSnapshot() {
        var row = makeRow()
        let raw = row.target
        row.deloadMode = "REDUCE_INTENSITY_BY_15_PERCENT"
        XCTAssertEqual(row.loggingSlots[0].weightKg, 17)
        XCTAssertEqual(row.loggingSlots[1].weightKg, 51)
        XCTAssertEqual(row.target, raw)
        row = makeRow(displaySets: 2)
        row.deloadMode = "HALVE_SET_VOLUME"
        XCTAssertEqual(row.loggingSlots.map(\.kind), ["WARMUP", "NORMAL", "NORMAL"])
        XCTAssertEqual(row.target?.setTargets.count, 4)
    }

    private func makeRow(sets: [ILWorkoutSet] = [], displaySets: Int = 3) -> IOSWorkoutExerciseRow {
        let target = ILWorkoutPlanTarget(id: 1, target: ILProgressionTarget(sets: 3, reps: 8, weightKg: 60), setTargets: [
            ILPlannedSet(kind: "WARMUP", reps: 10, weightKg: 20),
            ILPlannedSet(reps: 8, weightKg: 60), ILPlannedSet(reps: 8, weightKg: 60), ILPlannedSet(kind: "BACKOFF", reps: 10, weightKg: 55)
        ])
        return IOSWorkoutExerciseRow(id: "test", exercise: ILExercise(), target: target, displayTarget: ILProgressionTarget(sets: displaySets, reps: 8, weightKg: 60), sets: sets, previousWorkWeightKg: nil, nextSetRecommendation: nil)
    }
}
