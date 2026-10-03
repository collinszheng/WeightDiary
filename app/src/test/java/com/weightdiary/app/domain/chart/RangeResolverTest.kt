package com.weightdiary.app.domain.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RangeResolverTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 6, 30)
    private val earliest = LocalDate.of(2024, 3, 1)

    // ─────────────── 区间解析 ───────────────

    @Test
    fun `日视图就是当天`() {
        val r = RangeResolver.resolve(ChartTab.DAY, today, earliest, zone)
        assertEquals("2026-06-30T00:00:00Z", r.start.toString())
        assertEquals(1, r.daySpan)
    }

    @Test
    fun `周视图是近 7 天`() {
        val r = RangeResolver.resolve(ChartTab.WEEK, today, earliest, zone)
        assertEquals("2026-06-24T00:00:00Z", r.start.toString())
        assertEquals(7, r.daySpan)
    }

    @Test
    fun `月视图是近 30 天`() {
        val r = RangeResolver.resolve(ChartTab.MONTH, today, earliest, zone)
        assertEquals(30, r.daySpan)
        assertEquals("2026-06-01T00:00:00Z", r.start.toString())
    }

    @Test
    fun `年视图是近 12 个月且从月初起算`() {
        val r = RangeResolver.resolve(ChartTab.YEAR, today, earliest, zone)
        // 2025-07-01 到 2026-06-30
        assertEquals("2025-07-01T00:00:00Z", r.start.toString())
        assertEquals("2026-06-30T23:59:59.999Z", r.end.toString())
    }

    @Test
    fun `总视图从首条记录起算`() {
        val r = RangeResolver.resolve(ChartTab.ALL, today, earliest, zone)
        assertEquals("2024-03-01T00:00:00Z", r.start.toString())
        assertEquals("2026-06-30T23:59:59.999Z", r.end.toString())
    }

    @Test
    fun `总视图没有数据时退化为当天`() {
        val r = RangeResolver.resolve(ChartTab.ALL, today, null, zone)
        assertEquals(1, r.daySpan)
    }

    // ─────────────── 粒度 ───────────────

    @Test
    fun `各 Tab 的聚合粒度`() {
        fun g(tab: ChartTab) = RangeResolver.granularityOf(
            tab, RangeResolver.resolve(tab, today, earliest, zone),
        )
        assertEquals(Granularity.RAW, g(ChartTab.DAY))
        assertEquals(Granularity.DAILY, g(ChartTab.WEEK))
        assertEquals(Granularity.DAILY, g(ChartTab.MONTH))
        assertEquals(Granularity.MONTHLY, g(ChartTab.YEAR))
    }

    @Test
    fun `总视图在跨度大时自动按月聚合`() {
        // earliest 2024-03-01 → today 2026-06-30，远超 120 天
        val r = RangeResolver.resolve(ChartTab.ALL, today, earliest, zone)
        assertEquals(Granularity.MONTHLY, RangeResolver.granularityOf(ChartTab.ALL, r))
    }

    @Test
    fun `总视图跨度小时按天聚合`() {
        val near = LocalDate.of(2026, 5, 1)
        val r = RangeResolver.resolve(ChartTab.ALL, today, near, zone)
        assertEquals(Granularity.DAILY, RangeResolver.granularityOf(ChartTab.ALL, r))
    }

    // ─────────────── 平移 ───────────────

    @Test
    fun `各 Tab 的平移步长`() {
        assertEquals(today.minusDays(1), RangeResolver.shiftAnchor(ChartTab.DAY, today, -1))
        assertEquals(today.minusDays(7), RangeResolver.shiftAnchor(ChartTab.WEEK, today, -1))
        assertEquals(today.minusDays(30), RangeResolver.shiftAnchor(ChartTab.MONTH, today, -1))
        assertEquals(today.minusMonths(12), RangeResolver.shiftAnchor(ChartTab.YEAR, today, -1))
        // 总视图不参与平移
        assertEquals(today, RangeResolver.shiftAnchor(ChartTab.ALL, today, -1))
    }

    @Test
    fun `已经在最新时右箭头不可用`() {
        // 日视图停在今天，再右移就到明天了
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
        assertFalse(RangeResolver.canShiftBackward(null, today))
        // 已经退到首条记录那天
        assertFalse(RangeResolver.canShiftBackward(earliest, earliest))
        assertTrue(RangeResolver.canShiftBackward(earliest, today))
    }
}
