package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 首页的历史记录区块：标题 + 最近几条 + 「查看更多记录」。
 *
 * 列表项与「全部记录」弹窗共用 [RecordRowItem]（设计规范 §4.5 / §5.2 要求行内容一致）。
 */
@Composable
fun HistorySection(
    rows: List<RecordRow>,
    totalCount: Int,
    hasMore: Boolean,
    onRowClick: (RecordRow) -> Unit,
    onRowDelete: (RecordRow) -> Unit,
    onViewMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = typo.body,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.history_count, totalCount),
                style = typo.cardLabel,
                color = colors.textSecondary,
            )
        }

        rows.forEachIndexed { index, row ->
            // 分隔线只画在行与行之间，最后一行下面不画
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(colors.divider),
                )
            }
            // 必须按 id 分组：否则行的滑动状态会跟着**位置**走 ——
            // 删掉第一行后，下一行会顶到同一个槽位、继承「已展开」的状态，看着像自己划开了
            key(row.id) {
                SwipeToDeleteRow(
                    row = row,
                    onClick = { onRowClick(row) },
                    onDelete = { onRowDelete(row) },
                )
            }
        }

        if (hasMore) {
            Spacer(Modifier.height(8.dp))
            ViewMoreButton(onClick = onViewMore)
        }
    }
}

/** 全宽 44dp、`#F2F2F7` 圆角 12dp、文字 15sp Bold 居中 */
@Composable
private fun ViewMoreButton(onClick: () -> Unit) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(colors.fieldFill)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.action_view_more),
            style = typo.body,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
