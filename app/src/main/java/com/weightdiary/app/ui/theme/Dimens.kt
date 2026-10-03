package com.weightdiary.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 尺寸与间距。对应 [docs/02-设计规范.md §2.2 / §2.3 / §3]。
 *
 * 全部走 8dp 栅格的变体，不要在各处写魔法数字。
 */
@Immutable
data class WeightDiaryDimens(
    // ── 间距 ──
    val pageHorizontal: Dp = 16.dp,
    val cardGap: Dp = 12.dp,
    val cardPadding: Dp = 16.dp,
    val sectionGap: Dp = 20.dp,
    val metricCardGap: Dp = 10.dp,

    // ── 尺寸 ──
    val topBarHeight: Dp = 56.dp,
    /** 112dp 而不是设计稿暗示的 120dp：393dp 宽的屏幕上 120dp 会把第 3 张卡裁掉 */
    val metricCardWidth: Dp = 112.dp,
    val metricCardHeight: Dp = 96.dp,
    val goalCardHeight: Dp = 110.dp,
    val chartCardHeight: Dp = 356.dp,
    val chartPlotHeight: Dp = 220.dp,

    /** 无障碍最小触摸目标 */
    val minTouchTarget: Dp = 48.dp,

    // ── 悬浮的「添加」按钮 ──
    val fabSize: Dp = 56.dp,
    val fabMargin: Dp = 16.dp,

    // ── 圆角 ──
    val radiusCard: Dp = 20.dp,
    val radiusButton: Dp = 12.dp,
    val radiusTabContainer: Dp = 10.dp,
    val radiusField: Dp = 12.dp,
    val radiusSheetTop: Dp = 24.dp,
)

internal val DefaultDimens = WeightDiaryDimens()

val LocalWeightDiaryDimens = staticCompositionLocalOf { DefaultDimens }
