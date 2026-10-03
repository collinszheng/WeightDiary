package com.weightdiary.app.ui.home

import com.weightdiary.app.domain.chart.ChartPoint
import com.weightdiary.app.domain.chart.ChartTab
import com.weightdiary.app.domain.chart.Granularity
import com.weightdiary.app.domain.chart.XAxisLabels
import com.weightdiary.app.domain.chart.XLabelKind
import com.weightdiary.app.domain.chart.YAxis
import com.weightdiary.app.domain.model.BmiLevel
import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.domain.record.RecordRow
import java.time.Instant

/** 当前打开的底部弹窗。放在 UiState 里而不是 Compose 局部状态，旋转屏幕后不会丢。 */
enum class ActiveSheet {
    NONE,
    ADD_RECORD,
    EDIT_PROFILE,
    ALL_RECORDS,
}

/**
 * 首页 UI 状态。
 *
 * 刻意**只放原始数据，不放显示文案** —— ViewModel 没有 Context，
 * 文案一律由 Composable 用 `stringResource` 组装（决策 C5：字符串走 strings.xml）。
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    /** 无任何记录时为 true，用于空状态 */
    val isEmpty: Boolean = true,
    val recordCount: Int = 0,

    /** 当前选中的图表指标 */
    val selectedMetric: Metric = Metric.WEIGHT,

    // ── 概览卡片的值（都是"最新一条"。与图表的"当日最低值"有意不同，见决策记录 冲突 2）──
    val weightKg: Double? = null,
    val bmi: Double? = null,
    val bodyFatPercent: Double? = null,
    val heightCm: Double? = null,
    val latestMeasuredAt: Instant? = null,
    /** 与上一条记录的差值。正数为增、负数为减；只有一条记录时为 null */
    val deltaKg: Double? = null,

    val goal: GoalUi = GoalUi(),
    /** 无身高时为 null，UI 显示 `--` */
    val level: BmiLevel? = null,

    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    /** 首次启动的身高引导。填写或跳过之后都不再出现 */
    val showOnboarding: Boolean = false,

    val chart: ChartUi = ChartUi(),

    /** 首页历史列表。只放最近几条，全部记录在 [allRecords] */
    val history: List<RecordRow> = emptyList(),
    /** 「全部记录」弹窗用的全量列表。一次性算好，不引入 Paging（设计规范 §5.2） */
    val allRecords: List<RecordRow> = emptyList(),
    /** 正在编辑的记录。非空时 [ActiveSheet.ADD_RECORD] 进入编辑模式 */
    val editing: RecordRow? = null,
) {
    /** 首页历史区默认展示条数（设计规范 §4.5：默认展示最近 2–3 条） */
    val historyLimit: Int get() = HOME_HISTORY_LIMIT

    val hasMoreRecords: Boolean get() = allRecords.size > history.size
}

const val HOME_HISTORY_LIMIT = 3

/**
 * 图表区的状态。
 *
 * 时间用 `Instant` 而不是格式化好的字符串 —— 文案由 Composable 查 strings.xml 组（决策 C5）。
 */
data class ChartUi(
    val tab: ChartTab = ChartTab.WEEK,
    val granularity: Granularity = Granularity.DAILY,
    /** 可视区间。null 表示还没算出来（首帧） */
    val start: Instant? = null,
    val end: Instant? = null,
    val points: List<ChartPoint> = emptyList(),
    val yAxis: YAxis? = null,
    /** X 轴标签。位置与语义类别都由按 Tab 分类的规则给出，不是统一的「5 个等分」 */
    val xLabels: XAxisLabels = XAxisLabels(emptyList(), XLabelKind.DATE),
    /** 画虚线的目标值。已确认纳入 Y 轴范围才会非空 */
    val goalLine: Double? = null,
    /** 目标被判定为「离数据太远、不撑开 Y 轴」时，在图表边缘画方向箭头（决策 B8） */
    val goalOffscreen: GoalOffscreen = GoalOffscreen.NONE,
    val canShiftForward: Boolean = false,
    val canShiftBackward: Boolean = false,
) {
    val hasData: Boolean get() = points.isNotEmpty()
}

/** 目标线跑到轴外时，箭头指向哪边 */
enum class GoalOffscreen {
    NONE,
    BELOW,
    ABOVE,
}

data class GoalUi(
    /** 最新体重，作为目标卡片的"当前体重" */
    val currentWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    /** 设置目标时的体重，进度条的起点。改身高时不能被覆盖 */
    val startWeightKg: Double? = null,
    /** 目标进度 0f..1f。未设目标或无起点体重时为 null，此时不显示进度条 */
    val progress: Float? = null,
) {
    val hasTarget: Boolean get() = targetWeightKg != null
}

/** 一次性事件（Snackbar 之类），用 Channel 发，避免旋转屏幕后重放。 */
sealed interface HomeEvent {
    data class RecordSaved(val id: Long, val weightKg: Double) : HomeEvent
    data object ProfileSaved : HomeEvent
    data object RecordUpdated : HomeEvent
}
