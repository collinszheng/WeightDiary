package com.weightdiary.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 数值格式化。
 *
 * 一律用 [Locale.US] 固定小数点符号 —— 应用语言是中文，但某些系统区域设置会把
 * 小数点渲染成逗号，对体重数据来说是错的。
 */
fun Double.format1(): String = String.format(Locale.US, "%.1f", this)

/** 去掉无意义的小数位：身高 175.0 → `175`，68.5 → `68.5` */
fun Double.formatTrimmed(): String =
    if (this == toLong().toDouble()) toLong().toString() else format1()

fun Double?.format1OrPlaceholder(): String? = this?.format1()

/**
 * 时间标注。只描述"相对今天的关系"，具体文案交给 Composable 查 strings.xml，
 * 这样这个函数保持纯函数、可单测。
 */
sealed interface TimeLabel {
    data class Today(val time: String) : TimeLabel
    data class Yesterday(val time: String) : TimeLabel
    /** 更早的日期，形如 `6月28日 07:35` */
    data class Absolute(val text: String) : TimeLabel
}

fun Instant.toTimeLabel(zone: ZoneId = ZoneId.systemDefault()): TimeLabel {
    val dateTime = atZone(zone)
    val today = LocalDate.now(zone)
    val time = TIME_FORMAT.format(dateTime)
    return when (dateTime.toLocalDate()) {
        today -> TimeLabel.Today(time)
        today.minusDays(1) -> TimeLabel.Yesterday(time)
        else -> TimeLabel.Absolute("${dateTime.monthValue}月${dateTime.dayOfMonth}日 $time")
    }
}

private val DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日  HH:mm")

/** 编辑弹窗里显示的完整日期时间，如 `2026年6月30日  20:15` */
fun Instant.formatDateTime(zone: ZoneId = ZoneId.systemDefault()): String =
    DATE_TIME_FORMAT.format(atZone(zone))

private val RANGE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
private val X_LABEL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val X_LABEL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
private val X_LABEL_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")

/** 日期范围选择器里的起止日期，如 `2026年06月01日` */
fun Instant.formatRangeDate(zone: ZoneId = ZoneId.systemDefault()): String =
    RANGE_DATE_FORMAT.format(atZone(zone))

/**
 * 图表 X 轴标签。格式随粒度变：
 * 按条（日视图）显示时刻、按天显示月日、按月显示年月。
 */
fun Instant.formatXLabel(
    granularity: com.weightdiary.app.domain.chart.Granularity,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val dateTime = atZone(zone)
    return when (granularity) {
        com.weightdiary.app.domain.chart.Granularity.RAW -> X_LABEL_TIME.format(dateTime)
        com.weightdiary.app.domain.chart.Granularity.DAILY -> X_LABEL_DATE.format(dateTime)
        com.weightdiary.app.domain.chart.Granularity.MONTHLY -> X_LABEL_MONTH.format(dateTime)
    }
}
