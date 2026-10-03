package com.weightdiary.app.domain.bmi

import kotlin.math.round

/**
 * BMI = 体重(kg) / 身高(m)²
 */
object BmiCalculator {

    /**
     * @return BMI 保留 1 位小数；身高或体重非法时返回 `null`（UI 显示 `--`）
     */
    fun calculate(weightKg: Double, heightCm: Double): Double? {
        if (!weightKg.isFinite() || !heightCm.isFinite()) return null
        if (weightKg <= 0.0 || heightCm <= 0.0) return null

        val heightM = heightCm / 100.0
        val raw = weightKg / (heightM * heightM)
        if (!raw.isFinite()) return null

        return round(raw * 10.0) / 10.0
    }
}
