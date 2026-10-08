package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.bmi.BmiClassifier
import com.weightdiary.app.domain.model.BmiStandard

/**
 * BMI 分级阈值 → 图表上的水平参照线。
 *
 * 放在 domain 层而不是 ViewModel 的私有函数里，是为了**能被单测直接覆盖** ——
 * 这段逻辑有两个容易写错的地方：单位换算（身高是 cm，BMI 公式要 m）和视野过滤。
 */
object ReferenceLines {

    /**
     * 把三条 BMI 分级阈值换算成体重，并**只保留落进 [axis] 窗口的那些**。
     *
     * 视野外的一律不画，也绝不为了画它去撑大坐标轴 —— 那会把主步长从 1 顶到 3，
     * 窗口宽度从 3 个单位涨到 9 个，折线振幅掉到三分之一。
     *
     * 实际效果就是：**体重贴近某个临界值时那条线自然出现，离得远就不出现。**
     * 这正是产品要的 —— 「在临界值上时能清楚看到临界值在哪」。
     *
     * @param heightCm 没有身高就算不出体重（`体重 = BMI × 身高²`），返回空列表。
     *   宁可这个功能不出现，也不能画一条错位置的线。
     */
    fun visibleIn(
        axis: YAxis,
        heightCm: Double?,
        standard: BmiStandard = BmiStandard.CHINA,
    ): List<ReferenceLine> {
        val heightM = heightCm?.takeIf { it > 0.0 }?.div(100.0) ?: return emptyList()
        val heightSquared = heightM * heightM
        if (!heightSquared.isFinite() || heightSquared <= 0.0) return emptyList()

        return BmiClassifier.thresholds(standard)
            .map { ReferenceLine(value = it.bmi * heightSquared, opensLevel = it.opensLevel) }
            // 与目标线共用同一个可见性判据，见 YAxis.showsReferenceLine
            .filter { axis.showsReferenceLine(it.value) }
    }
}
