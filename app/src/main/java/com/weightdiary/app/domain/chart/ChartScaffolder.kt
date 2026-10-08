package com.weightdiary.app.domain.chart

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

/**
 * Y 轴：4 条带数字的主刻度 + 若干条不带数字的次刻度。
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

    /** 全部刻度线（含主刻度） */
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

    /**
     * 这条水平参照线落在这个窗口里吗（严格内部，免得压在上下边框上）。
     *
     * **目标线与 BMI 阈值线必须共用这一个判据。** 从前目标线用的是另一套
     * （「纳入目标会不会把主步长顶大」），两者会在真机上打架：目标 65 明明落在
     * 窗口 [64, 70] 内，线却不画，还弹出一个方向说反的角标。
     * 详见 [docs/09-真机实测记录.md §6.6]。
     */
    fun showsReferenceLine(value: Double): Boolean = value > lower && value < upper
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
     * @param targetLine 目标体重。**始终纳入范围** —— 产品要求目标线必须在图上看得见
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
     * X 轴标签。**按视图分类**，不用统一的「5 个等分」——
     * 一周就是七个星期几、一年就是十二个月，这是日历直觉，一刀切地均分反而对不上。
     *
     * 位置取所属日历单位的**中点**（中午 / 当月 15 日）：标签落在它代表的那一天
     * 或那一月的中间，而不是起点，否则最后一个标签会离右边缘差一整格。
     */
    fun xLabels(tab: ChartTab, range: ChartRange, zone: ZoneId): XAxisLabels {
        val startDate = range.start.atZone(zone).toLocalDate()
        val endDate = range.end.atZone(zone).toLocalDate()

        return when (tab) {
            // 0 / 6 / 12 / 18 时，最后一个是区间末端（UI 会把它显示成 24:00）
            ChartTab.DAY -> XAxisLabels(
                instants = listOf(0, 6, 12, 18).map { hour ->
                    startDate.atStartOfDay(zone).plusHours(hour.toLong()).toInstant()
                } + range.end,
                kind = XLabelKind.HOUR,
            )

            // 周一至周日，7 个
            ChartTab.WEEK -> XAxisLabels(
                instants = (0..6).map { offset -> midday(startDate.plusDays(offset.toLong()), zone) },
                kind = XLabelKind.WEEKDAY,
            )

            // 1 / 10 / 20 / 月末
            ChartTab.MONTH -> XAxisLabels(
                instants = listOf(1, 10, 20, endDate.dayOfMonth)
                    .distinct()
                    .map { day -> midday(startDate.withDayOfMonth(day), zone) },
                kind = XLabelKind.DAY_OF_MONTH,
            )

            // 1 月至 12 月，12 个
            ChartTab.YEAR -> XAxisLabels(
                instants = (1..12).map { month ->
                    midday(startDate.withMonth(month).withDayOfMonth(15), zone)
                },
                kind = XLabelKind.MONTH_OF_YEAR,
            )

            // 区间长度不固定，只能等分
            ChartTab.ALL -> {
                val span = (range.end.toEpochMilli() - range.start.toEpochMilli()).toDouble()
                XAxisLabels(
                    instants = (0..4).map { i ->
                        Instant.ofEpochMilli((range.start.toEpochMilli() + span * i / 4).toLong())
                    },
                    kind = XLabelKind.DATE,
                )
            }
        }
    }

    /** 某一天的中午，用作该天的标签位置 */
    private fun midday(date: LocalDate, zone: ZoneId): Instant =
        date.atTime(12, 0).atZone(zone).toInstant()

    /** 次步长是否「干净」：最多一位小数，且不为 0 */
    private fun isClean(step: Double): Boolean {
        if (step <= 0.0) return false
        val scaled = step * 10.0
        return abs(scaled - Math.round(scaled).toDouble()) < 1e-6
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

    /** 供测试用：把期望的上界向下取整到步长整数倍 */
    internal fun snapDown(value: Double, step: Double): Double = floor(value / step) * step

    /** 供测试用 */
    internal fun snapUp(value: Double, step: Double): Double = ceil(value / step) * step
}
