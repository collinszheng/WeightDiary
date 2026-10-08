package com.weightdiary.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    // ─────────────── Health Connect 同步 ───────────────

    @Query("SELECT * FROM weight_records WHERE externalId = :externalId LIMIT 1")
    suspend fun findByExternalId(externalId: String): WeightRecordEntity?

    /** 去重要用的已知外部 id 集合（手动记录的 NULL 不在其中） */
    @Query("SELECT externalId FROM weight_records WHERE externalId IS NOT NULL")
    suspend fun allExternalIds(): List<String>

    /**
     * 批量插入，撞唯一索引就跳过。
     *
     * 同步路径用它而不是 [insertAll]：即使去重算漏了一条，
     * 唯一索引这道兜底也不会让同步整体失败。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(entities: List<WeightRecordEntity>): List<Long>

    /** 「邻近认领」：把外部 id 挂到一条已有的手动记录上，不新增行 */
    @Query(
        "UPDATE weight_records SET externalId = :externalId, updatedAt = :updatedAt " +
            "WHERE id = :id"
    )
    suspend fun attachExternalId(id: Long, externalId: String, updatedAt: Long)

    // ─────────────── 墓碑 ───────────────

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnored(entity: IgnoredExternalIdEntity)

    @Query("SELECT externalId FROM ignored_external_ids")
    suspend fun allIgnoredIds(): List<String>

    @Query("DELETE FROM ignored_external_ids")
    suspend fun clearIgnored()
}
