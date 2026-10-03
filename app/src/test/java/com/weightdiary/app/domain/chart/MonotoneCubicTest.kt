package com.weightdiary.app.domain.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonotoneCubicTest {

    // ─────────────── 退化情况 ───────────────

    @Test
    fun `0 个点不崩`() {
        assertTrue(MonotoneCubic.tangents(emptyList(), emptyList()).isEmpty())
        assertTrue(MonotoneCubic.bezierSegments(emptyList(), emptyList()).isEmpty())
        assertTrue(MonotoneCubic.sample(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `1 个点退化为单点`() {
        val pts = MonotoneCubic.sample(listOf(5.0), listOf(68.0))
        assertEquals(1, pts.size)
        assertEquals(5.0, pts[0].first, 1e-9)
        assertEquals(68.0, pts[0].second, 1e-9)
        assertTrue(MonotoneCubic.bezierSegments(listOf(5.0), listOf(68.0)).isEmpty())
    }

    @Test
    fun `2 个点退化为直线`() {
        val xs = listOf(0.0, 10.0)
        val ys = listOf(70.0, 60.0)
        val m = MonotoneCubic.tangents(xs, ys)
        // 两点切线相同 = 直线斜率
        assertEquals(-1.0, m[0], 1e-9)
        assertEquals(-1.0, m[1], 1e-9)

        val segs = MonotoneCubic.bezierSegments(xs, ys)
        assertEquals(1, segs.size)
        // 控制点应当落在直线上
        assertEquals(70.0 - 10.0 / 3.0, segs[0].c1y, 1e-9)
        assertEquals(60.0 + 10.0 / 3.0, segs[0].c2y, 1e-9)
    }

    // ─────────────── 核心性质 ───────────────

    /**
     * 最关键的一条：**不过冲**。
     *
     * 普通三次样条在「先降后升」这类数据上会冲出数据范围，画出根本没发生过的峰谷。
     */
    @Test
    fun `不过冲 - 曲线始终落在相邻两点的值域内`() {
        val xs = listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0)
        val ys = listOf(70.0, 66.0, 69.0, 65.0, 68.0, 64.0) // 剧烈来回，最容易过冲的形状

        for (i in 0 until xs.size - 1) {
            val lo = minOf(ys[i], ys[i + 1]) - 1e-6
            val hi = maxOf(ys[i], ys[i + 1]) + 1e-6
            val seg = MonotoneCubic.bezierSegments(xs, ys)[i]
            for (k in 0..64) {
                val t = k / 64.0
                val u = 1 - t
                val y = u * u * u * seg.y0 + 3 * u * u * t * seg.c1y +
                    3 * u * t * t * seg.c2y + t * t * t * seg.y1
                assertTrue(
                    "第 $i 段 t=$t 处 y=$y 超出 [${ys[i]}, ${ys[i + 1]}] 的值域",
                    y >= lo && y <= hi,
                )
            }
        }
    }

    @Test
    fun `保单调 - 单调下降的数据插值后仍然单调`() {
        val xs = listOf(0.0, 1.0, 2.0, 3.0, 4.0)
        val ys = listOf(72.0, 71.0, 69.5, 69.4, 68.0)

        val sampled = MonotoneCubic.sample(xs, ys, samplesPerSegment = 32).map { it.second }
        for (i in 0 until sampled.size - 1) {
            assertTrue(
                "第 $i 个采样点出现回升：${sampled[i]} → ${sampled[i + 1]}",
                sampled[i + 1] <= sampled[i] + 1e-9,
            )
        }
    }

    @Test
    fun `曲线穿过所有数据点`() {
        val xs = listOf(0.0, 1.0, 2.0, 3.0)
        val ys = listOf(70.0, 68.0, 69.0, 65.0)
        val segs = MonotoneCubic.bezierSegments(xs, ys)
        segs.forEachIndexed { i, s ->
            assertEquals(xs[i], s.x0, 1e-9)
            assertEquals(ys[i], s.y0, 1e-9)
            assertEquals(xs[i + 1], s.x1, 1e-9)
            assertEquals(ys[i + 1], s.y1, 1e-9)
        }
    }

    @Test
    fun `极值点切线为 0`() {
        // 中间那个点是局部最高点，切线应当是 0
        val xs = listOf(0.0, 1.0, 2.0)
        val ys = listOf(68.0, 70.0, 68.5)
        val m = MonotoneCubic.tangents(xs, ys)
        assertEquals(0.0, m[1], 1e-9)
    }

    @Test
    fun `采样点按 x 递增`() {
        val xs = listOf(0.0, 1.0, 2.0, 3.0)
        val ys = listOf(70.0, 72.0, 68.0, 69.0)
        val sampled = MonotoneCubic.sample(xs, ys, samplesPerSegment = 8)
        for (i in 0 until sampled.size - 1) {
            assertTrue(sampled[i].first <= sampled[i + 1].first + 1e-9)
        }
        assertEquals(xs.first(), sampled.first().first, 1e-9)
        assertEquals(xs.last(), sampled.last().first, 1e-9)
    }

    @Test
    fun `全部数值相同时不产生除零`() {
        val xs = listOf(0.0, 1.0, 2.0)
        val ys = listOf(68.0, 68.0, 68.0)
        val sampled = MonotoneCubic.sample(xs, ys)
        assertTrue(sampled.all { it.second.isFinite() })
        assertTrue(sampled.all { kotlin.math.abs(it.second - 68.0) < 1e-9 })
    }
}
