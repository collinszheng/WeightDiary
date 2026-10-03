package com.weightdiary.app.domain.chart

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Y 轴：4 条带数字的主刻度 + 若干条不带数字的次刻度（默认共 13 条）。
 *
 * 见 [docs/03-技术设计.md §4.4]。
 */
data class YAxis(
    val lower: Double,
    val upper: Double,
    val majorStep: Double,
    val subdivisions: Int,
) {
    /** 4 条带数字的主刻度 */
    val majorTicks: List<Double> = (0..3).map { lower + it * majorStep }

    val minorStep: Double get() = majorStep / subdivisions

    /** 全部刻度线（含主刻度），默认 13 条 */
    val allTicks: List<Double>
        get() {
            val total = 3 * subdivisions
            return (0..total).map { lower + it * minorStep }
        }

    /** 只含次刻度（不含与主刻度重合的位置） */
    val minorTicks: List<Double>
        get() = allTicks.filterIndexed { index, _ -> index % subdivisions != 0 }

    fun normalize(value: Double): Double =
        if (upper - lower == 0.0) 0.5 else ((value - lower) / (upper - lower)).coerceIn(0.0, 1.0)
}

object ChartScaffolder {

    /**
     * 主步长**只从整数里选** —— 这样四条主刻度的数字必然是整数（设计规范 §2.4 / §4.4）。
     * 最小步长是 1，所以刻度窗口至少 3 个单位宽。
     */
    private val NICE_INT_STEPS = listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

    /** 数据上下各留这么多比例的空隙，免得最低/最高点正好压在轴线上（圆点会被裁掉一半） */
    private const val PADDING_RATIO = 0.08

    /** 次刻度的候选分段数，优先取能整除出「干净」次步长的那个 */
    private val SUBDIVISION_CANDIDATES = listOf(4, 3, 5, 2)

    /**
     * 构造 Y 轴。
     *
     * @param values   参与绘图的数据值
     * @param targetLine 目标体重。是否纳入由调用方先用 [shouldIncludeTarget] 判断过
     */
    fun buildYAxis(values: List<Double>, targetLine: Double? = null): YAxis {
        val extremes = buildList {
            addAll(values.filter { it.isFinite() })
            targetLine?.takeIf { it.isFinite() }?.let { add(it) }
        }

        // 退化：没有任何可用的值 → 给一个 0..3 的默认区间
        if (extremes.isEmpty()) {
            return YAxis(lower = 0.0, upper = 3.0, majorStep = 1.0, subdivisions = 4)
        }

        var min = extremes.min()
        var max = extremes.max()

        // 退化：只有一个值或全部相同 —— 上下各扩 1，否则跨度为 0
        if (max - min < 1e-9) {
            min -= 1.0
            max += 1.0
        }

        // 先给数据留出边距，再据此选步长与对齐 —— 顺序不能反，
        // 否则「68.0–69.0」会得到紧贴的 68/69/70/71 而不是留了边距的 67/68/69/70
        val pad = (max - min) * PADDING_RATIO
        val lo = min - pad
        val hi = max + pad
        val center = (lo + hi) / 2.0

        // 候选步长按升序取第一个「能覆盖住留边后的数据、又能整除出干净次步长」的。
        //
        // 下界的取法是 **吸附到离「让数据居中」最近的那个步长整数倍**，
        // 而不是 floor(lo / M) * M —— 后者只会往下掉、从不往上抬，
        // 结果就是数据永远贴在窗口下半部分，折线看着很靠下。
        //
        // 覆盖判据仍用 lo / hi：居中优先，但绝不能把数据挤出窗口。
        val candidates = candidateSteps()
        val majorStep = candidates.firstOrNull { step ->
            val lower = snapCentered(center, step)
            lower <= lo + 1e-9 &&
                lower + 3 * step >= hi - 1e-9 &&
                SUBDIVISION_CANDIDATES.any { isClean(step / it) }
        } ?: candidates.last()

        val subdivisions = SUBDIVISION_CANDIDATES.first { isClean(majorStep / it) }

        val lower = snapCentered(center, majorStep)
        val upper = lower + 3 * majorStep

        return YAxis(lower = lower, upper = upper, majorStep = majorStep, subdivisions = subdivisions)
    }

