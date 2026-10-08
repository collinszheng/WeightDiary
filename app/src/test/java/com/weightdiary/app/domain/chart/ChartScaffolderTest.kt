package com.weightdiary.app.domain.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class ChartScaffolderTest {

    private val zone = ZoneId.of("UTC")

    // ─────────────── Y 轴：数据居中 ───────────────

    /**
     * 用户反馈：折线总是贴在图表下半部分。
     *
     * 原因是下界原本用 `floor(lo / M) * M` —— 只会往下掉、从不往上抬，
     * 数据自然永远偏下。改成为「让数据居中」的那个步长整数倍之后就正常了。
     */
    @Test
    fun `数据落在刻度窗口中段 - 用户实测场景`() {
        // 一周数据 68.32–68.90。旧算法给 68/69/70/71，折线挤在下半部分
        val axis = ChartScaffolder.buildYAxis(listOf(68.32, 68.4, 68.74, 68.5, 68.9, 68.7, 68.52))
        assertEquals(
            "应当得到 67/68/69/70",
            listOf(67.0, 68.0, 69.0, 70.0),
            axis.majorTicks,
        )
    }

    @Test
    fun `数据中点与窗口中点的偏差不超过一个主步长`() {
        var seed = 99L
        fun nextDouble(): Double {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
        }
        repeat(300) {
            val base = 20.0 + nextDouble() * 90.0
            val width = 0.1 + nextDouble() * 20.0
            val values = List(2 + (nextDouble() * 6).toInt()) { base + nextDouble() * width }

            val axis = ChartScaffolder.buildYAxis(values)
            val dataCenter = (values.min() + values.max()) / 2
            val windowCenter = (axis.lower + axis.upper) / 2
            assertTrue(
                "values=$values 数据中点 $dataCenter 偏离窗口中点 $windowCenter 超过一个主步长 ${axis.majorStep}",
                kotlin.math.abs(dataCenter - windowCenter) <= axis.majorStep + 1e-6,
            )
        }
    }

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

    // ─────────────── 目标线：始终纳入 Y 轴 ───────────────

    /**
     * 产品要求目标线**必须在图上看得见**，所以不再有「离得太远就不撑开」的例外。
     * 代价是目标离数据很远时折线会被压扁 —— 这是刻意用「看得见目标」换「看趋势」。
     */
    @Test
    fun `目标线无条件纳入范围 - 哪怕离数据很远`() {
        // 数据 67.7–68.8，目标 65.0：差 2.7，远超原先 30% 的阈值
        val axis = ChartScaffolder.buildYAxis(listOf(67.7, 68.8), targetLine = 65.0)
        assertTrue("下界 ${axis.lower} 没包住目标 65.0", axis.lower <= 65.0)
        assertTrue(axis.upper >= 68.8)
    }

    @Test
    fun `目标高于数据时同样纳入`() {
        val axis = ChartScaffolder.buildYAxis(listOf(67.7, 68.8), targetLine = 75.0)
        assertTrue("上界 ${axis.upper} 没包住目标 75.0", axis.upper >= 75.0)
        assertTrue(axis.lower <= 67.7)
    }

    @Test
    fun `目标在数据区间内时不影响`() {
        val without = ChartScaffolder.buildYAxis(listOf(66.0, 72.0))
        val with = ChartScaffolder.buildYAxis(listOf(66.0, 72.0), targetLine = 68.0)
        assertEquals(without.lower, with.lower, 1e-9)
        assertEquals(without.upper, with.upper, 1e-9)
    }

    @Test
    fun `没有数据时目标也纳入`() {
        val axis = ChartScaffolder.buildYAxis(emptyList(), targetLine = 60.0)
        assertTrue(axis.upper > axis.lower)
    }
    // ─────────────── Y 轴：数据居中 ───────────────

    // ─────────────── Y 轴：必须真的覆盖住数据 ───────────────

    /**
     * 这是修过的一个真 bug。
     *
     * 原先按「3M ≥ 跨度」挑步长，但下界向下取整会掉到 min 以下、吃掉 3M 的预算，
     * 结果是 `lower + 3M < max` —— 折线顶部被裁到图外。
     * 判据必须是「lower + 3M ≥ max」。
     */


    // ─────────────── 目标线：30% 撑开限制（决策 B8）───────────────






    // ─────────────── Y 轴：对齐设计文档的主/次步长对照表 ───────────────

    /**
     * 逐行核对 [docs/03-技术设计.md §4.4] 的对照表。
     *
     * 算法要点：主步长只从整数里选（最小 1），下界按步长整数倍向下取整，
     * 数据上下各留 8% 边距，范围恒为 `lower .. lower + 3M`。
     */
    @Test
    fun `设计文档对照表 - 主步长与总刻度线数`() {
        data class Case(
            val min: Double,
            val max: Double,
            val step: Double,
            val subs: Int,
            val total: Int,
            val ticks: List<Double>,
        )

        val cases = listOf(
            Case(68.0, 69.0, 1.0, 5, 16, listOf(67.0, 68.0, 69.0, 70.0)),
            Case(68.0, 69.5, 1.0, 5, 16, listOf(67.0, 68.0, 69.0, 70.0)),
            Case(66.0, 72.0, 4.0, 4, 13, listOf(64.0, 68.0, 72.0, 76.0)),
            Case(66.0, 75.0, 4.0, 4, 13, listOf(64.0, 68.0, 72.0, 76.0)),
            Case(64.0, 76.0, 6.0, 4, 13, listOf(60.0, 66.0, 72.0, 78.0)),
            Case(65.0, 80.0, 10.0, 4, 13, listOf(60.0, 70.0, 80.0, 90.0)),
        )

        cases.forEach { c ->
            val axis = ChartScaffolder.buildYAxis(listOf(c.min, c.max))
            val label = "数据 ${c.min}–${c.max}"
            assertEquals("$label 的主步长", c.step, axis.majorStep, 1e-9)
            assertEquals("$label 的分段数", c.subs, axis.subdivisions)
            assertEquals("$label 的总刻度线", c.total, axis.allTicks.size)
            c.ticks.forEachIndexed { i, expected ->
                assertEquals("$label 第 $i 条主刻度", expected, axis.majorTicks[i], 1e-9)
            }
        }
    }

    /**
     * 核心要求：**四条主刻度的数字必须是整数**。
     *
     * 主步长只从整数里选，所以只要下界是步长的整数倍就成立。
     * 这是用户明确提出的要求 —— 早先为了照顾小跨度，步长会落到 0.4 这种值，
     * 刻度就出现了 68.4 / 68.8。
     */
    @Test
    fun `主刻度数字永远是整数`() {
        var seed = 7L
        fun nextDouble(): Double {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble())
        }
        repeat(400) {
            // 覆盖体重 / BMI / 体脂率三种量级，以及各种窄跨度
            val base = when ((nextDouble() * 3).toInt()) {
                0 -> 20.0 + nextDouble() * 100.0   // 体重
                1 -> 15.0 + nextDouble() * 20.0    // BMI
                else -> 5.0 + nextDouble() * 45.0  // 体脂率
            }
            val width = listOf(0.05, 0.3, 0.9, 2.0, 7.0, 25.0)[(nextDouble() * 6).toInt()]
            val values = List(2 + (nextDouble() * 6).toInt()) { base + nextDouble() * width }

            val axis = ChartScaffolder.buildYAxis(values)
            axis.majorTicks.forEach { tick ->
                assertEquals(
                    "values=$values 出现非整数刻度 $tick",
                    tick,
                    Math.round(tick).toDouble(),
                    1e-9,
                )
            }
        }
    }

    /** 用户举的例子：记录都在 68–69 之间 → 刻度 67 / 68 / 69 / 70 */
    @Test
    fun `用户举例 - 68 到 69 之间应得到 67 68 69 70`() {
        val axis = ChartScaffolder.buildYAxis(listOf(68.0, 68.4, 68.8, 69.0))
        assertEquals(listOf(67.0, 68.0, 69.0, 70.0), axis.majorTicks)
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
            listOf(68.0, 69.0), listOf(66.0, 72.0), listOf(66.0, 75.0),
            listOf(64.0, 76.0), listOf(65.0, 80.0), listOf(60.0, 100.0),
            listOf(67.0, 67.4), listOf(20.0, 200.0),
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

    // ─────────────── Y 轴：参照线可见性 ───────────────

    @Test
    fun `showsReferenceLine 只认严格落在窗口内部的线`() {
        val axis = ChartScaffolder.buildYAxis(listOf(66.0, 72.0))
        assertTrue(axis.showsReferenceLine((axis.lower + axis.upper) / 2))
        // 压在上下边框上不算「看得见」—— 画上去会和坐标轴重合
        assertFalse(axis.showsReferenceLine(axis.lower))
        assertFalse(axis.showsReferenceLine(axis.upper))
        assertFalse(axis.showsReferenceLine(axis.lower - 0.1))
        assertFalse(axis.showsReferenceLine(axis.upper + 0.1))
    }

    /**
     * 真机回归（小米 11 / HyperOS，2025-09），详见 [docs/09-真机实测记录.md §6.6]。
     *
     * 现场数据：本周只有一个绘图点 66.0，目标 65.0。
     *
     * 旧代码先算 `buildYAxis(values)`（**不含**目标）拿步长，再与含目标的比，
     * 据此判「纳入目标要付代价」，于是坐标轴用了不含目标的那个 —— 而那个窗口
     * 碰巧也把 65 包了进去。结果目标线不画，角标又按 `65 < lower` 判成「目标在上方」，
     * 弹出「▲ 还需 1.0 kg」——**可用户实际要减 1.0 kg**。
     *
     * 修正后目标无条件纳入坐标轴（与上面「目标线无条件纳入范围」一致），
     * 目标必然可见，角标只在极端跨度连兜底步长都撑不下时才轮到。
     */
    @Test
    fun `真机回归 - 目标被旧判据挡掉其实它就在那个窗口里`() {
        val withoutTarget = ChartScaffolder.buildYAxis(listOf(66.0))

        // 旧代码用的就是这个「不含目标」的轴，而 65 恰好落在它里面 ——
        // 正因如此「线不画」和「角标说在上方」才会同时出现、互相矛盾
        assertTrue(
            "65 本来就在不含目标的窗口 ${withoutTarget.lower}..${withoutTarget.upper} 内",
            withoutTarget.showsReferenceLine(65.0),
        )

        val axis = ChartScaffolder.buildYAxis(listOf(66.0), targetLine = 65.0)
        // 含目标的轴步长更小（窗口更紧），所以旧判据才认为「纳入要付代价」而放弃了它
        assertTrue(
            "含目标步长 ${axis.majorStep} 应小于不含目标的 ${withoutTarget.majorStep}",
            axis.majorStep < withoutTarget.majorStep,
        )
        assertTrue(
            "目标 65 必须落在窗口 ${axis.lower}..${axis.upper} 内",
            axis.showsReferenceLine(65.0),
        )
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

    // ─────────────── X 轴标签：按视图分类 ───────────────

    private val anchorDate = LocalDate.of(2026, 6, 30) // 周二

    private fun labelsFor(tab: ChartTab) = ChartScaffolder.xLabels(
        tab,
        RangeResolver.resolve(tab, anchorDate, LocalDate.of(2024, 3, 1), zone),
        zone,
    )

    /** 周视图就是七个星期几，不是「5 个等分」 */
    @Test
    fun `周视图给七个标签且都是星期几`() {
        val labels = labelsFor(ChartTab.WEEK)
        assertEquals(7, labels.instants.size)
        assertEquals(XLabelKind.WEEKDAY, labels.kind)
        // 七个标签必须分别落在周一到周日，且各在所属那天的中点
        val days = labels.instants.map { it.atZone(zone).dayOfWeek }
        assertEquals(
            listOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
            ),
            days,
        )
        labels.instants.forEach { assertEquals(12, it.atZone(zone).hour) }
    }

    /** 年视图是 1 月至 12 月 */
    @Test
    fun `年视图给十二个月标签`() {
        val labels = labelsFor(ChartTab.YEAR)
        assertEquals(12, labels.instants.size)
        assertEquals(XLabelKind.MONTH_OF_YEAR, labels.kind)
        assertEquals((1..12).toList(), labels.instants.map { it.atZone(zone).monthValue })
    }

    /** 月视图是 1 / 10 / 20 / 月末 */
    @Test
    fun `月视图给 1 10 20 月末四个标签`() {
        val labels = labelsFor(ChartTab.MONTH)
        assertEquals(XLabelKind.DAY_OF_MONTH, labels.kind)
        assertEquals(listOf(1, 10, 20, 30), labels.instants.map { it.atZone(zone).dayOfMonth })
    }

    @Test
    fun `月视图在 31 天的月份用 31 作为月末`() {
        val labels = ChartScaffolder.xLabels(
            ChartTab.MONTH,
            RangeResolver.resolve(ChartTab.MONTH, LocalDate.of(2026, 7, 15), null, zone),
            zone,
        )
        assertEquals(listOf(1, 10, 20, 31), labels.instants.map { it.atZone(zone).dayOfMonth })
    }

    /** 日视图是 0 / 6 / 12 / 18 时，加上区间末端 */
    @Test
    fun `日视图给五个时刻标签`() {
        val labels = labelsFor(ChartTab.DAY)
        assertEquals(5, labels.instants.size)
        assertEquals(XLabelKind.HOUR, labels.kind)
        assertEquals(listOf(0, 6, 12, 18), labels.instants.dropLast(1).map { it.atZone(zone).hour })
        // 最后一个就是区间末端（23:59:59.999），UI 会把它显示成 24:00
        val range = RangeResolver.resolve(ChartTab.DAY, anchorDate, null, zone)
        assertEquals(range.end, labels.instants.last())
    }

    /** 「总」跨度不固定，只能等分 */
    @Test
    fun `总视图给五个等分日期`() {
        val labels = labelsFor(ChartTab.ALL)
        assertEquals(5, labels.instants.size)
        assertEquals(XLabelKind.DATE, labels.kind)

        val range = RangeResolver.resolve(ChartTab.ALL, anchorDate, LocalDate.of(2024, 3, 1), zone)
        assertEquals(range.start, labels.instants.first())
        assertEquals(range.end, labels.instants.last())

        val span = (range.end.toEpochMilli() - range.start.toEpochMilli()).toDouble()
        labels.instants.forEachIndexed { i, p ->
            assertEquals(
                range.start.toEpochMilli() + (span * i / 4).toLong(),
                p.toEpochMilli(),
            )
        }
    }

    @Test
    fun `所有标签都落在区间内`() {
        ChartTab.entries.forEach { tab ->
            val range = RangeResolver.resolve(tab, anchorDate, LocalDate.of(2024, 3, 1), zone)
            ChartScaffolder.xLabels(tab, range, zone).instants.forEach { instant ->
                assertTrue(
                    "$tab 的标签 $instant 跑到区间 $range 之外了",
                    instant >= range.start && instant <= range.end,
                )
            }
        }
    }
}
