package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 可左滑删除的记录行。
 *
 * 设计规范 §5.2 写的是「长按**或**左滑」，此前只做了长按 —— 而长按没有任何视觉提示：
 * 用户想删记录时最自然的动作是点一下，点进编辑弹窗又找不到删除，就会以为删不掉。
 * 左滑虽然是列表里的常见习惯，但同样没有提示，所以编辑弹窗里也补了一个显式的删除按钮。
 */
@Composable
fun SwipeToDeleteRow(
    row: RecordRow,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    val state = rememberSwipeToDismissBoxState(
        // 默认阈值是行宽的 50%（411dp 的行要滑 205dp），实测很难触发。
        // 降到 30%：仍然是有意为之的动作，误删也有 Snackbar 撤销兜底。
        positionalThreshold = { totalDistance -> totalDistance * 0.3f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            // 恒返回 false：不让行**留在**已划走的状态。
            // 删除是异步的，而 rememberSwipeToDismissBoxState 内部用的是 rememberSaveable ——
            // 若让它真的划走，撤销之后行回来了、状态却还是「已划走」，那一行就看不见了。
            false
        },
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.bmiObese)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    style = typo.body,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) {
        // 行本身必须有**不透明背景**，否则滑动时会直接看到下面的红底
        RecordRowItem(
            row = row,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background),
        )
    }
}
