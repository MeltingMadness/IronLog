package com.ironlog.app.domain.repository

import com.ironlog.shared.readinessdata.ReadinessCheckIn
import com.ironlog.shared.readinessdata.ReadinessData
import com.ironlog.shared.readinessdata.SetIntention
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Persistence boundary for the readiness side channel (dated check-ins and
 * per-set intentions) on Android.
 *
 * The canonical document type is [ReadinessData] from `com.ironlog.shared.readinessdata`.
 * The repository never invents a value: a set without an explicit record is
 * [SetIntention.UNKNOWN], and a day without an explicit check-in simply has no row.
 *
 * All comments use the shared vocabulary; no Android-specific model is introduced here.
 */
interface ReadinessRepository {
    /** Streams the whole, validated document. Emits an empty document when nothing is stored. */
    fun observeReadinessData(): Flow<ReadinessData>

    /** Current, validated document. */
    suspend fun getReadinessData(): ReadinessData

    /** Streams check-ins keyed by their explicit local date. */
    fun observeCheckIns(): Flow<Map<LocalDate, ReadinessCheckIn>>

    /** Streams set intentions keyed by the durable set id. */
    fun observeSetIntentions(): Flow<Map<Long, SetIntention>>

    /**
     * Inserts or replaces the check-in for its own [ReadinessCheckIn.localDate].
     *
     * A check-in must carry at least one answer ([ReadinessCheckIn.hasAnyAnswer]);
     * an entirely empty entry is rejected so "no answer" is never stored as a row.
     *
     * Editing an existing day keeps its original
     * [ReadinessCheckIn.recordedAtEpochMillis] when the caller does not send one and
     * advances [ReadinessCheckIn.updatedAtEpochMillis] instead, so a later edit never
     * rewrites the fact that the day was first recorded earlier.
     */
    suspend fun upsertCheckIn(checkIn: ReadinessCheckIn)

    /**
     * Removes the check-in for [localDate]. Removing a missing day is a no-op.
     *
     * This is the explicit "delete" action. Merely dismissing the check-in dialog
     * ("skip") must not call this, because it would erase answers the user already
     * gave for that day; skipping is a UI-only action that persists nothing.
     */
    suspend fun deleteCheckIn(localDate: LocalDate)

    /**
     * Records why a logged set ended the way it did.
     *
     * [SetIntention.UNKNOWN] carries no information and therefore clears the record
     * instead of storing a placeholder.
     *
     * The set must still exist when the write is applied; the existence check runs
     * inside the same transaction to avoid orphaning the record on a delete race.
     * A missing set throws [IllegalArgumentException] rather than silently reporting
     * success, so a caller can never believe an intention was stored when it was not.
     */
    suspend fun setSetIntention(setId: Long, intention: SetIntention, note: String = "")

    /** Removes the record for [setId]; the set then resolves to [SetIntention.UNKNOWN]. */
    suspend fun clearSetIntention(setId: Long)

    /**
     * Drops intention records for deleted sets. Runs in the same transaction as the
     * delete so no orphaned record can survive.
     */
    suspend fun pruneSetIntentions(setIds: Collection<Long>)

    /**
     * Replaces the whole document with already-validated, imported data.
     *
     * Used by the backup import: the training graph is replaced wholesale, so the
     * readiness document is replaced too (never merged by set id). The caller is
     * responsible for running this inside the existing import transaction.
     */
    suspend fun replaceReadinessData(data: ReadinessData)

    /** Clears every check-in and intention (full user-data reset). */
    suspend fun clearReadinessData()
}
