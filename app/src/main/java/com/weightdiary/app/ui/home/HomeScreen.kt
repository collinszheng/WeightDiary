package com.weightdiary.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.chart.ChartTab
import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.ui.home.components.ChartCard
import com.weightdiary.app.ui.home.components.CirclePlusButton
import com.weightdiary.app.ui.home.components.GoalStatusCard
import com.weightdiary.app.ui.home.components.MetricsRow
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 首页。
 *
 * M1 只实现上半屏（导航栏 + 概览卡片行 + 目标与水平卡片）；
 * 图表卡片在 M3、历史记录列表在 M4 加入，届时整体换成 `LazyColumn`。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onMetricClick: (Metric) -> Unit,
    onAddRecord: () -> Unit,
    onEditProfile: () -> Unit,
    onChartTabSelected: (ChartTab) -> Unit,
    onShiftRange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // ─────────── 顶部导航栏 ───────────
        // 背景已由外层 Column 铺满（含状态栏区域），这里只把**内容**下移避开状态栏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(dimen.topBarHeight),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = typo.screenTitle,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            CirclePlusButton(
                onClick = onAddRecord,
                // 让 28dp 的视觉圆圈落在距右边缘 16dp 处：
                // 触摸区是 48dp，多出来的 (48-28)/2 = 10dp 要从 padding 里扣掉
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = dimen.pageHorizontal - (dimen.minTouchTarget - 28.dp) / 2),
            )
        }

        Spacer(Modifier.height(8.dp))

        // ─────────── 概览卡片行 ───────────
        MetricsRow(
            state = state,
            onMetricClick = onMetricClick,
        )

        Spacer(Modifier.height(dimen.cardGap))

        // ─────────── 目标与水平卡片 ───────────
        GoalStatusCard(
            state = state,
            onEditProfile = onEditProfile,
            goalReached = state.isGoalReached(),
            modifier = Modifier.padding(horizontal = dimen.pageHorizontal),
        )

        Spacer(Modifier.height(dimen.cardGap))

        // ─────────── 图表卡片 ───────────
        ChartCard(
            chart = state.chart,
            metric = state.selectedMetric,
            onTabSelected = onChartTabSelected,
            onShiftRange = onShiftRange,
            modifier = Modifier.padding(horizontal = dimen.pageHorizontal),
        )

        // 历史记录列表（M4）会接在这里
        Spacer(Modifier.height(dimen.sectionGap))

        // 避让底部手势条
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/**
 * 是否已达成目标。
 *
 * 目前只用于把卡片底色染成强调色。M5 会在此基础上加一次性微动效（决策 B9）。
 */
private fun HomeUiState.isGoalReached(): Boolean {
    val target = goal.targetWeightKg ?: return false
    val current = goal.currentWeightKg ?: return false
    // 减重场景：当前体重已降到目标及以下
    return current <= target
}
