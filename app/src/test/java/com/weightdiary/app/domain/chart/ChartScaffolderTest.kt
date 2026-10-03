package com.weightdiary.app.domain.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ChartScaffolderTest {

    private val zone = ZoneId.of("UTC")

    // ─────────────── Y 轴：必须真的覆盖住数据 ───────────────

    /**
     * 这是修过的一个真 bug。
     *
     * 原先按「3M ≥ 跨度」挑步长，但下界向下取整会掉到 min 以下、吃掉 3M 的预算，
     * 结果是 `lower + 3M < max` —— 折线顶部被裁到图外。
     * 判据必须是「lower + 3M ≥ max」。
     */
    @Test
    fun `上界必须不低于数据最大值 - 否则折线会被裁掉`() {
        val cases = listOf(
            listOf(67.0, 68.8),          // 含目标线时最容易触发
            listOf(66.0, 72.0),
            listOf(66.0, 70.5),
            listOf(64.0, 76.0),
            listOf(65.0, 80.0),
            listOf(67.3, 71.8),
            listOf(60.1, 60.9),
            listOf(120.0, 121.0),
        )
        cases.forEach { values ->
            val axis = ChartScaffolder.buildYAxis(values)
            assertTrue(
                "输入 $values：上界 ${axis.upper} 低于最大值 ${values.max()}",
                axis.upper >= values.max() - 1e-9,
            )
            assertTrue(
                "输入 $values：下界 ${axis.lower} 高于最小值 ${values.min()}",
                axis.lower <= values.min() + 1e-9,
            )
        }
    }

    @Test
    fun `随机取值下也始终覆盖数据`() {
        var seed = 42L
        fun nextDouble(): Double {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
        }
        repeat(300) {
            val base = 20.0 + nextDouble() * 90.0
            val values = List(1 + (nextDouble() * 8).toInt()) { base + nextDouble() * 12.0 - 6.0 }
            val target = if (nextDouble() < 0.5) base - nextDouble() * 6.0 else null

            val all = if (target != null) values + target else values
            val axis = ChartScaffolder.buildYAxis(values, target)
            assertTrue(
                "values=$values target=$target 上界 ${axis.upper} 没盖住 ${all.max()}",
                axis.upper >= all.max() - 1e-6,
            )
            assertTrue(
                "values=$values target=$target 下界 ${axis.lower} 没盖住 ${all.min()}",
                axis.lower <= all.min() + 1e-6,
            )
        }
    }

    // ─────────────── 目标线：30% 撑开限制（决策 B8）───────────────

    @Test
    fun `目标靠近数据时纳入范围`() {
        // 数据 67.7-68.8（跨度 1.1），目标 67.0，差距 0.7 = 64% 跨度 → 超出 30%，不该纳入
        assertTrue(ChartScaffolder.shouldIncludeTarget(listOf(67.7, 68.8), 67.6))
        assertTrue(ChartScaffolder.shouldIncludeTarget(listOf(67.7, 68.8), 68.0))
    }

    @Test
    fun `目标远离数据时不纳入 - 否则折线会被压成平线`() {
        assertTrue(
            "目标 65 与数据 67.7-68.8 差 2.7，远超 30%",
            !ChartScaffolder.shouldIncludeTarget(listOf(67.7, 68.8), 65.0),
        )
        assertTrue(!ChartScaffolder.shouldIncludeTarget(listOf(67.7, 68.8), 70.5))
    }

    @Test
    fun `目标在数据区间内时必然纳入`() {
        assertTrue(ChartScaffolder.shouldIncludeTarget(listOf(66.0, 72.0), 68.0))
    }

    @Test
    fun `数据跨度为零时目标无条件纳入`() {
        assertTrue(ChartScaffolder.shouldIncludeTarget(listOf(68.0, 68.0), 60.0))
    }

    @Test
    fun `没有数据时目标无条件纳入`() {
        assertTrue(ChartScaffolder.shouldIncludeTarget(emptyList(), 60.0))
    }

    // ─────────────── Y 轴：对齐设计文档的主/次步长对照表 ───────────────

    /**
     * 逐行核对 [docs/03-技术设计.md §4.4] 的对照表。
     * 表里「数据跨度」是原始跨度，不含 padding —— 下界向下取整本身已提供留白。
     */
    @Test
    fun `设计文档对照表 - 主步长与总刻度线数`() {
        data class Case(val min: Double, val max: Double, val step: Double, val subs: Int, val total: Int)

        val cases = listOf(
            Case(68.0, 69.5, 0.5, 5, 16),
            Case(66.0, 70.5, 1.5, 3, 10),
            Case(66.0, 72.0, 2.0, 4, 13),
            Case(66.0, 75.0, 3.0, 3, 10),
            Case(64.0, 76.0, 4.0, 4, 13),
            Case(65.0, 80.0, 5.0, 5, 16),
        )

        cases.forEach { c ->
            val axis = ChartScaffolder.buildYAxis(listOf(c.min, c.max))
            assertEquals("跨度 ${c.max - c.min} 的主步长", c.step, axis.majorStep, 1e-9)
            assertEquals("跨度 ${c.max - c.min} 的分段数", c.subs, axis.subdivisions)
            assertEquals("跨度 ${c.max - c.min} 的总刻度线", c.total, axis.allTicks.size)
        }
    }

    @Test
    fun `恒有 4 条带数字的主刻度`() {
        listOf(
            listOf(68.0, 69.5), listOf(66.0, 75.0), listOf(60.0, 90.0), listOf(68.5),
        ).forEach { values ->
            val axis = ChartScaffolder.buildYAxis(values)
            assertEquals("输入 $values", 4, axis.majorTicks.size)
        }
    }

    @Test
    fun `主刻度都是步长的整数倍 - 数字好读`() {
        val axis = ChartScaffolder.buildYAxis(listOf(67.3, 71.8))
        axis.majorTicks.forEach { tick ->
            val ratio = tick / axis.majorStep
            assertEquals("刻度 $tick 不是步长 ${axis.majorStep} 的整数倍", ratio, Math.round(ratio).toDouble(), 1e-9)
        }
    }

    @Test
    fun `次步长最多一位小数`() {
        val cases = listOf(
            listOf(68.0, 69.5), listOf(66.0, 70.5), listOf(66.0, 72.0),
            listOf(66.0, 75.0), listOf(64.0, 76.0), listOf(65.0, 80.0),
            listOf(60.0, 100.0), listOf(67.0, 67.4),
        )
        cases.forEach { values ->
            val axis = ChartScaffolder.buildYAxis(values)
            val m = axis.minorStep
            assertEquals("输入 $values 的次步长 $m 不止一位小数", m * 10, Math.round(m * 10).toDouble(), 1e-9)
        }
    }

    @Test
    fun `次刻度不含与主刻度重合的位置`() {
        val axis = ChartScaffolder.buildYAxis(listOf(66.0, 72.0))
        axis.minorTicks.forEach { tick ->
            assertTrue(
                "次刻度 $tick 与主刻度重合了",
                axis.majorTicks.none { kotlin.math.abs(it - tick) < 1e-9 },
            )
        }
        assertEquals(axis.allTicks.size - axis.majorTicks.size, axis.minorTicks.size)
    }

    // ─────────────── Y 轴：目标线 ───────────────

    @Test
    fun `目标线必须落在范围内`() {
        // 数据 68-72，目标 65 —— 不把目标纳入的话虚线会跑到图外
        val axis = ChartScaffolder.buildYAxis(listOf(68.0, 72.0), targetLine = 65.0)
        assertTrue("下界 ${axis.lower} 没包住目标 65.0", axis.lower <= 65.0)
        assertTrue(axis.upper >= 72.0)
    }

    @Test
    fun `目标线在数据范围内时不改变区间`() {
        val withoutTarget = ChartScaffolder.buildYAxis(listOf(66.0, 72.0))
        val withTarget = ChartScaffolder.buildYAxis(listOf(66.0, 72.0), targetLine = 68.0)
        assertEquals(withoutTarget.lower, withTarget.lower, 1e-9)
        assertEquals(withoutTarget.upper, withTarget.upper, 1e-9)
    }

    // ─────────────── Y 轴：退化 ───────────────

    @Test
    fun `没有数据时给一个合法区间`() {
        val axis = ChartScaffolder.buildYAxis(emptyList())
        assertTrue(axis.upper > axis.lower)
        assertEquals(4, axis.majorTicks.size)
    }

    @Test
    fun `只有一个数据点时上下各扩 1`() {
        val axis = ChartScaffolder.buildYAxis(listOf(68.5))
        assertTrue("下界 ${axis.lower} 应低于 68.5", axis.lower < 68.5)
        assertTrue("上界 ${axis.upper} 应高于 68.5", axis.upper > 68.5)
    }

    @Test
    fun `所有值相同时不产生零跨度`() {
        val axis = ChartScaffolder.buildYAxis(listOf(68.0, 68.0, 68.0))
        assertTrue(axis.upper > axis.lower)
        assertTrue(axis.lower <= 68.0 && axis.upper >= 68.0)
    }

    @Test
    fun `非有限值被忽略`() {
        val axis = ChartScaffolder.buildYAxis(listOf(66.0, Double.NaN, 72.0, Double.POSITIVE_INFINITY))
        assertTrue(axis.lower.isFinite() && axis.upper.isFinite())
        assertTrue(axis.lower <= 66.0 && axis.upper >= 72.0)
    }

    @Test
    fun `normalize 把值映射到 0 到 1`() {
        val axis = ChartScaffolder.buildYAxis(listOf(66.0, 72.0))
        assertEquals(0.0, axis.normalize(axis.lower), 1e-9)
        assertEquals(1.0, axis.normalize(axis.upper), 1e-9)
        assertEquals(0.5, axis.normalize((axis.lower + axis.upper) / 2), 1e-9)
        // 越界裁剪
        assertEquals(0.0, axis.normalize(axis.lower - 10), 1e-9)
        assertEquals(1.0, axis.normalize(axis.upper + 10), 1e-9)
    }

    // ─────────────── X 轴标签 ───────────────

    @Test
    fun `5 个标签位置严格四等分`() {
        val range = RangeResolver.resolve(
            ChartTab.MONTH, LocalDate.of(2026, 6, 30), null, zone,
        )
        val positions = ChartScaffolder.xLabelPositions(range)
        assertEquals(5, positions.size)
        assertEquals(range.start, positions.first())
        assertEquals(range.end, positions.last())

        val span = (range.end.toEpochMilli() - range.start.toEpochMilli()).toDouble()
        positions.forEachIndexed { i, p ->
            val expected = range.start.toEpochMilli() + (span * i / 4).toLong()
            assertEquals("第 $i 个标签位置", expected, p.toEpochMilli())
        }
    }

    @Test
    fun `标签间距相等 - 数据有缺口也不受影响`() {
        val range = RangeResolver.resolve(
            ChartTab.MONTH, LocalDate.of(2026, 6, 30), null, zone,
        )
        val ms = ChartScaffolder.xLabelPositions(range).map { it.toEpochMilli() }
        val gaps = (0 until ms.size - 1).map { ms[it + 1] - ms[it] }
        assertTrue("间距不齐：$gaps", gaps.max() - gaps.min() <= 1)
    }

    // ─────────────── X 轴刻度位 ───────────────

    @Test
    fun `周视图的刻度位按日历日铺满`() {
        val range = RangeResolver.resolve(ChartTab.WEEK, LocalDate.of(2026, 6, 30), null, zone)
        val ticks = ChartScaffolder.xTickPositions(range, ChartTab.WEEK, zone)
        assertEquals(7, ticks.size)
    }

    @Test
    fun `月视图的刻度位按日历日铺满 - 缺失日期也保留位置`() {
        val range = RangeResolver.resolve(ChartTab.MONTH, LocalDate.of(2026, 6, 30), null, zone)
        val ticks = ChartScaffolder.xTickPositions(range, ChartTab.MONTH, zone)
        // 近 30 天 = 30 个日历日，与有没有记录无关
        assertEquals(30, ticks.size)
    }

    @Test
    fun `年视图的刻度位按月铺满`() {
        val range = RangeResolver.resolve(ChartTab.YEAR, LocalDate.of(2026, 6, 30), null, zone)
        val ticks = ChartScaffolder.xTickPositions(range, ChartTab.YEAR, zone)
        assertEquals(12, ticks.size)
    }

    @Test
    fun `日视图的刻度位按小时铺满`() {
        val range = RangeResolver.resolve(ChartTab.DAY, LocalDate.of(2026, 6, 30), null, zone)
        val ticks = ChartScaffolder.xTickPositions(range, ChartTab.DAY, zone)
        assertEquals(24, ticks.size)
    }
}
