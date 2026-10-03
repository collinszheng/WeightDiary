package com.weightdiary.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    @Query("SELECT * FROM weight_records ORDER BY measuredAt DESC")
    fun observeAll(): Flow<List<WeightRecordEntity>>

    @Query(
        "SELECT * FROM weight_records WHERE measuredAt BETWEEN :from AND :to " +
            "ORDER BY measuredAt ASC"
    )
    fun observeBetween(from: Long, to: Long): Flow<List<WeightRecordEntity>>

    @Query("SELECT * FROM weight_records ORDER BY measuredAt DESC LIMIT 1")
    fun observeLatest(): Flow<WeightRecordEntity?>

    @Query("SELECT * FROM weight_records ORDER BY measuredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WeightRecordEntity>>

    @Query("SELECT COUNT(*) FROM weight_records")
    fun observeCount(): Flow<Int>

    /** 用于「总」视图的范围起点 */
    @Query("SELECT MIN(measuredAt) FROM weight_records")
    fun observeEarliestTime(): Flow<Long?>

    @Query("SELECT * FROM weight_records WHERE id = :id")
    suspend fun findById(id: Long): WeightRecordEntity?

    @Insert
    suspend fun insert(entity: WeightRecordEntity): Long

    @Update
    suspend fun update(entity: WeightRecordEntity)

    @Query("DELETE FROM weight_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert
    suspend fun insertAll(entities: List<WeightRecordEntity>)

    @Query("DELETE FROM weight_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM weight_records ORDER BY measuredAt ASC")
    suspend fun getAllOnce(): List<WeightRecordEntity>
}
