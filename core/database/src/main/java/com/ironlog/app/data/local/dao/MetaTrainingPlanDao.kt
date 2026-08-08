package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaPlanSkipEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import com.ironlog.app.domain.util.resolveMetaPlanRotation
import kotlinx.coroutines.flow.Flow

data class MetaTrainingPlanWithItems(
    @Embedded val metaPlan: MetaTrainingPlanEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "metaPlanId"
    )
    val items: List<MetaPlanItemEntity>
)

@Dao
interface MetaTrainingPlanDao {

    @Query("SELECT * FROM meta_training_plans ORDER BY createdAt DESC")
    fun getAllMetaPlans(): Flow<List<MetaTrainingPlanEntity>>

    @Transaction
    @Query("SELECT * FROM meta_training_plans ORDER BY createdAt DESC")
    fun getAllMetaPlansWithItems(): Flow<List<MetaTrainingPlanWithItems>>

    @Query("SELECT * FROM meta_training_plans ORDER BY id ASC")
    suspend fun getAllMetaPlansList(): List<MetaTrainingPlanEntity>

    @Query("SELECT * FROM meta_plan_items ORDER BY id ASC")
    suspend fun getAllMetaPlanItemsList(): List<MetaPlanItemEntity>

    @Query("SELECT * FROM meta_training_plans WHERE id = :id")
    suspend fun getMetaPlanById(id: Long): MetaTrainingPlanEntity?

    @Transaction
    @Query("SELECT * FROM meta_training_plans WHERE id = :id")
    suspend fun getMetaPlanWithItemsById(id: Long): MetaTrainingPlanWithItems?

    @Query("SELECT * FROM meta_plan_items WHERE metaPlanId = :metaPlanId ORDER BY orderIndex ASC")
    suspend fun getItemsForMetaPlan(metaPlanId: Long): List<MetaPlanItemEntity>

    @Query(
        """
        SELECT trainingPlanId, metaPlanId, MAX(eventAt) AS lastEventAt
        FROM (
            SELECT planId AS trainingPlanId, metaPlanId, startTime AS eventAt
            FROM workout_sessions
            WHERE endTime IS NOT NULL AND planId IS NOT NULL AND metaPlanId IS NOT NULL
            UNION ALL
            SELECT trainingPlanId, metaPlanId, skippedAt AS eventAt
            FROM meta_plan_skips
        )
        GROUP BY trainingPlanId, metaPlanId
        """
    )
    fun observeLastRotationEventPerMetaPlanSubPlan(): Flow<List<LastMetaPlanRotationEventRow>>

    @Query(
        """
        SELECT trainingPlanId, metaPlanId, MAX(eventAt) AS lastEventAt
        FROM (
            SELECT planId AS trainingPlanId, metaPlanId, startTime AS eventAt
            FROM workout_sessions
            WHERE endTime IS NOT NULL AND planId IS NOT NULL AND metaPlanId IS NOT NULL
              AND metaPlanId = :metaPlanId
            UNION ALL
            SELECT trainingPlanId, metaPlanId, skippedAt AS eventAt
            FROM meta_plan_skips
            WHERE metaPlanId = :metaPlanId
        )
        GROUP BY trainingPlanId, metaPlanId
        """
    )
    suspend fun getLastRotationEventsForMetaPlan(metaPlanId: Long): List<LastMetaPlanRotationEventRow>

    @Insert
    suspend fun insertMetaPlanSkip(skip: MetaPlanSkipEntity)

    @Query("SELECT * FROM meta_plan_skips ORDER BY id ASC")
    suspend fun getAllMetaPlanSkipsList(): List<MetaPlanSkipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllMetaPlanSkips(skips: List<MetaPlanSkipEntity>)

    @Query("DELETE FROM meta_plan_skips")
    suspend fun deleteAllMetaPlanSkips()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetaPlan(plan: MetaTrainingPlanEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllMetaPlans(plans: List<MetaTrainingPlanEntity>)

    @Update
    suspend fun updateMetaPlan(plan: MetaTrainingPlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MetaPlanItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAllItems(items: List<MetaPlanItemEntity>)

    @Query("DELETE FROM meta_plan_items WHERE metaPlanId = :metaPlanId")
    suspend fun deleteItemsForMetaPlan(metaPlanId: Long)

    @Query("DELETE FROM meta_plan_items")
    suspend fun deleteAllMetaPlanItems()

    @Query("DELETE FROM meta_training_plans WHERE id = :metaPlanId")
    suspend fun deleteMetaPlan(metaPlanId: Long)

    @Query("UPDATE workout_sessions SET metaPlanId = NULL WHERE metaPlanId = :metaPlanId")
    suspend fun detachSessionsFromMetaPlan(metaPlanId: Long)

    /**
     * Atomically detaches referencing workout sessions before deleting the meta-plan.
     * Sessions keep their own data and simply lose the meta-plan reference, which keeps
     * the database and backups valid even for an active session.
     */
    @Transaction
    suspend fun deleteMetaPlanAndDetachSessions(metaPlanId: Long) {
        detachSessionsFromMetaPlan(metaPlanId)
        deleteMetaPlan(metaPlanId)
    }

    @Query("DELETE FROM meta_training_plans")
    suspend fun deleteAllMetaPlans()

    @Transaction
    suspend fun replaceMetaPlanAndItems(
        plan: MetaTrainingPlanEntity,
        items: List<MetaPlanItemEntity>
    ): Long {
        val metaPlanId = if (plan.id == 0L) {
            insertMetaPlan(plan)
        } else {
            updateMetaPlan(plan)
            plan.id
        }

        deleteItemsForMetaPlan(metaPlanId)
        val normalizedItems = items.mapIndexed { index, item ->
            item.copy(
                id = 0L,
                metaPlanId = metaPlanId,
                orderIndex = index
            )
        }
        insertItems(normalizedItems)

        return metaPlanId
    }

    /**
     * Persists a skip only when the expected sub-plan is still the current rotation
     * target. All reads and the insert run inside one Room transaction so stale UI
     * state and double taps cannot produce a second effective event.
     */
    @Transaction
    suspend fun skipCurrentSubPlanIfCurrent(
        metaPlanId: Long,
        expectedTrainingPlanId: Long,
        skippedAt: Long
    ): Boolean {
        val orderedIds = getItemsForMetaPlan(metaPlanId)
            .sortedBy { it.orderIndex }
            .map { it.trainingPlanId }
        if (orderedIds.size < 2) return false

        val anchors = getLastRotationEventsForMetaPlan(metaPlanId)
            .associate { it.trainingPlanId to it.lastEventAt }
        val current = resolveMetaPlanRotation(orderedIds, anchors).firstOrNull()
        if (current != expectedTrainingPlanId) return false

        val greatestAnchor = anchors.values.maxOrNull()
        val effectiveSkippedAt = if (greatestAnchor != null && skippedAt <= greatestAnchor) {
            greatestAnchor + 1L
        } else {
            skippedAt
        }

        insertMetaPlanSkip(
            MetaPlanSkipEntity(
                metaPlanId = metaPlanId,
                trainingPlanId = expectedTrainingPlanId,
                skippedAt = effectiveSkippedAt
            )
        )
        return true
    }
}
