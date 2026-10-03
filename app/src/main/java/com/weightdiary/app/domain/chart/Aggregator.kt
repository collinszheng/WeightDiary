package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.domain.model.WeightRecord
import java.time.Instant
import java.time.ZoneId

/**
 * 把原始记录聚合成绘图点。
 *
 * 两条铁律：
 * 1. **「当日最低值」必须锁定同一条记录** —— 先按体重选出当天最低的那条，再取它的 BMI / 体脂率。
 *    对三个指标各自取最小值会让同一天的点来自两次不同称重，图上自相矛盾。
 * 2. **算不出值的记录要被跳过**，不能当成 0 —— 没填体脂率、或没设身高算不出 BMI 的情况很常见。
 */
object Aggregator {

    fun aggregate(
        records: List<WeightRecord>,
        range: ChartRange,
        granularity: Granularity,
        metric: Metric,
        heightCm: Double?,
        zone: ZoneId,
    ): List<ChartPoint> {
        val inRange = records.filter { it.measuredAt >= range.start && it.measuredAt <= range.end }

        return when (granularity) {
            Granularity.RAW -> inRange
                .mapNotNull { record ->
                    metric.readFrom(record, heightCm)?.let {
                        ChartPoint(record.measuredAt, it, record.id)
                    }
                }
                .sortedBy { it.time }

            Granularity.DAILY -> inRange
                .groupBy { it.measuredAt.atZone(zone).toLocalDate() }
                .toSortedMap()
                .mapNotNull { (_, dayRecords) -> dailyPoint(dayRecords, metric, heightCm) }

            Granularity.MONTHLY -> inRange
                .groupBy { it.measuredAt.atZone(zone).let { z -> z.year * 100 + z.monthValue } }
                .toSortedMap()
                .mapNotNull { (_, monthRecords) -> monthlyPoint(monthRecords, metric, heightCm) }
        }
    }

    /**
     * 当日最低值。
     *
     * 先按**体重**选出当天最低的那条记录（与当前展示哪个指标无关），再从这个记录上读指标值。
     * 若这条记录恰好没有当前指标的值（比如没填体脂），则往后顺延到当日体重次低、但该指标有值的记录。
     */
    private fun dailyPoint(
        dayRecords: List<WeightRecord>,
        metric: Metric,
        heightCm: Double?,
    ): ChartPoint? =
        dayRecords
            .sortedBy { it.weightKg }
            .firstNotNullOfOrNull { record ->
                metric.readFrom(record, heightCm)?.let {
                    ChartPoint(record.measuredAt, it, record.id)
                }
            }

    /**
     * 月平均值。这里对指标值直接求平均 —— 平均值不需要「锁定同一条记录」，
     * 因为它是聚合量而不是挑出来的样本。
     */
    private fun monthlyPoint(
        monthRecords: List<WeightRecord>,
        metric: Metric,
        heightCm: Double?,
    ): ChartPoint? {
        val valued = monthRecords.mapNotNull { record ->
            metric.readFrom(record, heightCm)?.let { record to it }
        }
        if (valued.isEmpty()) return null
        val avg = valued.map { it.second }.average()
        // 用该月最后一条记录的时间作为代表时间
        val representative = valued.maxByOrNull { it.first.measuredAt }!!.first
        return ChartPoint(representative.measuredAt, avg, representative.id)
    }
}
