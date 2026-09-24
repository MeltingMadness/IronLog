import Foundation
import Shared
import XCTest
@testable import IronLogIOS

@MainActor
final class BackupBridgeTests: XCTestCase {
    private enum BridgeTestError: Error, CustomStringConvertible {
        case callback(String)

        var description: String {
            switch self {
            case .callback(let message): return message
            }
        }
    }

    private struct InspectResult {
        let preview: IosBackupPreview?
        let error: String?
    }

    func testExportAndInspectRoundTripUsesTrainingDataContract() async throws {
        let feature = IosSettingsFeature()
        defer { feature.close() }

        let document = try await exportDocument(from: feature)
        XCTAssertEqual(document.fileName, "ironlog-backup-v1.json")
        XCTAssertEqual(document.mimeType, "application/json")

        let bytes = try XCTUnwrap(
            Data(base64Encoded: document.base64Data),
            "Exportantwort muss Base64-kodierte JSON-Bytes enthalten",
        )
        XCTAssertFalse(bytes.isEmpty)

        let payload = try JSONDecoder().decode(ILTrainingData.self, from: bytes)
        XCTAssertEqual(payload.formatVersion, 1)
        XCTAssertGreaterThan(payload.schemaVersion, 0)

        let preview = try await inspect(document.base64Data, using: feature)
        XCTAssertEqual(Int(preview.formatVersion), payload.formatVersion)
        XCTAssertEqual(Int(preview.schemaVersion), payload.schemaVersion)
        XCTAssertEqual(Int(preview.exerciseCount), payload.exercises.count)
        XCTAssertEqual(Int(preview.workoutSessionCount), payload.workoutSessions.count)
        XCTAssertEqual(Int(preview.workoutSetCount), payload.workoutSets.count)
        XCTAssertEqual(Int(preview.trainingPlanCount), payload.trainingPlans.count)
        XCTAssertEqual(Int(preview.metaPlanCount), payload.metaTrainingPlans.count)
        XCTAssertEqual(
            Int(preview.progressionSuggestionCount),
            payload.progressionSuggestions.count,
        )
        XCTAssertTrue(preview.replacesExistingData)

        // The confirmation guard must carry the exact Kotlin snapshot that was
        // current when inspectBackup decoded these same bytes.
        let expectedSnapshot = try XCTUnwrap(preview.expectedSnapshot)
        XCTAssertEqual(Int(expectedSnapshot.formatVersion), payload.formatVersion)
        XCTAssertEqual(Int(expectedSnapshot.schemaVersion), payload.schemaVersion)
        XCTAssertEqual(expectedSnapshot.exercises.count, payload.exercises.count)
        XCTAssertEqual(expectedSnapshot.workoutSessions.count, payload.workoutSessions.count)
        XCTAssertEqual(expectedSnapshot.workoutSets.count, payload.workoutSets.count)
        XCTAssertEqual(expectedSnapshot.trainingPlans.count, payload.trainingPlans.count)
        XCTAssertEqual(expectedSnapshot.metaTrainingPlans.count, payload.metaTrainingPlans.count)
        XCTAssertEqual(
            expectedSnapshot.progressionSuggestions.count,
            payload.progressionSuggestions.count,
        )
    }

    func testCorruptInspectIsRejectedWithoutChangingStore() async throws {
        let feature = IosSettingsFeature()
        defer { feature.close() }

        let before = try await exportDocument(from: feature)
        let beforeBytes = try XCTUnwrap(Data(base64Encoded: before.base64Data))

        var corruptBytes = beforeBytes
        corruptBytes.append(0)
        let result = await inspectResult(
            corruptBytes.base64EncodedString(),
            using: feature,
        )

        XCTAssertNil(result.preview)
        XCTAssertNotNil(result.error)

        // inspectBackup is read-only; exporting again must produce the exact
        // same durable graph, including seeded simulator data. Export metadata
        // contains a fresh timestamp, so compare the durable fields after
        // decoding rather than the raw document bytes.
        let after = try await exportDocument(from: feature)
        let afterBytes = try XCTUnwrap(Data(base64Encoded: after.base64Data))
        let beforePayload = try JSONDecoder().decode(ILTrainingData.self, from: beforeBytes)
        let afterPayload = try JSONDecoder().decode(ILTrainingData.self, from: afterBytes)
        assertDurablePayloadEqual(beforePayload, afterPayload)
    }

    private func exportDocument(from feature: IosSettingsFeature) async throws -> IosDocumentPayload {
        try await withCheckedThrowingContinuation { continuation in
            feature.exportBackup { payload, error in
                if let payload {
                    continuation.resume(returning: payload)
                } else {
                    continuation.resume(
                        throwing: BridgeTestError.callback(
                            error ?? "Backup konnte nicht exportiert werden.",
                        ),
                    )
                }
            }
        }
    }

    private func inspect(_ base64Data: String, using feature: IosSettingsFeature) async throws -> IosBackupPreview {
        let result = await inspectResult(base64Data, using: feature)
        if let preview = result.preview {
            return preview
        }
        throw BridgeTestError.callback(result.error ?? "Backup konnte nicht geprüft werden.")
    }

    private func inspectResult(
        _ base64Data: String,
        using feature: IosSettingsFeature,
    ) async -> InspectResult {
        await withCheckedContinuation { continuation in
            feature.inspectBackup(base64Data: base64Data) { preview, error in
                continuation.resume(returning: InspectResult(preview: preview, error: error))
            }
        }
    }

    private func assertDurablePayloadEqual(
        _ lhs: ILTrainingData,
        _ rhs: ILTrainingData,
        file: StaticString = #filePath,
        line: UInt = #line,
    ) {
        XCTAssertEqual(lhs.formatVersion, rhs.formatVersion, file: file, line: line)
        XCTAssertEqual(lhs.schemaVersion, rhs.schemaVersion, file: file, line: line)
        XCTAssertEqual(lhs.appVersion, rhs.appVersion, file: file, line: line)
        XCTAssertEqual(lhs.exercises, rhs.exercises, file: file, line: line)
        XCTAssertEqual(lhs.workoutSessions, rhs.workoutSessions, file: file, line: line)
        XCTAssertEqual(lhs.workoutSets, rhs.workoutSets, file: file, line: line)
        XCTAssertEqual(lhs.trainingPlans, rhs.trainingPlans, file: file, line: line)
        XCTAssertEqual(lhs.planExercises, rhs.planExercises, file: file, line: line)
        XCTAssertEqual(lhs.personalRecords, rhs.personalRecords, file: file, line: line)
        XCTAssertEqual(lhs.metaTrainingPlans, rhs.metaTrainingPlans, file: file, line: line)
        XCTAssertEqual(lhs.metaPlanItems, rhs.metaPlanItems, file: file, line: line)
        XCTAssertEqual(lhs.metaPlanSkips, rhs.metaPlanSkips, file: file, line: line)
        XCTAssertEqual(lhs.workoutPlanTargets, rhs.workoutPlanTargets, file: file, line: line)
        XCTAssertEqual(
            lhs.progressionSuggestions,
            rhs.progressionSuggestions,
            file: file,
            line: line,
        )
    }
}
