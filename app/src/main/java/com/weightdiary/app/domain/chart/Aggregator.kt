package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.model.WeightRecord
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * 把原始记录聚合成绘图点。
 *
 * 图表**只画体重**。原先它支持按指标（体重 / BMI / 体脂率）取数，那张用来切指标的卡片
 * 已经删掉了，所以这里不再有任何指标换算，也不需要「算不出值的记录要跳过」那套逻辑。
 *
 * 2026-10 之前这里有一条铁律：「当日最低值必须锁定同一条记录」。它存在的理由是
 * **不能让同一天的点来自两次不同称重**（体重取早上的、体脂取晚上的）。只画体重之后
 * 这条约束自动满足 —— 取的就是那条记录本身。
 */
object Aggregator {

    fun aggregate(
        records: List<WeightRecord>,
        range: ChartRange,
        granularity: Granularity,
        zone: ZoneId,
    ): List<ChartPoint> {
        val inRange = records.filter { it.measuredAt >= range.start && it.measuredAt <= range.end }

        return when (granularity) {
            Granularity.RAW -> inRange
                .map { ChartPoint(it.measuredAt, it.weightKg, it.id) }
                .sortedBy { it.time }

            Granularity.DAILY -> inRange
                .groupBy { it.measuredAt.atZone(zone).toLocalDate() }
                .toSortedMap()
                .map { (_, dayRecords) -> dailyPoint(dayRecords) }

            Granularity.MONTHLY -> inRange
                .groupBy {
                    it.measuredAt.atZone(zone).let { z -> YearMonth.of(z.year, z.monthValue) }
                }
                .toSortedMap()
                .map { (month, monthRecords) -> monthlyPoint(month, monthRecords, zone) }
        }
    }

    /** 当日最低体重那条记录。`groupBy` 的分组至少有一个元素，所以 `minBy` 安全。 */
    private fun dailyPoint(dayRecords: List<WeightRecord>): ChartPoint {
        val lowest = dayRecords.minBy { it.weightKg }
        return ChartPoint(lowest.measuredAt, lowest.weightKg, lowest.id)
    }

    /**
     * 月平均值。
     *
     * 点的横坐标取**月中**（15 日中午），不是该月最后一条记录的时间：
     * 当前月才过了几天时（比如 10 月只到 3 号），用最后一条记录会让 10 月的点
     * 紧贴 9 月的点、糊成一团。月中位置也与年视图的 X 轴刻度对齐。
     *
     * 代表记录取该月**最后一条**，只用于气泡定位时的身份标识。
     */
    private fun monthlyPoint(
        month: YearMonth,
        monthRecords: List<WeightRecord>,
        zone: ZoneId,
    ): ChartPoint {
        val avg = monthRecords.map { it.weightKg }.average()
        val representative = monthRecords.maxBy { it.measuredAt }
        return ChartPoint(monthMidpoint(month, zone), avg, representative.id)
    }

    /** 某个月的中点（15 日中午），与年视图 X 轴刻度的取位保持一致 */
    fun monthMidpoint(month: YearMonth, zone: ZoneId): Instant =
        month.atDay(15).atTime(12, 0).atZone(zone).toInstant()
}
