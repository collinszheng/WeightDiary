package com.weightdiary.app.ui.home

import com.weightdiary.app.domain.model.BmiLevel
import com.weightdiary.app.domain.model.Metric
import java.time.Instant

/** 当前打开的底部弹窗。放在 UiState 里而不是 Compose 局部状态，旋转屏幕后不会丢。 */
enum class ActiveSheet {
    NONE,
    ADD_RECORD,
    EDIT_PROFILE,
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
)

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
}
