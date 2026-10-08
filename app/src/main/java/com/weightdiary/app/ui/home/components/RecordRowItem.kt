package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.model.RecordSource
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.TimeLabel
import com.weightdiary.app.ui.common.toTimeLabel
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import com.weightdiary.app.ui.theme.tabular
import kotlin.math.abs

/** 列表项高度（设计规范 §4.5） */
private val ROW_HEIGHT = 56.dp

/**
 * 历史记录的一行。首页历史列表与「全部记录」弹窗共用同一个组件 ——
 * 设计规范明确要求两侧「行内容一致」，共用一个组件是唯一能保证不跑偏的做法。
 *
 * 删除不在这一层：左滑露出按钮由 [SwipeToDeleteRow] 包在外面负责。
 * 长按入口已取消 —— 它没有任何视觉提示，且取消撤销后误触不可恢复。
 */
@Composable
fun RecordRowItem(
    row: RecordRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 主动消费横向拖动。
     *
     * 首页历史区没有横向滚动容器（左滑删除已移除），横滑不会被任何东西消费，
     * 于是 `clickable` 会把「横滑」误判成「点击」并打开编辑弹窗。
     * 消费掉横向拖动，点击判定就会失效。
     *
     * 「全部记录」弹窗里不能开这个 —— 那里的外层 `draggable` 要靠这个手势来露出删除按钮。
     */
    swallowHorizontalDrag: Boolean = false,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    val synced = row.source == RecordSource.HEALTH_CONNECT

    // 时间标签：**可见文本**与**朗读文本**要分开。
    // 可见的那份用 `· 体脂秤` 这种短符号（列表右侧空间有限），
    // 读屏的那份要念全（「来自体脂秤」），所以不能共用同一个字符串。
    val timeLabel = row.measuredAt.toTimeLabel().asText()
    val displayTime = if (synced) {
        timeLabel + " · " + stringResource(R.string.record_source_scale)
    } else {
        timeLabel
    }

    val weightText = row.weightKg.format1()
    // 体脂是可见文本，读屏描述里也要带上 —— 否则 Row 上的 contentDescription
    // 会把子元素的文字整个盖掉，读屏用户永远听不到体脂
    val baseDescription = row.bodyFatPercent?.let { fat ->
        stringResource(
            R.string.cd_record_row_with_fat,
            weightText,
            fat.format1(),
            timeLabel,
        )
    } ?: stringResource(R.string.cd_record_row, "$weightText kg", timeLabel)
    val description = if (synced) {
        baseDescription + stringResource(R.string.cd_record_source_scale)
    } else {
        baseDescription
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // heightIn 而不是 height：字体放大后行内容要能撑开（设计规范 §8）
            .heightIn(min = ROW_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            .then(
                if (swallowHorizontalDrag) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures { change, _ -> change.consume() }
                    }
                } else {
                    Modifier
                }
            )
            .semantics { contentDescription = description }
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = weightText,
                    style = typo.valueMedium.tabular,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = stringResource(R.string.unit_kg),
                    style = typo.unit,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 2.dp),
                )

                // 体脂加在体重**右侧的同一行**：体脂测量频率远低于体重，多数行没有这个值，
                // 若另起一行会让行高参差不齐。加在同一行就只增加长度，不增加高度。
                row.bodyFatPercent?.let { fat ->
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.body_fat_inline, fat.format1()),
                        style = typo.unit.tabular,
                        color = colors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
            row.deltaKg?.let {
                DeltaText(deltaKg = it, modifier = Modifier)
            }
        }

        Text(
            text = displayTime,
            style = typo.caption,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}

/** 时间标签的文案组装。放在这里是因为历史记录行是它唯一的用户。 */
@Composable
internal fun TimeLabel.asText(): String = when (this) {
    is TimeLabel.Today -> stringResource(R.string.time_today, time)
    is TimeLabel.Yesterday -> stringResource(R.string.time_yesterday, time)
    is TimeLabel.Absolute -> text
}

/**
 * 变化量文字：`↓ 0.3` / `↑ 0.8` / `— 0.0`。
 *
 * 箭头之外还有颜色，但**颜色不是唯一信息载体** —— 箭头本身也是语义（无障碍要求）。
 * 原先它与概览卡片共用，那张卡片删掉后就只剩历史记录行这一个用户了。
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