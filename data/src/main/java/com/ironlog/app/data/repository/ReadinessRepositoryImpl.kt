package com.ironlog.app.data.repository

import com.ironlog.app.data.db.TransactionRunner
import com.ironlog.app.data.local.dao.ReadinessDataDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ReadinessDataEntity
import com.ironlog.app.domain.repository.ReadinessRepository
import com.ironlog.shared.readinessdata.ReadinessCheckIn
import com.ironlog.shared.readinessdata.ReadinessData
import com.ironlog.shared.readinessdata.ReadinessDataCodec
import com.ironlog.shared.readinessdata.SetIntention
import com.ironlog.shared.readinessdata.SetIntentionRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * Room-backed [ReadinessRepository] over the single-row [ReadinessDataEntity].
 *
 * The stored payload is the canonical JSON document produced by
 * [ReadinessDataCodec], so every read and write is validated with the shared
 * validator.
 *
 * Every read-modify-write runs inside the shared [TransactionRunner] (the same
 * Room database as the backup import and the workout repositories). A plain
 * in-process mutex would not be enough: the backup import writes the same row
 * through a different object and would otherwise race with a check-in and lose
 * one of the two values.
 *
 * Reads are fail-closed: a corrupted or newer-than-supported row raises instead
 * of silently degrading to "no data", which would look like an empty store and
 * could overwrite the user's check-ins on the next write.
 */
class ReadinessRepositoryImpl(
    private val transactionRunner: TransactionRunner,
    private val readinessDataDao: ReadinessDataDao,
    private val workoutSetDao: WorkoutSetDao,
    /**
     * Wall-clock source for the write-side metadata. Injectable so tests can pin
     * "now" instead of observing real time; the app uses the system clock.
     */
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() }
) : ReadinessRepository {

    override fun observeReadinessData(): Flow<ReadinessData> =
        readinessDataDao.observePayload().map { payload -> payload.toReadinessData() }

    override suspend fun getReadinessData(): ReadinessData =
        readinessDataDao.getPayload().toReadinessData()

    override fun observeCheckIns(): Flow<Map<LocalDate, ReadinessCheckIn>> =
        observeReadinessData().map { data -> data.checkIns.associateBy { it.localDate } }

    override fun observeSetIntentions(): Flow<Map<Long, SetIntention>> =
        observeReadinessData().map { data ->
            data.setIntentions.associate { it.setId to it.intention }
        }

    override suspend fun upsertCheckIn(checkIn: ReadinessCheckIn) {
        require(checkIn.hasAnyAnswer()) {
            "Refusing to store an empty check-in for ${checkIn.localDate}"
        }
        transactionRunner.runInTransaction {
            val current = readinessDataDao.getPayload().toReadinessData()
            val existing = current.checkIns.firstOrNull { it.localDate == checkIn.localDate }
            // An edit without explicit metadata must not look like a brand-new record:
            // keep the original record time and stamp the edit time on top of it.
            val normalized = checkIn.copy(
                recordedAtEpochMillis = checkIn.recordedAtEpochMillis
                    ?: existing?.recordedAtEpochMillis
                    ?: nowEpochMillis().coerceAtLeast(0L),
                updatedAtEpochMillis = if (existing == null) {
                    checkIn.updatedAtEpochMillis
                } else {
                    nowEpochMillis().coerceAtLeast(existing.recordedAtEpochMillis ?: 0L)
                }
            )
            store(
                current.copy(
                    checkIns = (current.checkIns.filterNot { it.localDate == checkIn.localDate } + normalized)
                        .sortedBy { it.localDate }
                )
            )
        }
    }

    override suspend fun deleteCheckIn(localDate: LocalDate) {
        mutate { data ->
            data.copy(checkIns = data.checkIns.filterNot { it.localDate == localDate })
        }
    }

    override suspend fun setSetIntention(setId: Long, intention: SetIntention, note: String) {
        require(setId > 0L) { "Set intention requires a positive set id, was $setId" }
        transactionRunner.runInTransaction {
            // Checked inside the same transaction as the write: a concurrently deleted
            // set must never leave an intention record behind. A missing set fails loudly
            // instead of reporting a success the UI would trust.
            require(workoutSetDao.getSetById(setId) != null) { "Workout set $setId does not exist" }
            val current = readinessDataDao.getPayload().toReadinessData()
            val updated = if (intention == SetIntention.UNKNOWN) {
                current.copy(setIntentions = current.setIntentions.filterNot { it.setId == setId })
            } else {
                val record = SetIntentionRecord(setId = setId, intention = intention, note = note)
                val others = current.setIntentions.filterNot { it.setId == setId }
                current.copy(setIntentions = (others + record).sortedBy { it.setId })
            }
            if (updated == current) return@runInTransaction
            store(updated)
        }
    }

    override suspend fun clearSetIntention(setId: Long) {
        mutate { data ->
            data.copy(setIntentions = data.setIntentions.filterNot { it.setId == setId })
        }
    }

    override suspend fun pruneSetIntentions(setIds: Collection<Long>) {
        if (setIds.isEmpty()) return
        val removed = setIds.toSet()
        mutate { data ->
            data.copy(setIntentions = data.setIntentions.filterNot { it.setId in removed })
        }
    }

    override suspend fun replaceReadinessData(data: ReadinessData) {
        // Encoded/validated before the transaction so an invalid document fails
        // without taking a write lock.
        val payload = ReadinessDataCodec.encode(data)
        transactionRunner.runInTransaction {
            readinessDataDao.upsert(ReadinessDataEntity(payload = payload))
        }
    }

    override suspend fun clearReadinessData() {
        transactionRunner.runInTransaction {
            readinessDataDao.deleteAll()
        }
    }

    private suspend fun mutate(transform: (ReadinessData) -> ReadinessData) {
        transactionRunner.runInTransaction {
            val current = readinessDataDao.getPayload().toReadinessData()
            val updated = transform(current)
            // A clear/prune for a set without a record must not rewrite (and re-emit)
            // the document; this keeps the atomic set paths free of spurious writes.
            if (updated != current) store(updated)
        }
    }

    /** Validates and persists [data]; fails closed before anything is written. */
    private suspend fun store(data: ReadinessData) {
        val payload = ReadinessDataCodec.encode(data)
        readinessDataDao.upsert(ReadinessDataEntity(payload = payload))
    }

    private fun String?.toReadinessData(): ReadinessData =
        if (this == null) ReadinessData() else ReadinessDataCodec.decode(this)
}
