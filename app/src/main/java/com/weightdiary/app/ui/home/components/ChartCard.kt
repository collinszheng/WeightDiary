package com.weightdiary.app.ui.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.weightdiary.app.domain.chart.Granularity
import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.formatShortDateTime
import com.weightdiary.app.ui.common.formatYearMonth
import com.weightdiary.app.ui.common.formatXLabel
import com.weightdiary.app.ui.common.rangeLabel
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
    /** 一条记录都没有。用于区分「首次使用」与「这段时间没数据」两种空状态 */
    hasAnyRecord: Boolean,
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
            // 区间文案按视图精简：本年度不带年份，日只写当天、月只写几月、年只写几年
            text = if (start != null && end != null) {
                rangeLabel(chart.tab, start, end)
            } else {
                ""
            },
            canGoBack = chart.canShiftBackward,
            canGoForward = chart.canShiftForward,
            onShift = onShiftRange,
        )

        Spacer(Modifier.height(12.dp))

        if (chart.hasData) {
            val unit = when (metric) {
                Metric.WEIGHT -> stringResource(R.string.unit_kg)
                Metric.BODY_FAT -> stringResource(R.string.unit_percent)
                Metric.BMI -> ""
            }
            val axisTitle = stringResource(metric.axisTitleRes())
            val chartDesc = stringResource(R.string.cd_chart, axisTitle, chart.points.size)
            // 切换 Tab / 日期范围时淡出淡入（设计规范 §7，200ms）。
            // 以 chart 本身作为 targetState：它是 data class，内容相同就不会触发动画。
            Crossfade(
                targetState = chart,
                animationSpec = tween(durationMillis = 200),
                label = "chart",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics(mergeDescendants = true) { contentDescription = chartDesc },
            ) { shown ->
                WeightChart(
                    chart = shown,
                    yAxisTitle = axisTitle,
                    // 主步长只从整数里选，所以四个刻度必然都是整数（设计规范 §4.4）
                    formatY = { value -> value.roundToInt().toString() },
                    formatX = { time, kind -> time.formatXLabel(kind) },
                    endOfDayLabel = stringResource(R.string.axis_end_of_day),
                    formatTooltip = { point ->
                        val value = point.value.format1()
                        val withUnit = if (unit.isEmpty()) value else "$value $unit"
                        // 月聚合点的横坐标是「月中」，不是真实测量时刻 —— 只显示到月，免得误导
                        val whenText = if (shown.granularity == Granularity.MONTHLY) {
                            point.time.formatYearMonth()
                        } else {
                            point.time.formatShortDateTime()
                        }
                        "$withUnit · $whenText"
                    },
                    goalLabel = shown.goalLine?.let { stringResource(R.string.goal_line, it.format1()) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else if (!hasAnyRecord) {
            // 一条都还没有 —— 该做的是去添加，不是换时间段
            FirstUseEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            // 有数据但当前区间没有 —— 该做的是换时间段
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整行不再有灰底 —— 底色改由两个箭头各自携带，用来表示「能不能点」。
            // 高度取 48dp 是为了容下箭头的触摸区（无障碍要求）
            .heightIn(min = WeightDiaryTheme.dimens.minTouchTarget),
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
            // 字体放大时日期范围放不下，允许折成两行，而不是被箭头挤掉尾部的日期
            maxLines = 2,
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

/**
 * 日期范围箭头。
 *
 * 底色是**可用性的指示**：可用时有灰色圆角矩形，到头了就没有底色、只剩淡箭头。
 * 这比只把箭头调淡更容易区分 —— 尤其是左右一个可用、一个不可用时。
 */
@Composable
private fun RangeArrow(
    pointsLeft: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val tint = if (enabled) colors.textSecondary else colors.textDisabled
    val chipShape = RoundedCornerShape(dimen.radiusTabContainer)

    Box(
        modifier = Modifier
            // 触摸区 48dp（无障碍要求），视觉上的圆角矩形只有 32dp
            .size(dimen.minTouchTarget)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .then(if (enabled) Modifier.background(colors.fieldFill, chipShape) else Modifier),
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
}
