package com.weightdiary.app.domain.model

import java.time.Instant

/**
 * 一次称重记录（领域模型）。
 *
 * 与 [com.weightdiary.app.data.local.WeightRecordEntity] 的区别：这里用 `Instant`，
 * 实体里用 epoch millis，两者通过 mapper 转换，避免 Room 需要 TypeConverter。
 */
data class WeightRecord(
    val id: Long = 0L,
    /** 带时分秒，因此支持一天多条记录 */
    val measuredAt: Instant,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
    val note: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    /**
     * 来源。**这里刻意没有 `externalId`** —— 那是持久层的去重键，
     * domain 不需要知道它（见 `docs/08` §3.3）。
     */
    val source: RecordSource = RecordSource.MANUAL,
)
