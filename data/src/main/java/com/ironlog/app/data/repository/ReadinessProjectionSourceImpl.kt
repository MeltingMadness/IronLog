package com.ironlog.app.data.repository

import com.ironlog.app.data.local.IronLogDatabase
import com.ironlog.app.domain.repository.BackupRepository
import com.ironlog.app.domain.repository.ReadinessProjectionSource
import com.ironlog.shared.backup.BackupPayloadV1
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Room-backed [ReadinessProjectionSource].
 *
 * [observePayload] watches the tables that feed the readiness input with a single
 * Room invalidation flow and re-reads the canonical payload for each change.
 * Delegating to [BackupRepository.currentPayload] keeps the readiness view on the
 * exact same mapping as the backup export, so a second interpretation of the
 * stored graph cannot drift away from it.
 */
class ReadinessProjectionSourceImpl(
    private val database: IronLogDatabase,
    private val backupRepository: BackupRepository
) : ReadinessProjectionSource {

    override suspend fun currentPayload(): BackupPayloadV1 = backupRepository.currentPayload()

    override fun observePayload(): Flow<BackupPayloadV1> =
        database.invalidationTracker
            // Only the tables that can change the assessment: the exercise catalog,
            // the completed workout graph and the readiness side channel. Metadata
            // tables (personal records, meta-plan rotations) are not observed.
            .createFlow(
                "exercises",
                "workout_sessions",
                "workout_sets",
                "plan_exercises",
                "workout_plan_targets",
                "readiness_data",
                emitInitialState = true,
            )
            .map { backupRepository.currentPayload() }
            .flowOn(Dispatchers.IO)
}
