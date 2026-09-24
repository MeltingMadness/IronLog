import Foundation
import Shared
import XCTest
@testable import IronLogIOS

@MainActor
final class RecoveryBridgeTests: XCTestCase {
    private enum TestError: Error {
        case callback(String)
    }

    private struct ImportResult {
        let success: Bool
        let error: String?
    }

    private struct RecoveryResult {
        let recovery: IosRecoveryBackup?
        let error: String?
    }

    func testConfirmedImportCreatesRecoveryAndRestoreReturnsPreviousGraph() async throws {
        let feature = IosSettingsFeature()
        defer { feature.close() }

        let baselineDocument = try await exportDocument(from: feature)
        let baselineBytes = try XCTUnwrap(Data(base64Encoded: baselineDocument.base64Data))
        let baseline = try JSONDecoder().decode(ILTrainingData.self, from: baselineBytes)
        var candidate = baseline
        guard let firstExercise = candidate.exercises.first else {
            throw TestError.callback("Der Test benötigt mindestens eine katalogisierte Übung.")
        }
        candidate.exercises[0].name = firstExercise.name + " (Import)"
        let candidateData = try JSONEncoder().encode(candidate)
        let candidateBase64 = candidateData.base64EncodedString()

        let preview = try await inspect(candidateBase64, using: feature)
        let expectedSnapshot = try XCTUnwrap(preview.expectedSnapshot)
        let imported = await importBackup(
            candidateBase64,
            expectedSnapshot: expectedSnapshot,
            using: feature,
        )
        XCTAssertTrue(imported.success, imported.error ?? "Import muss erfolgreich sein")

        let importedDocument = try await exportDocument(from: feature)
        let importedData = try JSONDecoder().decode(
            ILTrainingData.self,
            from: XCTUnwrap(Data(base64Encoded: importedDocument.base64Data)),
        )
        XCTAssertEqual(importedData.exercises.first?.name, firstExercise.name + " (Import)")

        let recovery = await latestRecovery(using: feature)
        let recoveryMetadata = try XCTUnwrap(recovery.recovery, recovery.error ?? "Recovery fehlt")
        XCTAssertEqual(Int(recoveryMetadata.exerciseCount), baseline.exercises.count)

        let restored = await restoreLatestRecovery(using: feature)
        XCTAssertNotNil(restored.recovery, restored.error ?? "Restore muss erfolgreich sein")

        let restoredDocument = try await exportDocument(from: feature)
        let restoredData = try JSONDecoder().decode(
            ILTrainingData.self,
            from: XCTUnwrap(Data(base64Encoded: restoredDocument.base64Data)),
        )
        XCTAssertEqual(restoredData.exercises, baseline.exercises)
        XCTAssertEqual(restoredData.workoutSessions, baseline.workoutSessions)
        XCTAssertEqual(restoredData.workoutSets, baseline.workoutSets)
        XCTAssertEqual(restoredData.trainingPlans, baseline.trainingPlans)
        XCTAssertEqual(restoredData.planExercises, baseline.planExercises)
        XCTAssertEqual(restoredData.personalRecords, baseline.personalRecords)
        XCTAssertEqual(restoredData.metaTrainingPlans, baseline.metaTrainingPlans)
        XCTAssertEqual(restoredData.metaPlanItems, baseline.metaPlanItems)
        XCTAssertEqual(restoredData.metaPlanSkips, baseline.metaPlanSkips)
        XCTAssertEqual(restoredData.workoutPlanTargets, baseline.workoutPlanTargets)
        XCTAssertEqual(restoredData.progressionSuggestions, baseline.progressionSuggestions)
    }

    private func exportDocument(from feature: IosSettingsFeature) async throws -> IosDocumentPayload {
        try await withCheckedThrowingContinuation { continuation in
            feature.exportBackup { payload, error in
                if let payload {
                    continuation.resume(returning: payload)
                } else {
                    continuation.resume(throwing: TestError.callback(error ?? "Export fehlgeschlagen"))
                }
            }
        }
    }

    private func inspect(_ base64Data: String, using feature: IosSettingsFeature) async throws -> IosBackupPreview {
        try await withCheckedThrowingContinuation { continuation in
            feature.inspectBackup(base64Data: base64Data) { preview, error in
                if let preview {
                    continuation.resume(returning: preview)
                } else {
                    continuation.resume(throwing: TestError.callback(error ?? "Preview fehlgeschlagen"))
                }
            }
        }
    }

    private func importBackup(
        _ base64Data: String,
        expectedSnapshot: BackupPayloadV1,
        using feature: IosSettingsFeature,
    ) async -> ImportResult {
        await withCheckedContinuation { continuation in
            feature.importBackup(base64Data: base64Data, expectedSnapshot: expectedSnapshot) { success, error in
                continuation.resume(returning: ImportResult(success: success.boolValue, error: error))
            }
        }
    }

    private func latestRecovery(using feature: IosSettingsFeature) async -> RecoveryResult {
        await withCheckedContinuation { continuation in
            feature.latestRecovery { recovery, error in
                continuation.resume(returning: RecoveryResult(recovery: recovery, error: error))
            }
        }
    }

    private func restoreLatestRecovery(using feature: IosSettingsFeature) async -> RecoveryResult {
        await withCheckedContinuation { continuation in
            feature.restoreLatestRecovery { recovery, error in
                continuation.resume(returning: RecoveryResult(recovery: recovery, error: error))
            }
        }
    }
}
