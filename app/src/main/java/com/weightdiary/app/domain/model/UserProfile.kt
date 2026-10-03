package com.weightdiary.app.domain.model

import java.time.LocalTime

/**
 * 用户档案。低频变更，存在 DataStore 里而不是 Room 表。
 */
data class UserProfile(
    val heightCm: Double? = null,
    val targetWeightKg: Double? = null,
    /**
     * 设置目标体重时的体重，作为目标进度条的起点。
     * 为空时进度条不显示（见设计规范 §4.3）。
     */
    val targetSetAtWeightKg: Double? = null,
    val bmiStandard: BmiStandard = BmiStandard.CHINA,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val reminderEnabled: Boolean = false,
    val reminderTime: LocalTime? = null,
    /** 首次启动的身高引导是否已经走完（填写或跳过都算） */
    val onboardingCompleted: Boolean = false,
) {
    val hasHeight: Boolean get() = heightCm != null && heightCm > 0.0
    val hasTarget: Boolean get() = targetWeightKg != null && targetWeightKg > 0.0
}
