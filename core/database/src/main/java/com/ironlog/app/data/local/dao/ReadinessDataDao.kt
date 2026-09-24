package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ironlog.app.data.local.entity.ReadinessDataEntity
import kotlinx.coroutines.flow.Flow

/**
 * Access to the single-row [ReadinessDataEntity].
 *
 * The DAO stores and returns the raw JSON; parsing/validating it is the
 * adapter's job (`ReadinessRepositoryImpl`). A missing row yields `null`, which
 * the adapter interprets as the empty canonical document.
 */
@Dao
interface ReadinessDataDao {
    @Query("SELECT payload FROM readiness_data WHERE id = ${ReadinessDataEntity.SINGLETON_ID}")
    fun observePayload(): Flow<String?>

    @Query("SELECT payload FROM readiness_data WHERE id = ${ReadinessDataEntity.SINGLETON_ID}")
    suspend fun getPayload(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReadinessDataEntity)

    @Query("DELETE FROM readiness_data")
    suspend fun deleteAll()
}
