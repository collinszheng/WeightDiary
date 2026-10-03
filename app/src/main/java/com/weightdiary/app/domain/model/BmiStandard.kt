package com.weightdiary.app.domain.model

/**
 * BMI 分级标准。
 *
 * 三个字段都是**开区间上限**（`bmi < limit` 时落入该档）：
 * - `bmi < underweightLimit`             → 较轻
 * - `bmi < normalLimit`                  → 标准
 * - `bmi < overweightLimit`              → 超重
 * - 否则                                  → 肥胖
 *
 * v1 固定使用 [CHINA]，不向用户暴露设置项；保留 [WHO] 是为了避免将来加标准时改数据模型。
 */
enum class BmiStandard(
    val underweightLimit: Double,
    val normalLimit: Double,
    val overweightLimit: Double,
) {
    /** 中国标准 WS/T 428-2013 */
    CHINA(18.5, 24.0, 28.0),

    /** WHO 国际标准 */
    WHO(18.5, 25.0, 30.0),
}
