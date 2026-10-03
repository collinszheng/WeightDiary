package com.weightdiary.app.domain.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

class RangeResolverTest {

    private val zone = ZoneId.of("UTC")

    /** 2026-06-30 是周二 */
    private val anchor = LocalDate.of(2026, 6, 30)
    private val today = anchor
    private val earliest = LocalDate.of(2024, 3, 1)

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun expectStart(date: LocalDate) = RangeResolver.startOfDay(date, zone).toString()
    private fun expectEnd(date: LocalDate) = RangeResolver.endOfDay(date, zone).toString()

    // ─────────────── 自然周期 ───────────────

    @Test
    fun `日视图就是当天`() {
        val r = RangeResolver.resolve(ChartTab.DAY, anchor, earliest, zone)
        assertEquals(expectStart(anchor), r.start.toString())
        assertEquals(expectEnd(anchor), r.end.toString())
        assertEquals(1, r.daySpan)
    }

    /**
     * 周视图是**本周一到本周日**，不是「今天往前推 7 天」。
     * 这样每周的图长得一样，左右箭头也正好是「上一周 / 下一周」。
     */
    @Test
    fun `周视图是本周一到周日`() {
        val r = RangeResolver.resolve(ChartTab.WEEK, anchor, earliest, zone)
        val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        assertEquals(expectStart(monday), r.start.toString())
        assertEquals(expectEnd(monday.plusDays(6)), r.end.toString())
        assertEquals(7, r.daySpan)
        // 起点必须落在周一
        assertEquals(DayOfWeek.MONDAY, r.start.atZone(zone).dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, r.end.atZone(zone).dayOfWeek)
    }

    @Test
    fun `月视图是本月 1 日到月末`() {
        val r = RangeResolver.resolve(ChartTab.MONTH, anchor, earliest, zone)
        assertEquals(expectStart(d(2026, 6, 1)), r.start.toString())
        assertEquals(expectEnd(d(2026, 6, 30)), r.end.toString())
    }

    @Test
    fun `月视图正确处理 31 天与闰年 2 月`() {
        val r31 = RangeResolver.resolve(ChartTab.MONTH, d(2026, 7, 15), earliest, zone)
        assertEquals(expectEnd(d(2026, 7, 31)), r31.end.toString())

        val feb = RangeResolver.resolve(ChartTab.MONTH, d(2028, 2, 10), earliest, zone)
        assertEquals(expectStart(d(2028, 2, 1)), feb.start.toString())
        assertEquals(expectEnd(d(2028, 2, 29)), feb.end.toString())
    }

    @Test
    fun `年视图是 1 月 1 日到 12 月 31 日`() {
        val r = RangeResolver.resolve(ChartTab.YEAR, anchor, earliest, zone)
        assertEquals(expectStart(d(2026, 1, 1)), r.start.toString())
        assertEquals(expectEnd(d(2026, 12, 31)), r.end.toString())
    }

    @Test
    fun `总视图从首条记录起算`() {
        val r = RangeResolver.resolve(ChartTab.ALL, today, earliest, zone)
        assertEquals(expectStart(earliest), r.start.toString())
        assertEquals(expectEnd(today), r.end.toString())
    }

    @Test
    fun `总视图没有数据时退化为当天`() {
        assertEquals(1, RangeResolver.resolve(ChartTab.ALL, today, null, zone).daySpan)
    }

    // ─────────────── 粒度 ───────────────

    @Test
    fun `各 Tab 的聚合粒度`() {
        fun g(tab: ChartTab) = RangeResolver.granularityOf(
            tab, RangeResolver.resolve(tab, anchor, earliest, zone),
        )
        assertEquals(Granularity.RAW, g(ChartTab.DAY))
        assertEquals(Granularity.DAILY, g(ChartTab.WEEK))
        assertEquals(Granularity.DAILY, g(ChartTab.MONTH))
        assertEquals(Granularity.MONTHLY, g(ChartTab.YEAR))
    }

    @Test
    fun `总视图在跨度大时自动按月聚合`() {
        val r = RangeResolver.resolve(ChartTab.ALL, today, earliest, zone)
        assertEquals(Granularity.MONTHLY, RangeResolver.granularityOf(ChartTab.ALL, r))
    }

    // ─────────────── 平移 ───────────────

    @Test
    fun `各 Tab 的平移步长`() {
        assertEquals(anchor.minusDays(1), RangeResolver.shiftAnchor(ChartTab.DAY, anchor, -1))
        assertEquals(anchor.minusWeeks(1), RangeResolver.shiftAnchor(ChartTab.WEEK, anchor, -1))
        assertEquals(d(2026, 5, 1), RangeResolver.shiftAnchor(ChartTab.MONTH, anchor, -1))
        assertEquals(d(2025, 1, 1), RangeResolver.shiftAnchor(ChartTab.YEAR, anchor, -1))
        assertEquals(anchor, RangeResolver.shiftAnchor(ChartTab.ALL, anchor, -1))
    }

    /**
     * 从 31 号往前跳必须落到上个月的 1 号，而不是漂到 28 号去 ——
     * `LocalDate.plusMonths` 会做月末钳制，所以先归到月初再加减。
     */
    @Test
    fun `月末平移不漂移`() {
        val jan31 = d(2026, 1, 31)
        assertEquals(d(2025, 12, 1), RangeResolver.shiftAnchor(ChartTab.MONTH, jan31, -1))
        // 连续往前跳多次也要稳
        var a = jan31
        repeat(3) { a = RangeResolver.shiftAnchor(ChartTab.MONTH, a, -1) }
        assertEquals(d(2025, 10, 1), a)
    }

    @Test
    fun `已经在当期时右箭头不可用`() {
        assertFalse(RangeResolver.canShiftForward(ChartTab.DAY, today, today, earliest, zone))
        assertFalse(RangeResolver.canShiftForward(ChartTab.WEEK, today, today, earliest, zone))
        assertFalse(RangeResolver.canShiftForward(ChartTab.MONTH, today, today, earliest, zone))
        assertFalse(RangeResolver.canShiftForward(ChartTab.YEAR, today, today, earliest, zone))
        assertFalse(RangeResolver.canShiftForward(ChartTab.ALL, today, today, earliest, zone))
    }

    @Test
    fun `往回平移后右箭头恢复可用`() {
        val back = RangeResolver.shiftAnchor(ChartTab.WEEK, today, -3)
        assertTrue(RangeResolver.canShiftForward(ChartTab.WEEK, back, today, earliest, zone))
    }

    @Test
    fun `没有更早数据时左箭头不可用`() {
        assertFalse(RangeResolver.canShiftBackward(ChartTab.WEEK, today, null, zone))
        // 当期已经覆盖到首条记录那天
        assertFalse(RangeResolver.canShiftBackward(ChartTab.MONTH, d(2024, 3, 10), earliest, zone))
        assertTrue(RangeResolver.canShiftBackward(ChartTab.MONTH, today, earliest, zone))
    }
}
