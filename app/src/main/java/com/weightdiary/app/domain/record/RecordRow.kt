package com.weightdiary.app.domain.record

import com.weightdiary.app.domain.model.WeightRecord
import java.time.Instant

/**
 * 列表里的一行记录。
 *
 * [deltaKg] 是相对**时间上更早的那一条**的差值，不是相对当前体重。
 * 首页卡片上的变化量用的是「最新 − 次新」，与这里的口径一致。
 */
data class RecordRow(
    val id: Long,
    val measuredAt: Instant,
    val weightKg: Double,
    val bodyFatPercent: Double?,
    val note: String?,
    val deltaKg: Double?,
)

object RecordRows {

    /**
     * 把记录切成列表行。
     *
     * **要求入参已按 `measuredAt` 倒序**（`WeightDao.observeAll()` 的 `ORDER BY measuredAt DESC`）——
     * 所以第 i 行的「上一条」就是 i+1，而不是 i−1。
     */
    fun build(records: List<WeightRecord>): List<RecordRow> =
        records.mapIndexed { index, record ->
            val earlier = records.getOrNull(index + 1)
            RecordRow(
                id = record.id,
                measuredAt = record.measuredAt,
                weightKg = record.weightKg,
                bodyFatPercent = record.bodyFatPercent,
                note = record.note,
                deltaKg = earlier?.let { record.weightKg - it.weightKg },
            )
        }
}
