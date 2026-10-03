package com.weightdiary.app.domain.model

/**
 * 图表可以画的指标。身高不进图表（不是趋势型数据），因此不在枚举内。
 */
enum class Metric {
    WEIGHT,
    BMI,
    BODY_FAT,
}
