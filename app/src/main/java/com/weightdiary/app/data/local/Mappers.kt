package com.weightdiary.app.data.local

import com.weightdiary.app.domain.model.RecordSource
import com.weightdiary.app.domain.model.WeightRecord
import java.time.Instant

/**
 * 实体 ↔ 领域模型映射。
 *
 * 领域模型用 `Instant`，实体用 epoch millis；这样 Room 不需要 TypeConverter，
 * 而 domain 层又能用上 `java.time`（minSdk 26 原生可用）。
 */
fun WeightRecordEntity.toDomain(): WeightRecord = WeightRecord(
    id = id,
    measuredAt = Instant.ofEpochMilli(measuredAt),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    note = note,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    source = source.toRecordSource(),
)

fun WeightRecord.toEntity(): WeightRecordEntity = WeightRecordEntity(
    id = id,
    measuredAt = measuredAt.toEpochMilli(),
    weightKg = weightKg,
    bodyFatPercent = bodyFatPercent,
    note = note,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    source = source.name,
)

/**
 * 存的是枚举名，读回来时认不出就当手动记录 ——
 * 宁可把一条陌生的来源降级成 MANUAL，也不要因为一个字符串让整个界面崩掉。
 */
fun String.toRecordSource(): RecordSource =
    RecordSource.entries.firstOrNull { it.name == this } ?: RecordSource.MANUAL
