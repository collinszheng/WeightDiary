package com.weightdiary.app.domain.chart

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 把「Tab + 基准日期」解析成具体的可视区间。
 *
 * 区间是**自然周期**，不是滚动窗口：
 * - 日 = 当天
 * - 周 = 本周（周一 00:00 ～ 周日 23:59:59.999）
 * - 月 = 本月（1 日 ～ 月末）
 * - 年 = 本年（1 月 1 日 ～ 12 月 31 日）
 * - 总 = 首条记录 ～ 今天
 *
 * 用自然周期而不是「近 7 天 / 近 30 天」，是因为左右箭头随之变得很自然
 * （上一周 / 上一月 / 上一年），日历对齐也更符合直觉。
 *
 * 代价是**当前这一周 / 月 / 年里还没到的日子会是空的**，折线画到今天为止。
 * 这是刻意接受的：刻度固定、每周的图长得一样，比「只画到今天」更好读。
 */
object RangeResolver {

    /** 把某一天的最后一毫秒作为闭区间右端 */
    fun endOfDay(date: LocalDate, zone: ZoneId): Instant =
        date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)

    fun startOfDay(date: LocalDate, zone: ZoneId): Instant =
        date.atStartOfDay(zone).toInstant()

    /** 本周一 */
    fun weekStart(anchor: LocalDate): LocalDate =
        anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 本月的第一天 */
    fun monthStart(anchor: LocalDate): LocalDate = anchor.withDayOfMonth(1)

    /** 本年的第一天 */
    fun yearStart(anchor: LocalDate): LocalDate = anchor.withDayOfYear(1)

    /**
     * @param earliestDataDate 首条记录的日期，「总」视图从它开始；没有数据时退化为当天
     */
    fun resolve(
        tab: ChartTab,
        anchor: LocalDate,
        earliestDataDate: LocalDate?,
        zone: ZoneId,
    ): ChartRange {
        val (startDate, endDate) = when (tab) {
            ChartTab.DAY -> anchor to anchor
            ChartTab.WEEK -> weekStart(anchor) to weekStart(anchor).plusDays(6)
            ChartTab.MONTH -> monthStart(anchor) to monthStart(anchor).plusMonths(1).minusDays(1)
            ChartTab.YEAR -> yearStart(anchor) to yearStart(anchor).plusYears(1).minusDays(1)
            ChartTab.ALL -> (earliestDataDate ?: anchor) to anchor
        }
        return ChartRange(startOfDay(startDate, zone), endOfDay(endDate, zone))
    }

    /** 该 Tab 下数据点的粒度 */
    fun granularityOf(tab: ChartTab, range: ChartRange): Granularity = when (tab) {
        ChartTab.DAY -> Granularity.RAW
        ChartTab.WEEK, ChartTab.MONTH -> Granularity.DAILY
        ChartTab.YEAR -> Granularity.MONTHLY
        ChartTab.ALL ->
            if (range.daySpan > ALL_MONTHLY_THRESHOLD_DAYS) Granularity.MONTHLY else Granularity.DAILY
    }

    /** 「总」视图跨度超过这个天数就按月聚合，否则按天 */
    const val ALL_MONTHLY_THRESHOLD_DAYS = 120L

    /**
     * 左右箭头：平移基准日期。整周 / 整月 / 整年地跳。
     *
     * 「总」视图不参与平移 —— 它的区间由数据本身决定，没有「上一段」的概念。
     */
    fun shiftAnchor(tab: ChartTab, anchor: LocalDate, steps: Int): LocalDate = when (tab) {
        ChartTab.DAY -> anchor.plusDays(steps.toLong())
        ChartTab.WEEK -> anchor.plusWeeks(steps.toLong())
        // 先归到月初 / 年初再加减，否则 31 号往后跳会漂移（1/31 → 2/28 → 3/28）
        ChartTab.MONTH -> monthStart(anchor).plusMonths(steps.toLong())
        ChartTab.YEAR -> yearStart(anchor).plusYears(steps.toLong())
        ChartTab.ALL -> anchor
    }

    /**
     * 右箭头是否可用。判据是**平移后的区间起点不能晚于今天** ——
     * 否则就已经跳到未来那一段了。
     */
    fun canShiftForward(
        tab: ChartTab,
        anchor: LocalDate,
        today: LocalDate,
        earliestDataDate: LocalDate?,
        zone: ZoneId,
    ): Boolean {
        if (tab == ChartTab.ALL) return false
        val next = shiftAnchor(tab, anchor, 1)
        return resolve(tab, next, earliestDataDate, zone).start <= endOfDay(today, zone)
    }

    /** 左箭头是否可用。当前区间之前还有数据才让往回看，否则全是空白。 */
    fun canShiftBackward(
        tab: ChartTab,
        anchor: LocalDate,
        earliestDataDate: LocalDate?,
        zone: ZoneId,
    ): Boolean {
        val earliest = earliestDataDate ?: return false
        return resolve(tab, anchor, earliest, zone).start > startOfDay(earliest, zone)
    }
}
