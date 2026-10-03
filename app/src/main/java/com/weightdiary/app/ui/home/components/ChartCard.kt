package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.chart.ChartTab
import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.formatRangeDate
import com.weightdiary.app.ui.common.formatXLabel
import com.weightdiary.app.ui.home.ChartUi
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import kotlin.math.roundToInt

/**
 * 图表卡片：Tab 栏 + 日期范围选择器 + 折线图。
 *
 * 手势职责划分（决策记录「冲突 1」）：
 * - **点左右箭头**才是切换日期范围，这是唯一入口
 * - **图表区域内左右滑动**只做视口滚动，不切范围
 */
@Composable
fun ChartCard(
    chart: ChartUi,
    metric: Metric,
    onTabSelected: (ChartTab) -> Unit,
    onShiftRange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val shape = RoundedCornerShape(dimen.radiusCard)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(dimen.chartCardHeight)
            .clip(shape)
            .background(colors.cardFill)
            .border(0.5.dp, colors.cardBorder, shape)
            .padding(dimen.cardPadding),
    ) {
        ChartTabBar(selected = chart.tab, onSelected = onTabSelected)

        Spacer(Modifier.height(10.dp))

        val start = chart.start
        val end = chart.end
        DateRangePicker(
            text = if (start != null && end != null) {
                stringResource(R.string.range_separator, start.formatRangeDate(), end.formatRangeDate())
            } else {
                ""
            },
            canGoBack = chart.canShiftBackward,
            canGoForward = chart.canShiftForward,
            onShift = onShiftRange,
        )

        Spacer(Modifier.height(12.dp))

        if (chart.hasData) {
            // 设计规范要求 Y 轴左侧「取整数」。但跨度很小时（比如 0.5 的步长）整数会全部重合，
            // 所以步长 < 1 时退回一位小数。
            val majorStep = chart.yAxis?.majorStep ?: 1.0
            WeightChart(
                chart = chart,
                yAxisTitle = stringResource(metric.axisTitleRes()),
                formatY = { value ->
                    if (majorStep >= 1.0 - 1e-9) value.roundToInt().toString() else value.format1()
                },
                formatX = { it.formatXLabel(chart.granularity) },
                goalLabel = chart.goalLine?.let { stringResource(R.string.goal_line, it.format1()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            ChartEmptyState(
                text = stringResource(R.string.chart_empty),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

private fun Metric.axisTitleRes(): Int = when (this) {
    Metric.WEIGHT -> R.string.axis_weight
    Metric.BMI -> R.string.axis_bmi
    Metric.BODY_FAT -> R.string.axis_body_fat
}

@Composable
private fun ChartTabBar(selected: ChartTab, onSelected: (ChartTab) -> Unit) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val containerShape = RoundedCornerShape(WeightDiaryTheme.dimens.radiusTabContainer)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(containerShape)
            .background(colors.divider)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ChartTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val itemShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(itemShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(colors.background)
                                .border(0.5.dp, colors.cardBorder, itemShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.Tab) { onSelected(tab) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(tab.labelRes()),
                    style = typo.tab,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

private fun ChartTab.labelRes(): Int = when (this) {
    ChartTab.DAY -> R.string.tab_day
    ChartTab.WEEK -> R.string.tab_week
    ChartTab.MONTH -> R.string.tab_month
    ChartTab.YEAR -> R.string.tab_year
    ChartTab.ALL -> R.string.tab_all
}

@Composable
private fun DateRangePicker(
    text: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onShift: (Int) -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val shape = RoundedCornerShape(WeightDiaryTheme.dimens.radiusTabContainer)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .background(colors.fieldFill)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RangeArrow(
            pointsLeft = true,
            enabled = canGoBack,
            description = stringResource(R.string.action_prev_range),
            onClick = { onShift(-1) },
        )

        Text(
            text = text,
            style = typo.caption,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        RangeArrow(
            pointsLeft = false,
            enabled = canGoForward,
            description = stringResource(R.string.action_next_range),
            onClick = { onShift(1) },
        )
    }
}

@Composable
private fun RangeArrow(
    pointsLeft: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val tint = if (enabled) colors.textSecondary else colors.textDisabled

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 1.5.dp.toPx()
            // 尖角在箭头的指向那一侧：向左的箭头尖角在左（0.15），向右的在右（0.85）
            val apex = if (pointsLeft) 0.15f else 0.85f
            val base = if (pointsLeft) 0.85f else 0.15f
            drawLine(
                color = tint,
                start = Offset(size.width * base, size.height * 0.15f),
                end = Offset(size.width * apex, size.height * 0.5f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = tint,
                start = Offset(size.width * apex, size.height * 0.5f),
                end = Offset(size.width * base, size.height * 0.85f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
