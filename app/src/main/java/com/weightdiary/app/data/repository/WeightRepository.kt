package com.weightdiary.app.data.repository

import com.weightdiary.app.data.local.WeightDao
import com.weightdiary.app.data.local.WeightRecordEntity
import com.weightdiary.app.data.local.toDomain
import com.weightdiary.app.data.local.toEntity
import com.weightdiary.app.data.prefs.ProfileStore
import com.weightdiary.app.domain.record.RecordCsv
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.model.UserProfile
import com.weightdiary.app.domain.model.WeightRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * 记录与档案的统一出口。UI 层只跟它打交道，不直接碰 Room / DataStore。
 */
class WeightRepository(
    private val dao: WeightDao,
    private val profileStore: ProfileStore,
) {

    val records: Flow<List<WeightRecord>> =
        dao.observeAll().map { list -> list.map(WeightRecordEntity::toDomain) }

    val latest: Flow<WeightRecord?> =
        dao.observeLatest().map { it?.toDomain() }

    val count: Flow<Int> = dao.observeCount()

    /** 「总」视图的范围起点 */
    val earliestMeasuredAt: Flow<Instant?> =
        dao.observeEarliestTime().map { it?.let(Instant::ofEpochMilli) }

    val profile: Flow<UserProfile> = profileStore.profile

    fun recent(limit: Int): Flow<List<WeightRecord>> =
        dao.observeRecent(limit).map { list -> list.map(WeightRecordEntity::toDomain) }

    fun between(from: Instant, to: Instant): Flow<List<WeightRecord>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli())
            .map { list -> list.map(WeightRecordEntity::toDomain) }

    suspend fun findById(id: Long): WeightRecord? = dao.findById(id)?.toDomain()

    /** @return 新记录的 id */
    suspend fun add(
        weightKg: Double,
        measuredAt: Instant,
        bodyFatPercent: Double? = null,
        note: String? = null,
    ): Long {
        val now = Instant.now()
        return dao.insert(
            WeightRecordEntity(
                measuredAt = measuredAt.toEpochMilli(),
                weightKg = weightKg,
                bodyFatPercent = bodyFatPercent,
                note = note,
                createdAt = now.toEpochMilli(),
                updatedAt = now.toEpochMilli(),
            )
        )
    }

    suspend fun update(record: WeightRecord) {
        dao.update(record.copy(updatedAt = Instant.now()).toEntity())
    }

    /**
     * 编辑一条已存在的记录。`createdAt` 保持不变。
     *
     * @return 是否真的改到了（记录可能已被别处删掉）
     */
    suspend fun updateFields(
        id: Long,
        weightKg: Double,
        measuredAt: Instant,
        bodyFatPercent: Double?,
        note: String?,
    ): Boolean {
        val existing = dao.findById(id) ?: return false
        dao.update(
            existing.copy(
                weightKg = weightKg,
                measuredAt = measuredAt.toEpochMilli(),
                bodyFatPercent = bodyFatPercent,
                note = note,
                updatedAt = Instant.now().toEpochMilli(),
            )
        )
        return true
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    /**
     * 从备份导入。**id 交给 Room 重新分配**，不沿用文件里的 id ——
     * 导入到一个已有数据的库里时，沿用旧 id 会撞上现有记录。
     *
     * @return 实际写入的条数
     */
    suspend fun importRows(rows: List<RecordCsv.Row>): Int {
        if (rows.isEmpty()) return 0
        val now = Instant.now().toEpochMilli()
        dao.insertAll(
            rows.map { row ->
                WeightRecordEntity(
                    measuredAt = row.measuredAt.toEpochMilli(),
                    weightKg = row.weightKg,
                    bodyFatPercent = row.bodyFatPercent,
                    note = row.note,
                    // 原始创建时间已经无从考证，统一记成导入时刻
                    createdAt = now,
                    updatedAt = now,
                )
            }
        )
        return rows.size
    }

    /**
     * 取一份完整快照用于导出。
     *
     * 不用 UI 状态里的 allRecords：那是 RecordRow，丢了 createdAt / updatedAt。
     * 导出的应当是原始记录。
     */
    suspend fun snapshot(): List<WeightRecord> = dao.getAllOnce().map(WeightRecordEntity::toDomain)

    /** 清空所有记录。档案（身高 / 目标 / 标准）不动 */
    suspend fun clearAll() {
        dao.deleteAll()
    }

    suspend fun setHeight(heightCm: Double?) = profileStore.setHeight(heightCm)

    suspend fun setTargetWeight(targetWeightKg: Double?, setAtWeightKg: Double?) =
        profileStore.setTargetWeight(targetWeightKg, setAtWeightKg)

    suspend fun setBmiStandard(standard: BmiStandard) = profileStore.setBmiStandard(standard)

    suspend fun setOnboardingCompleted(completed: Boolean) =
        profileStore.setOnboardingCompleted(completed)
}
