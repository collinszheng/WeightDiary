package com.weightdiary.app.ui.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 划开后停住的位置：行宽的三分之一（产品要求「划到三分之一处」） */
private const val REVEAL_FRACTION = 1f / 3f

/** 甩动速度超过这个值就直接判定方向，不看位置 */
private const val FLING_VELOCITY = 900f

/**
 * 左滑**露出**删除按钮的记录行。
 *
 * 交互与 `SwipeToDismissBox` 有本质区别：**滑动本身不删除**。
 * 行只会滑到三分之一处停住、露出按钮，必须再点一下那个按钮才真的删。
 *
 * 这样做的代价是少了一次「划一下就删掉」的爽快，换来的是：
 * - 误划不会丢数据（滑到底也只是露出按钮）
 * - 删除动作有明确的落点，不依赖阈值判断
 *
 * 因此全 App 不再需要「已删除」的撤销 Snackbar —— 二次确认本身就承担了防误删。
 * 长按入口已取消：它没有任何视觉提示，与左滑功能重叠，且取消撤销后误触不可恢复。
 */
@Composable
fun SwipeToDeleteRow(
    row: RecordRow,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // 先取出来：下面嵌套的 Box 有 BoxScope 作为隐式接收者，
        // 直接在里面写 maxWidth 会解析不到 BoxWithConstraintsScope.maxWidth
        val rowWidth = maxWidth
        val revealWidth = rowWidth * REVEAL_FRACTION
        val revealPx = with(density) { rowWidth.toPx() } * REVEAL_FRACTION
        // 0 = 收起；-revealPx = 完全展开
        val offset = remember { Animatable(0f) }
        // 单独用一个布尔量而不是每帧读 offset.value：拖动时逐帧读会让整行重组
        var revealed by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        fun settle(reveal: Boolean) {
            revealed = reveal
            scope.launch { offset.animateTo(if (reveal) -revealPx else 0f, tween(180)) }
        }

        Box {
            // 背景：右侧三分之一的删除按钮。只有划开之后才可点
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(revealWidth)
                        .background(colors.bmiObese)
                        .clickable(enabled = revealed, role = Role.Button, onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        style = typo.body,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            RecordRowItem(
                row = row,
                // 划开状态下点行本身 = 收起，而不是进编辑
                onClick = { if (revealed) settle(false) else onClick() },
                modifier = Modifier
                    // 顺序要紧：offset 必须在 background **之前**。
                    // 反过来的话白色底会画在 offset 外面、留在原位，把下面的红底整个盖住，
                    // 表现就是「滑动了但按钮不出现」
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    // 行必须有不透明背景，否则收起时也会透出下面的红底
                    .background(colors.background)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            scope.launch {
                                offset.snapTo((offset.value + delta).coerceIn(-revealPx, 0f))
                            }
                        },
                        onDragStopped = { velocity ->
                            settle(
                                when {
                                    velocity < -FLING_VELOCITY -> true
                                    velocity > FLING_VELOCITY -> false
                                    else -> offset.value < -revealPx / 2f
                                },
                            )
                        },
                    ),
            )
        }
    }
}
