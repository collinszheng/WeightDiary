package com.weightdiary.app.domain.model

/** BMI 分级结果。文案与颜色是 UI 层的事，由 `level` + `standard` 在资源里查表得到。 */
enum class Level {
    UNDERWEIGHT,
    NORMAL,
    OVERWEIGHT,
    OBESE,
}

/**
 * 一次 BMI 分级的完整结果。
 *
 * 注意：**不包含显示文案和颜色**。设计稿里 `BmiLevel` 曾带 `label` / `rangeLabel` / `color`，
 * 但那些是本地化与主题相关的 UI 关注点，放在 domain 会让 i18n 无从下手（见决策 C5）。
 * UI 层用 `level` + `standard` 去 `strings.xml` 取文案即可。
 */
data class BmiLevel(
    val level: Level,
    /** 四色条上的归一化位置，0f..1f */
    val sliderPos: Float,
    val standard: BmiStandard,
)
