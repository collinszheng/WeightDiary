package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.toTimeLabel
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import com.weightdiary.app.ui.theme.tabular

/** 列表项高度（设计规范 §4.5） */
private val ROW_HEIGHT = 56.dp

/**
 * 历史记录的一行。首页历史列表与「全部记录」弹窗共用同一个组件 ——
 * 设计规范明确要求两侧「行内容一致」，共用一个组件是唯一能保证不跑偏的做法。
 *
 * @param onLongClick 长按删除。设计规范写的是「长按**或**左滑」，这里实现长按
 */
@Composable
fun RecordRowItem(
    row: RecordRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    val timeText = row.measuredAt.toTimeLabel().asText()
    val weightText = row.weightKg.format1()
    val description = stringResource(R.string.cd_record_row, "$weightText kg", timeText)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
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
            }
            row.deltaKg?.let {
                DeltaText(deltaKg = it, modifier = Modifier)
            }
        }

        Text(
            text = timeText,
            style = typo.caption,
            color = colors.textSecondary,
            maxLines = 1,
        )
    }
}
