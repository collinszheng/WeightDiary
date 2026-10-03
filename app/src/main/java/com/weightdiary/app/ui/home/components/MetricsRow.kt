package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
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
import com.weightdiary.app.ui.common.TimeLabel
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.formatTrimmed
import com.weightdiary.app.ui.common.toTimeLabel
import com.weightdiary.app.ui.home.HomeUiState
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import com.weightdiary.app.ui.theme.tabular
import kotlin.math.abs

/**
 * 概览卡片行。横向可滑动，四张卡片分别是体重 / BMI / 体脂率 / 身高。
 *
 * 前三张可点，点了切换图表指标；**身高卡片不可点**（决策 Q2）。
 */
@Composable
fun MetricsRow(
    state: HomeUiState,
    onMetricClick: (Metric) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimen = WeightDiaryTheme.dimens
    val placeholder = stringResource(R.string.value_placeholder)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = dimen.pageHorizontal),
        horizontalArrangement = Arrangement.spacedBy(dimen.metricCardGap),
    ) {
        item {
            MetricCard(
                label = stringResource(R.string.metric_weight),
                value = state.weightKg?.format1() ?: placeholder,
                selected = state.selectedMetric == Metric.WEIGHT,
                selectable = true,
                deltaKg = state.deltaKg,
                timeLabel = state.latestMeasuredAt?.toTimeLabel(),
                onClick = { onMetricClick(Metric.WEIGHT) },
            )
        }
        item {
            MetricCard(
                label = stringResource(R.string.metric_bmi),
                value = state.bmi?.format1() ?: placeholder,
                selected = state.selectedMetric == Metric.BMI,
                selectable = true,
                onClick = { onMetricClick(Metric.BMI) },
            )
        }
        item {
            MetricCard(
                label = stringResource(R.string.metric_body_fat),
                value = state.bodyFatPercent?.format1() ?: placeholder,
                selected = state.selectedMetric == Metric.BODY_FAT,
                selectable = true,
                onClick = { onMetricClick(Metric.BODY_FAT) },
            )
        }
        item {
            MetricCard(
                label = stringResource(R.string.metric_height),
                value = state.heightCm?.formatTrimmed() ?: placeholder,
                selected = false,
                selectable = false,
                onClick = {},
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    selected: Boolean,
    selectable: Boolean,
    onClick: () -> Unit,
    deltaKg: Double? = null,
    timeLabel: TimeLabel? = null,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val shape = RoundedCornerShape(dimen.radiusCard)

    Column(
        modifier = Modifier
            // widthIn/heightIn 而不是 width/height：系统字体放大到 1.5× 时，
            // 112dp 宽塞不下「体重（公斤）」，固定高度还会把变化量裁掉（设计规范 §8）
            .widthIn(min = dimen.metricCardWidth)
            .heightIn(min = dimen.metricCardHeight)
            .clip(shape)
            .background(if (selected) colors.cardSelectedFill else colors.cardFill)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = if (selected) colors.textPrimary else colors.cardBorder,
                shape = shape,
            )
            .then(
                if (selectable) Modifier.clickable(onClick = onClick, role = Role.Button)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = typo.cardLabel,
            color = colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = value,
            style = typo.valueLarge.tabular,
            color = colors.textPrimary,
            maxLines = 1,
        )

        Spacer(Modifier.weight(1f))

        if (deltaKg != null || timeLabel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deltaKg != null) {
                    DeltaText(deltaKg = deltaKg, modifier = Modifier)
                }
                if (deltaKg != null && timeLabel != null) {
                    Spacer(Modifier.width(4.dp))
                }
                if (timeLabel != null) {
                    Text(
                        text = timeLabel.asText(),
                        style = typo.axisLabel,
                        color = colors.textDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 与上一条记录的差值。
 *
 * 箭头之外还有颜色，但**颜色不是唯一信息载体** —— 箭头本身也是语义（无障碍要求）。
 */
@Composable
internal fun DeltaText(deltaKg: Double, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    val flat = abs(deltaKg) < 0.05
    val down = deltaKg < 0
    val magnitude = abs(deltaKg).format1()

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

@Composable
internal fun TimeLabel.asText(): String = when (this) {
    is TimeLabel.Today -> stringResource(R.string.time_today, time)
    is TimeLabel.Yesterday -> stringResource(R.string.time_yesterday, time)
    is TimeLabel.Absolute -> text
}
