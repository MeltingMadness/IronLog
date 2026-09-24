package com.ironlog.app.domain.repository

import com.ironlog.shared.backup.BackupPayloadV1
import kotlinx.coroutines.flow.Flow

/**
 * Android data source for the shared readiness projection.
 *
 * Consumers (dashboard, statistics, ...) must not build their own engine input:
 * this source hands out exactly the same [BackupPayloadV1] the backup export
 * uses, so [com.ironlog.shared.analytics.SharedReadinessProjection] computes the
 * assessment from identical data on Android and iOS.
 *
 * The payload is complete: exercises, sessions (including the explicit
 * [com.ironlog.shared.backup.BackupWorkoutSession.isDeload] context), sets, plan
 * exercises, plan-target snapshots and the readiness side channel
 * ([com.ironlog.shared.backup.BackupPayloadV1.readinessData]). Set types and
 * intentions are deliberately passed through unfiltered; the engine owns those
 * rules.
 */
interface ReadinessProjectionSource {
    /** One consistent snapshot of the durable training graph as a backup payload. */
    suspend fun currentPayload(): BackupPayloadV1

    /** Emits a fresh payload whenever the underlying training graph changes. */
    fun observePayload(): Flow<BackupPayloadV1>
}
