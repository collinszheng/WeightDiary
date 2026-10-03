package com.weightdiary.app.domain.chart

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 把「Tab + 基准日期」解析成具体的可视区间。
 *
 * 区间一律是**滚动窗口**（近 7 天 / 近 30 天 / 近 12 个月），不是自然周 / 自然月。
 * 好处是任何位置都能取到完整窗口，不会在月末出现半截区间。
 * 见 [docs/03-技术设计.md §4.3]。
 */
object RangeResolver {

    const val WEEK_DAYS = 7L
    const val MONTH_DAYS = 30L
    const val YEAR_MONTHS = 12L

    /** 「总」视图跨度超过这个天数就按月聚合，否则按天 */
    const val ALL_MONTHLY_THRESHOLD_DAYS = 120L

    /** 把某一天的最后一毫秒作为闭区间右端 */
    fun endOfDay(date: LocalDate, zone: ZoneId): Instant =
        date.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1)

    fun startOfDay(date: LocalDate, zone: ZoneId): Instant =
        date.atStartOfDay(zone).toInstant()

    /**
     * @param earliestDataDate 首条记录的日期，「总」视图从它开始；没有数据时退化为当天
     */
    fun resolve(
        tab: ChartTab,
        anchor: LocalDate,
        earliestDataDate: LocalDate?,
        zone: ZoneId,
    ): ChartRange {
        val end = endOfDay(anchor, zone)
        val startDate = when (tab) {
            ChartTab.DAY -> anchor
            ChartTab.WEEK -> anchor.minusDays(WEEK_DAYS - 1)
            ChartTab.MONTH -> anchor.minusDays(MONTH_DAYS - 1)
            ChartTab.YEAR -> anchor.minusMonths(YEAR_MONTHS - 1).withDayOfMonth(1)
            ChartTab.ALL -> earliestDataDate ?: anchor
        }
        return ChartRange(startOfDay(startDate, zone), end)
    }

    /** 该 Tab 下数据点的粒度 */
    fun granularityOf(tab: ChartTab, range: ChartRange): Granularity = when (tab) {
        ChartTab.DAY -> Granularity.RAW
        ChartTab.WEEK, ChartTab.MONTH -> Granularity.DAILY
        ChartTab.YEAR -> Granularity.MONTHLY
        ChartTab.ALL ->
            if (range.daySpan > ALL_MONTHLY_THRESHOLD_DAYS) Granularity.MONTHLY else Granularity.DAILY
    }

    /**
     * 左右箭头：平移基准日期。
     *
     * 「总」视图不参与平移 —— 它的区间由数据本身决定，没有「上一段」的概念。
     */
    fun shiftAnchor(tab: ChartTab, anchor: LocalDate, steps: Int): LocalDate = when (tab) {
        ChartTab.DAY -> anchor.plusDays(steps.toLong())
        ChartTab.WEEK -> anchor.plusDays(WEEK_DAYS * steps)
        ChartTab.MONTH -> anchor.plusDays(MONTH_DAYS * steps)
        ChartTab.YEAR -> anchor.plusMonths(YEAR_MONTHS * steps)
        ChartTab.ALL -> anchor
    }

    /**
     * 右箭头是否可用。判据是**平移后的区间右端不能超过今天** ——
     * 不然「日」视图右移一格就到明天了，图表会是一片空白。
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
        return resolve(tab, next, earliestDataDate, zone).end <= endOfDay(today, zone)
    }

    /** 左箭头是否可用。有数据才能往回看，否则全是空白。 */
    fun canShiftBackward(earliestDataDate: LocalDate?, anchor: LocalDate): Boolean =
        earliestDataDate != null && anchor.isAfter(earliestDataDate)
}
