package com.ironlog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonalRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PersonalRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(records: List<PersonalRecordEntity>)

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId")
    fun getRecordsForExercise(exerciseId: Long): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records WHERE exerciseId = :exerciseId AND type = :type")
    suspend fun getRecord(exerciseId: Long, type: String): PersonalRecordEntity?

    @Query("SELECT * FROM personal_records WHERE exerciseId IN (:exerciseIds) ORDER BY achievedAt DESC")
    suspend fun getRecordsForExercisesList(exerciseIds: List<Long>): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_records ORDER BY achievedAt DESC LIMIT :limit")
    fun getRecentRecords(limit: Int): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records ORDER BY achievedAt DESC LIMIT :limit")
    suspend fun getRecentRecordsList(limit: Int): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_records ORDER BY id ASC")
    suspend fun getAllRecordsList(): List<PersonalRecordEntity>

    @Query("DELETE FROM personal_records")
    suspend fun deleteAll()

    /**
     * Removes a single (exerciseId, type) record, e.g. when a workout history deletion leaves
     * no remaining sets to justify the record (see Bug 7: orphaned PRs after session deletion).
     */
    @Query("DELETE FROM personal_records WHERE exerciseId = :exerciseId AND type = :type")
    suspend fun deleteRecord(exerciseId: Long, type: String)
}
