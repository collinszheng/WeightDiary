package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.clickable
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
 * 删除不在这一层：左滑露出按钮由 [SwipeToDeleteRow] 包在外面负责。
 * 长按入口已取消 —— 它没有任何视觉提示，且取消撤销后误触不可恢复。
 */
@Composable
fun RecordRowItem(
    row: RecordRow,
    onClick: () -> Unit,
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
            // heightIn 而不是 height：字体放大后行内容要能撑开（设计规范 §8）
            .heightIn(min = ROW_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
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
