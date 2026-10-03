package com.weightdiary.app.data.repository

import com.weightdiary.app.data.local.WeightDao
import com.weightdiary.app.data.local.WeightRecordEntity
import com.weightdiary.app.data.local.toDomain
import com.weightdiary.app.data.local.toEntity
import com.weightdiary.app.data.prefs.ProfileStore
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

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    suspend fun setHeight(heightCm: Double?) = profileStore.setHeight(heightCm)

    suspend fun setTargetWeight(targetWeightKg: Double?, setAtWeightKg: Double?) =
        profileStore.setTargetWeight(targetWeightKg, setAtWeightKg)
}
