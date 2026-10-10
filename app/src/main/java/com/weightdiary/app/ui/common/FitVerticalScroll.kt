package com.weightdiary.app.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 竖向滚动，但**内容装得下时不让滑**。
 *
 * 为什么要有它：`verticalScroll` 默认永远可滑。内容明明全在屏上，手指一拉还是会有一下回弹 ——
 * 那一下会让人以为下面还藏着东西，界面也显得松垮。
 *
 * 判定靠 `ScrollState.maxValue`（「最多还能滚多远」），它是布局测完才有值的状态：
 * 装得下就是 0。首帧它还是 `Int.MAX_VALUE`（尚未测量），所以理论上有一帧可滑，实际碰不到。
 *
 * @param ignoreTrailingPx 内容**末尾那段看不见的净空**有多高（像素）。
 *   典型是给右下角悬浮按钮留的空间 —— 那一段在屏幕上没有任何东西，所以它不该单独构成
 *   「可以滑」的理由：只要它比实际能滚的距离还大，说明真正的内容一屏放得下。
 *   首页把末尾两个 Spacer 的高度量出来传进来；没有这种净空的页面用默认值 0 即可。
 */
@Composable
fun Modifier.fitVerticalScroll(
    state: ScrollState = rememberScrollState(),
    ignoreTrailingPx: Int = 0,
): Modifier = verticalScroll(state, enabled = state.maxValue > ignoreTrailingPx)
