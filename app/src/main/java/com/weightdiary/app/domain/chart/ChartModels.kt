package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.domain.model.WeightRecord
import java.time.Instant

/** 图表顶部的时间跨度 Tab */
enum class ChartTab {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL,
}

/**
 * 数据点粒度。
 *
 * - [RAW]：直接用原始记录，不聚合（「日」视图）
 * - [DAILY]：每天一个点，取**当日最低值**那条记录
 * - [MONTHLY]：每月一个点，取该月**平均值**
 */
enum class Granularity {
    RAW,
    DAILY,
    MONTHLY,
}

/** 图表可视区间。`start` 与 `end` 都是**闭区间**（`end` 为当天 23:59:59.999）。 */
data class ChartRange(
    val start: Instant,
    val end: Instant,
) {
    val daySpan: Long
        get() = ((end.toEpochMilli() - start.toEpochMilli()) / 86_400_000L) + 1
}

/**
 * 一个绘图点。
 *
 * [sourceRecordId] 很关键：切换指标时「当日最低值」必须锁定**同一条记录**，
 * 否则同一天会出现「最低体重」和「最低体脂」来自两次不同称重的错位（见技术设计 §4.3）。
 */
data class ChartPoint(
    val time: Instant,
    val value: Double,
    val sourceRecordId: Long,
)

/**
 * 取某个指标在一条记录上的值。
 *
 * 返回 `null` 表示这条记录**参与不了**这个指标 —— 例如没填体脂率记录、或没设身高算不出 BMI。
 * 聚合时这类记录要被跳过，而不是当成 0。
 */
fun Metric.readFrom(record: WeightRecord, heightCm: Double?): Double? = when (this) {
    Metric.WEIGHT -> record.weightKg
    Metric.BMI -> heightCm
        ?.takeIf { it > 0.0 }
        ?.let { com.weightdiary.app.domain.bmi.BmiCalculator.calculate(record.weightKg, it) }

    Metric.BODY_FAT -> record.bodyFatPercent
}

/** 图表 Y 轴的标题文案 key。UI 层拿去查 strings.xml。 */
val Metric.usesDecimalAxis: Boolean
    get() = this != Metric.BMI
