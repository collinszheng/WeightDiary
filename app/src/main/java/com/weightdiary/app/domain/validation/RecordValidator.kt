package com.weightdiary.app.domain.validation

/** 会被校验的字段。用于把错误定位回具体的输入框。 */
enum class Field {
    WEIGHT,
    BODY_FAT,
    HEIGHT,
    MEASURED_AT,
}

/**
 * 校验失败的原因。
 *
 * 刻意**不带文案** —— 提示语在 UI 层用 `stringResource` 组装（决策 C5）。
 * 这里只携带组装所需的数据（字段、合法区间）。
 */
sealed interface ValidationError {
    data class Required(val field: Field) : ValidationError

    data class OutOfRange(
        val field: Field,
        val range: ClosedFloatingPointRange<Double>,
    ) : ValidationError

    data object FutureDate : ValidationError
}

/**
 * 录入校验。策略是**超出范围直接拒绝**（决策 B10）：保存按钮禁用并给出提示。
 *
 * 纯函数、无 Android 依赖，可直接 JVM 单测。
 */
object RecordValidator {

    val WEIGHT_KG = 20.0..500.0
    val BODY_FAT_PERCENT = 1.0..75.0
    val HEIGHT_CM = 50.0..260.0

    /** 体重必填 */
    fun validateWeight(value: Double?): ValidationError? {
        if (value == null || !value.isFinite()) return ValidationError.Required(Field.WEIGHT)
        if (value !in WEIGHT_KG) return ValidationError.OutOfRange(Field.WEIGHT, WEIGHT_KG)
        return null
    }

    /** 体脂率选填；null 表示留空，合法 */
    fun validateBodyFat(value: Double?): ValidationError? {
        if (value == null) return null
        if (!value.isFinite()) return ValidationError.OutOfRange(Field.BODY_FAT, BODY_FAT_PERCENT)
        if (value !in BODY_FAT_PERCENT) return ValidationError.OutOfRange(Field.BODY_FAT, BODY_FAT_PERCENT)
        return null
    }

    /** 身高选填；null 表示未设置，合法（此时 BMI 显示 `--`） */
    fun validateHeight(value: Double?): ValidationError? {
        if (value == null) return null
        if (!value.isFinite()) return ValidationError.OutOfRange(Field.HEIGHT, HEIGHT_CM)
        if (value !in HEIGHT_CM) return ValidationError.OutOfRange(Field.HEIGHT, HEIGHT_CM)
        return null
    }

    /** 目标体重选填，但一旦填写就要落在合法体重区间内 */
    fun validateTargetWeight(value: Double?): ValidationError? {
        if (value == null) return null
        if (!value.isFinite()) return ValidationError.OutOfRange(Field.WEIGHT, WEIGHT_KG)
        if (value !in WEIGHT_KG) return ValidationError.OutOfRange(Field.WEIGHT, WEIGHT_KG)
        return null
    }

    /**
     * 禁止未来时间（决策 B11）。
     *
     * 留 60 秒容差：设备时间与记录时间来自同一个时钟，但用户点保存前可能刚好跨过整分钟，
     * 不留容差会出现「选了当前时刻却报未来」的假失败。
     */
    fun validateMeasuredAt(
        instant: java.time.Instant,
        now: java.time.Instant = java.time.Instant.now(),
    ): ValidationError? =
        if (instant.isAfter(now.plusSeconds(60))) ValidationError.FutureDate else null
}
