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
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.home.components.ChartCard
import com.weightdiary.app.ui.home.components.CirclePlusButton
import com.weightdiary.app.ui.home.components.GoalStatusCard
import com.weightdiary.app.ui.home.components.HistorySection
import com.weightdiary.app.ui.home.components.HomeSkeleton
import com.weightdiary.app.ui.home.components.MetricsRow
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 首页。
 *
 * 整体是 `Column + verticalScroll` 而不是 `LazyColumn`：历史记录区在首页只展示最近几条，
 * 数据量恒定，用不着懒加载的复用机制；而图表 Canvas 放进 LazyColumn 反而会因为
 * 回收重组带来无谓的重新测量。
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onMetricClick: (Metric) -> Unit,
    onAddRecord: () -> Unit,
    onEditProfile: () -> Unit,
    onChartTabSelected: (ChartTab) -> Unit,
    onShiftRange: (Int) -> Unit,
    onRecordClick: (RecordRow) -> Unit,
    onRecordDelete: (RecordRow) -> Unit,
    onViewAllRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    // 首屏还没拿到 Room 的第一帧时显示骨架屏，而不是先闪一下空状态
    if (state.isLoading) {
        HomeSkeleton(modifier = modifier)
        return
    }

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
            hasAnyRecord = !state.isEmpty,
            onTabSelected = onChartTabSelected,
            onShiftRange = onShiftRange,
            modifier = Modifier.padding(horizontal = dimen.pageHorizontal),
        )

        // ─────────── 历史记录 ───────────
        // 空状态时不显示这一块（设计规范 §6 退化场景）
        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(dimen.sectionGap))
            HistorySection(
                rows = state.history,
                totalCount = state.recordCount,
                hasMore = state.hasMoreRecords,
                onRowClick = onRecordClick,
                onRowDelete = onRecordDelete,
                onViewMore = onViewAllRecords,
                modifier = Modifier.padding(horizontal = dimen.pageHorizontal),
            )
        }

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
