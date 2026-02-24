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
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
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
}
