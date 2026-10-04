package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.model.Level
import java.time.Instant

/** 图表顶部的时间跨度 Tab */
enum class ChartTab {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL,
}

/**
 * 数据点粒度。
 *
 * - [RAW]：直接用原始记录，不聚合（「日」视图）
 * - [DAILY]：每天一个点，取**当日最低值**那条记录
 * - [MONTHLY]：每月一个点，取该月**平均值**
 */
enum class Granularity {
    RAW,
    DAILY,
    MONTHLY,
}

/**
 * X 轴标签的语义类别。文案（「一」「10」「3月」…）由 UI 层按这个类别组装。
 */
enum class XLabelKind {
    /** 时刻，如 `06:00`（日视图） */
    HOUR,
    /** 星期几，如 `一`（周视图） */
    WEEKDAY,
    /** 几号，如 `10`（月视图） */
    DAY_OF_MONTH,
    /** 几月，如 `3月`（年视图） */
    MONTH_OF_YEAR,
    /** 完整日期，如 `9/9`（总视图） */
    DATE,
}

/** X 轴标签：位置 + 语义类别 */
data class XAxisLabels(
    val instants: List<Instant>,
    val kind: XLabelKind,
)

/** 图表可视区间。`start` 与 `end` 都是**闭区间**（`end` 为当天 23:59:59.999）。 */
data class ChartRange(
    val start: Instant,
    val end: Instant,
) {
    val daySpan: Long
        get() = ((end.toEpochMilli() - start.toEpochMilli()) / 86_400_000L) + 1
}

/**
 * 一条绘图点……
 */
data class ChartPoint(
    val time: Instant,
    val value: Double,
    val sourceRecordId: Long,
)

/**
 * 图表上的一条水平参照线 —— BMI 分级阈值换算成体重后的位置。
 *
 * **只放数值与语义，不放文案与颜色**：ViewModel 没有 Context，文案一律由 Composable
 * 查 strings.xml 组装（决策 C5），颜色同理走 `WeightDiaryTheme`。
 *
 * 是否把它画出来由调用方决定：**只有落进当前 Y 轴窗口的才会出现在列表里**，
 * 视野外的一律不画，也绝不为了画它去撑大坐标轴。
 */
data class ReferenceLine(
    val value: Double,
    /** 这条线是**哪一档的起点**：18.5 起为「正常」、24.0 起为「超重」、28.0 起为「肥胖」 */
    val opensLevel: Level,
)
