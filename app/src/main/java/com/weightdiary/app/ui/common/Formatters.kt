package com.weightdiary.app.ui.common

import com.weightdiary.app.domain.chart.ChartTab
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
private val SHORT_DATE_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")

/** 编辑弹窗里显示的完整日期时间，如 `2026年6月30日  20:15` */
fun Instant.formatDateTime(zone: ZoneId = ZoneId.systemDefault()): String =
    DATE_TIME_FORMAT.format(atZone(zone))

/** 图表气泡里的紧凑日期时间，如 `6月30日 20:15` */
fun Instant.formatShortDateTime(zone: ZoneId = ZoneId.systemDefault()): String =
    SHORT_DATE_TIME_FORMAT.format(atZone(zone))

private val RANGE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日")
private val X_LABEL_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val X_LABEL_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

/** 周一到周日 */
private const val WEEKDAY_CHARS = "一二三四五六日"

/** 日期范围选择器里的起止日期，如 `2026年06月01日` */
fun Instant.formatRangeDate(zone: ZoneId = ZoneId.systemDefault()): String =
    RANGE_DATE_FORMAT.format(atZone(zone))

private val MD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")
private val YMD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
private val YM_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月")

/**
 * 折线图上方的区间文案。
 *
 * **本年度不显示年份** —— 年份只在跨年时才有信息量，常驻只会把真正要看的
 * 「几月几号」挤小。各视图再各自精简：
 * - 日：只写当天是哪一天，不写「几号到几号」（同一天没有区间可言）
 * - 月：只写几月
 * - 年：只写几年
 */
fun rangeLabel(
    tab: ChartTab,
    start: Instant,
    end: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): String {
    val s = start.atZone(zone)
    val e = end.atZone(zone)
    val thisYear = s.year == today.year

    return when (tab) {
        ChartTab.DAY -> if (thisYear) MD_FORMAT.format(s) else YMD_FORMAT.format(s)

        ChartTab.WEEK ->
            if (thisYear) "${MD_FORMAT.format(s)} - ${MD_FORMAT.format(e)}"
            else "${YMD_FORMAT.format(s)} - ${YMD_FORMAT.format(e)}"

        ChartTab.MONTH -> if (thisYear) MONTH_FORMAT.format(s) else YM_FORMAT.format(s)

        ChartTab.YEAR -> "${s.year}年"

        // 区间长度不固定，跨年时两个端点都补上年份
        ChartTab.ALL ->
            if (thisYear && s.year == e.year) "${MD_FORMAT.format(s)} - ${MD_FORMAT.format(e)}"
            else "${YMD_FORMAT.format(s)} - ${YMD_FORMAT.format(e)}"
    }
}
/**
 * 图表 X 轴标签。格式随标签的**语义类别**变，而不是随粒度变 ——
 * 周视图下所有标签都是星期几，年视图下都是月份。
 *
 * 日期一律用 `M/d` 而不是 `M月d日`：后者在「总」视图那种要放 5 个标签的场合太占地方。
 */
fun Instant.formatXLabel(
    kind: com.weightdiary.app.domain.chart.XLabelKind,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val dateTime = atZone(zone)
    return when (kind) {
        com.weightdiary.app.domain.chart.XLabelKind.HOUR ->
            X_LABEL_TIME.format(dateTime.roundToMinuteKeepingDay())

        com.weightdiary.app.domain.chart.XLabelKind.WEEKDAY ->
            WEEKDAY_CHARS[dateTime.dayOfWeek.value - 1].toString()

        com.weightdiary.app.domain.chart.XLabelKind.DAY_OF_MONTH ->
            dateTime.dayOfMonth.toString()

        com.weightdiary.app.domain.chart.XLabelKind.MONTH_OF_YEAR ->
            // 裸数字而不是「3月」：带上「月」字后 12 个标签需要 822px，
            // 而绘图区正好 822px —— 会被抽稀成 6 个，而年视图要的就是十二个月
            dateTime.monthValue.toString()

        com.weightdiary.app.domain.chart.XLabelKind.DATE ->
            X_LABEL_DATE.format(dateTime)
    }
}

/**
 * 四舍五入到分钟。
 *
 * 「日」视图的 5 个标签落在 0% / 25% / 50% / 75% / 100%，也就是 05:59:59.999 这种时刻 ——
 * 直接格式化会显示成「05:59」，看着像算错了。
 * 但四舍五入到 24:00 会跨到第二天，那样一天的头尾都显示「00:00」，所以跨天时保持截断。
 */
private fun ZonedDateTime.roundToMinuteKeepingDay(): ZonedDateTime {
    val truncated = truncatedTo(ChronoUnit.MINUTES)
    val rounded = if (second >= 30) truncated.plusMinutes(1) else truncated
    return if (rounded.toLocalDate() == toLocalDate()) rounded else truncated
}
