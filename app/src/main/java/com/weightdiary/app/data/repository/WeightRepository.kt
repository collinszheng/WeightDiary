package com.weightdiary.app.data.repository

import androidx.room.withTransaction
import com.weightdiary.app.data.local.IgnoredExternalIdEntity
import com.weightdiary.app.data.local.WeightDatabase
import com.weightdiary.app.data.local.WeightRecordEntity
import com.weightdiary.app.data.local.toDomain
import com.weightdiary.app.data.local.toEntity
import com.weightdiary.app.data.local.toRecordSource
import com.weightdiary.app.data.prefs.ProfileStore
import com.weightdiary.app.data.prefs.SyncStore
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.model.RecordSource
import com.weightdiary.app.domain.model.UserProfile
import com.weightdiary.app.domain.model.WeightRecord
import com.weightdiary.app.domain.record.RecordCsv
import com.weightdiary.app.domain.sync.ExistingRecord
import com.weightdiary.app.domain.sync.SyncPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * 记录与档案的统一出口。UI 层只跟它打交道，不直接碰 Room / DataStore。
 */
class WeightRepository(
    database: WeightDatabase,
    private val profileStore: ProfileStore,
    private val syncStore: SyncStore,
) {

    private val database = database
    private val dao = database.weightDao()

    val records: Flow<List<WeightRecord>> =
        dao.observeAll().map { list -> list.map(WeightRecordEntity::toDomain) }

    val latest: Flow<WeightRecord?> =
        dao.observeLatest().map { it?.toDomain() }

    val count: Flow<Int> = dao.observeCount()

    /** 「总」视图的范围起点 */
    val earliestMeasuredAt: Flow<Instant?> =
        dao.observeEarliestTime().map { it?.let(Instant::ofEpochMilli) }

    val profile: Flow<UserProfile> = profileStore.profile

    /** 上次同步时刻。设置页要显示它（`docs/08` §6.3） */
    val lastSyncAt: Flow<Instant?> = syncStore.lastSyncAt

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
                source = RecordSource.MANUAL.name,
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

    /**
     * 删除一条记录。
     *
     * 如果它是**同步来的**，同时记一条墓碑：否则下次同步会把它原样拉回来，
     * 用户会看到自己删掉的记录复活（`docs/06` §7.3）。
     *
     * 两条写在一个事务里 —— 只删了记录却没记上墓碑，是最坏的结果。
     */
    suspend fun delete(id: Long) {
        database.withTransaction {
            val existing = dao.findById(id)
            val externalId = existing?.externalId
            if (externalId != null) {
                dao.insertIgnored(IgnoredExternalIdEntity(externalId, Instant.now().toEpochMilli()))
            }
            dao.deleteById(id)
        }
    }

    /**
     * 从备份导入。**id 交给 Room 重新分配**，不沿用文件里的 id ——
     * 导入到一个已有数据的库里时，沿用旧 id 会撞上现有记录。
     *
     * 导入回来的行**一律算手动记录**（`source = MANUAL`、没有 `externalId`）：
     * CSV 里没有外部 id，去重上它等同于手动记录（`docs/08` §3.1）。
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
                    source = RecordSource.MANUAL.name,
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

    /**
     * 清空所有记录。档案（身高 / 目标 / 标准）不动。
     *
     * **墓碑和同步水位线一并清掉**：清空的语义是「我要重来」，
     * 留着墓碑会让下次同步静默地什么都不拉，用户只会觉得同步坏了（`docs/08` §10）。
     */
    suspend fun clearAll() {
        database.withTransaction {
            dao.deleteAll()
            dao.clearIgnored()
        }
        syncStore.clear()
    }

    // ─────────────── 同步用的读写口 ───────────────

    /** 同步用的「已有记录」视图。只带 SyncPlanner 用得上的字段 */
    suspend fun existingForSync(): List<ExistingRecord> =
        dao.getAllOnce().map {
            ExistingRecord(
                id = it.id,
                measuredAt = Instant.ofEpochMilli(it.measuredAt),
                weightKg = it.weightKg,
                externalId = it.externalId,
                source = it.source.toRecordSource(),
            )
        }

    suspend fun ignoredExternalIds(): Set<String> = dao.allIgnoredIds().toSet()

    /**
     * 落地一份同步计划。**整批一个事务** —— 只插了一半的话，
     * 下次同步虽然能补齐，但那期间用户会看到残缺的数据。
     *
     * @return 真正新增的行数（`insertAllIgnore` 对被唯一索引挡下的行返回 -1）
     */
    suspend fun applySyncPlan(plan: SyncPlan): Int = database.withTransaction {
        val now = Instant.now().toEpochMilli()

        plan.claims.forEach { claim ->
            dao.attachExternalId(claim.id, claim.externalId, now)
        }

        if (plan.inserts.isEmpty()) {
            return@withTransaction 0
        }

        val rows = plan.inserts.map { measurement ->
            WeightRecordEntity(
                measuredAt = measurement.measuredAt.toEpochMilli(),
                weightKg = measurement.weightKg,
                bodyFatPercent = measurement.bodyFatPercent,
                // 同步来的记录没有备注 —— 备注是用户自己的话，不该被同步内容覆盖或编造
                note = null,
                createdAt = now,
                updatedAt = now,
                source = RecordSource.HEALTH_CONNECT.name,
                externalId = measurement.externalId,
            )
        }
        dao.insertAllIgnore(rows).count { it != -1L }
    }

    suspend fun setHeight(heightCm: Double?) = profileStore.setHeight(heightCm)

    suspend fun setTargetWeight(targetWeightKg: Double?, setAtWeightKg: Double?) =
        profileStore.setTargetWeight(targetWeightKg, setAtWeightKg)

    suspend fun setBmiStandard(standard: BmiStandard) = profileStore.setBmiStandard(standard)

    suspend fun setOnboardingCompleted(completed: Boolean) =
        profileStore.setOnboardingCompleted(completed)
}
