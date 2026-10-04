package com.weightdiary.app.domain.bmi

import com.weightdiary.app.domain.model.BmiLevel
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.model.Level

/**
 * 一条 BMI 分级阈值：BMI 达到 [bmi] 就进入 [opensLevel] 这一档。
 *
 * 它是「分级」而不是「区间」，因为它只描述边界在哪、开启哪一档，
 * 不关心上面一档到哪结束（那是下一条阈值的事）。
 */
data class BmiThreshold(
    val bmi: Double,
    val opensLevel: Level,
)

/**
 * BMI 分级与四色条滑块定位。
 *
 * 四色条**四段等宽**（见决策 B13），所以滑块位置**不能**按 BMI 线性映射，
 * 必须用「段索引 + 段内比例」：
 *
 * ```
 * pos = (段索引 + 段内比例) / 4
 * ```
 *
 * 若误用线性映射 `(bmi - 15) / 20`，滑块会和色块完全对不上。
 */
object BmiClassifier {

    /** 滑块的兜底显示区间。BMI 超出这个范围时滑块贴边。 */
    const val SLIDER_MIN = 15.0
    const val SLIDER_MAX = 35.0

    /** 四色条的段数 */
    const val SEGMENT_COUNT = 4

    fun classify(bmi: Double, standard: BmiStandard = BmiStandard.CHINA): BmiLevel {
        require(bmi.isFinite()) { "bmi 必须是有限值，实际为 $bmi" }
        return BmiLevel(
            level = levelOf(bmi, standard),
            sliderPos = sliderPosition(bmi, standard),
            standard = standard,
        )
    }

    /**
     * 分级。边界值归入**下一档的起点**——例如 `24.0` 算「超重」而不是「标准」的末尾，
     * 与分级文案（24.0 起算超重）保持一致。
     */
    fun levelOf(bmi: Double, standard: BmiStandard = BmiStandard.CHINA): Level = when {
        bmi < standard.underweightLimit -> Level.UNDERWEIGHT
        bmi < standard.normalLimit -> Level.NORMAL
        bmi < standard.overweightLimit -> Level.OVERWEIGHT
        else -> Level.OBESE
    }

    /**
     * 四段的数值区间，语义为 `[first, second)`：`first` 含，`second` 不含。
     * 顺序与四色条从左到右一致：黄 → 绿 → 浅红 → 深红。
     */
    fun sliderBounds(standard: BmiStandard = BmiStandard.CHINA): List<Pair<Double, Double>> = listOf(
        SLIDER_MIN to standard.underweightLimit,
        standard.underweightLimit to standard.normalLimit,
        standard.normalLimit to standard.overweightLimit,
        standard.overweightLimit to SLIDER_MAX,
    )

    /**
     * 三条分级阈值，各自标注「从这一档起算」。
     *
     * 图表把它们画成水平参照线（换算成体重由调用方乘身高² 完成）。
     * 命名取向是**这条线开启了哪一档** —— `24.0` 那条读作「超重 73.4」，
     * 即「超过这条线就是超重」，比读作「正常上限」更直接。
     */
    fun thresholds(standard: BmiStandard = BmiStandard.CHINA): List<BmiThreshold> = listOf(
        BmiThreshold(standard.underweightLimit, Level.NORMAL),
        BmiThreshold(standard.normalLimit, Level.OVERWEIGHT),
        BmiThreshold(standard.overweightLimit, Level.OBESE),
    )

    /** @return 0f..1f，四色条上的归一化位置 */
    fun sliderPosition(bmi: Double, standard: BmiStandard = BmiStandard.CHINA): Float {
        val bounds = sliderBounds(standard)
        val index = bounds.indexOfFirst { bmi >= it.first && bmi < it.second }.let { found ->
            when {
                found >= 0 -> found
                bmi < SLIDER_MIN -> 0
                else -> bounds.lastIndex
            }
        }
        val (start, end) = bounds[index]
        val span = end - start
        val within = if (span <= 0.0) 0.0 else ((bmi - start) / span).coerceIn(0.0, 1.0)
        return ((index + within) / SEGMENT_COUNT).toFloat()
    }
}