    /**
     * 把窗口下界吸附到步长的整数倍，且尽量让 [center] 落在窗口正中。
     *
     * 理想下界是 `center - 1.5M`；把它四舍五入到最近的 `M` 的整数倍即可。
     */
    private fun snapCentered(center: Double, step: Double): Double =
        Math.round((center - 1.5 * step) / step).toDouble() * step

    /**
     * 目标线要不要纳入 Y 轴范围（决策 B8）。
     *
     * 无条件纳入的话，目标离数据很远时 Y 轴会被撑得很开、折线压成一条平线，趋势完全看不出来。
     * 所以只在与数据区间的距离不超过 [TARGET_STRETCH_LIMIT_RATIO] 时才纳入，
     * 否则改为在图表边缘画一个方向箭头。
     */
    const val TARGET_STRETCH_LIMIT_RATIO = 0.30

    fun shouldIncludeTarget(dataValues: List<Double>, target: Double): Boolean {
        val finite = dataValues.filter { it.isFinite() }
        if (finite.isEmpty() || !target.isFinite()) return true
        val dataMin = finite.min()
        val dataMax = finite.max()
        val span = dataMax - dataMin
        if (span < 1e-9) return true
        val gap = when {
            target < dataMin -> dataMin - target
            target > dataMax -> target - dataMax
            else -> 0.0
        }
        return gap <= span * TARGET_STRETCH_LIMIT_RATIO
    }

    /**
     * 全部候选步长：`{1, 2, 3, 4, 5, 6, 8, 10} × 10^k`（k = 0..2），升序，全是整数。
     *
     * 不能先把量级归一到 10 的幂再乘 —— 那样跨度 12 时候选会从 5 起步，把 4 漏掉。
     */
    private fun candidateSteps(): List<Double> =
        (0..2).flatMap { k ->
            val scale = 10.0.pow(k)
            NICE_INT_STEPS.map { it * scale }
        }.distinct().sorted()

    /**
     * 5 个日期标签的时间点，**按位置四等分**。
     *
     * 不能按数据点序号等分，也不能吸附到最近的数据点 —— 数据有缺口时那两种做法会让间距忽宽忽窄。
     * 见 [docs/03-技术设计.md §4.5]。
     */
    fun xLabelPositions(range: ChartRange, count: Int = 5): List<Instant> {
        if (count <= 1) return listOf(range.start)
        val span = (range.end.toEpochMilli() - range.start.toEpochMilli()).toDouble()
        return (0 until count).map { i ->
            Instant.ofEpochMilli(range.start.toEpochMilli() + (span * i / (count - 1)).toLong())
        }
    }

    /**
     * X 轴刻度位。**按日历单位铺满**（缺失日期也保留刻度位），
     * 虽然不渲染，但标签定位依赖它。
     */
    fun xTickPositions(range: ChartRange, tab: ChartTab, zone: ZoneId): List<Instant> = when (tab) {
        ChartTab.DAY -> (0..23).map { hour ->
            range.start.atZone(zone).withHour(hour).withMinute(0).withSecond(0).withNano(0).toInstant()
        }

        ChartTab.WEEK, ChartTab.MONTH -> {
            val startDate = range.start.atZone(zone).toLocalDate()
            val endDate = range.end.atZone(zone).toLocalDate()
            generateSequence(startDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(endDate) }
                .map { it.atStartOfDay(zone).toInstant() }
                .toList()
        }

        ChartTab.YEAR, ChartTab.ALL -> {
            val startMonth = range.start.atZone(zone).toLocalDate().withDayOfMonth(1)
            val endMonth = range.end.atZone(zone).toLocalDate().withDayOfMonth(1)
            generateSequence(startMonth) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(endMonth) }
                .map { it.atStartOfDay(zone).toInstant() }
                .toList()
        }
    }

    /** 次步长是否「干净」：最多一位小数，且不为 0 */
    private fun isClean(step: Double): Boolean {
        if (step <= 0.0) return false
        val scaled = step * 10.0
        return abs(scaled - Math.round(scaled).toDouble()) < 1e-6
    }

    /** 供测试用：把期望的上界向下取整到步长整数倍 */
    internal fun snapDown(value: Double, step: Double): Double = floor(value / step) * step

    /** 供测试用 */
    internal fun snapUp(value: Double, step: Double): Double = ceil(value / step) * step
}
