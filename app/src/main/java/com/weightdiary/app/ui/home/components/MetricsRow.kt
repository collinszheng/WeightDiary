package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.home.HomeUiState
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import com.weightdiary.app.ui.theme.tabular

/**
 * 概览卡片行：体重 / BMI / 体脂率。
 *
 * 三张都**可点**，点了切换图表指标。
 *
 * 原先还有第四张「身高」卡，但它既不可点、数字也只能去设置里改，纯占位 —— 已移除。
 * 身高仍然可以在「编辑个人资料」里改。
 */
@Composable
fun MetricsRow(
    state: HomeUiState,
    onMetricClick: (Metric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimen = WeightDiaryTheme.dimens
    val placeholder = stringResource(R.string.value_placeholder)

    // 只剩三张卡，一屏放得下，不再需要 LazyRow 的横向滚动。
    // 均分整行宽度而不是固定 112dp —— 固定宽度会在右侧留下 20 多 dp 的空档。
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimen.metricCardGap),
    ) {
        MetricCard(
            label = stringResource(R.string.metric_weight),
            value = state.weightKg?.format1() ?: placeholder,
            selected = state.selectedMetric == Metric.WEIGHT,
            onClick = { onMetricClick(Metric.WEIGHT) },
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.metric_bmi),
            value = state.bmi?.format1() ?: placeholder,
            selected = state.selectedMetric == Metric.BMI,
            onClick = { onMetricClick(Metric.BMI) },
            modifier = Modifier.weight(1f),
        )
        MetricCard(
            label = stringResource(R.string.metric_body_fat),
            value = state.bodyFatPercent?.format1() ?: placeholder,
            selected = state.selectedMetric == Metric.BODY_FAT,
            onClick = { onMetricClick(Metric.BODY_FAT) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val shape = RoundedCornerShape(dimen.radiusCard)

    Column(
        modifier = modifier
            // widthIn/heightIn 而不是 width/height：系统字体放大到 1.5× 时，
            // 固定尺寸会把标签截断、把内容裁掉（设计规范 §8）
            .widthIn(min = dimen.metricCardWidth)
            .heightIn(min = dimen.metricCardHeight)
            .clip(shape)
            .background(if (selected) colors.cardSelectedFill else colors.cardFill)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) colors.textPrimary else colors.cardBorder,
                shape = shape,
            )
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = typo.cardLabel,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = value,
            style = typo.valueLarge.tabular,
            color = colors.textPrimary,
            maxLines = 1,
        )
    }
}

/**
 * 变化量文字：`↓ 0.3` / `↑ 0.8` / `— 0.0`。
 *
 * 首页概览卡已不再显示它（产品要求），但历史记录列表还在用，所以留在这里共用。
 * 箭头之外还有颜色，但**颜色不是唯一信息载体** —— 箭头本身也是语义（无障碍要求）。
 */
@Composable
internal fun DeltaText(deltaKg: Double, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    val flat = kotlin.math.abs(deltaKg) < 0.05
    val down = deltaKg < 0
    val magnitude = kotlin.math.abs(deltaKg).format1()

    val text = when {
        flat -> stringResource(R.string.delta_flat, magnitude)
        down -> stringResource(R.string.delta_down, magnitude)
        else -> stringResource(R.string.delta_up, magnitude)
    }
    val color = when {
        flat -> colors.textSecondary
        down -> colors.deltaDown
        else -> colors.deltaUp
    }

    Text(
        text = text,
        style = typo.caption,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}
