package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 顶部导航栏右侧的「+」按钮。
 *
 * 视觉直径 28dp 的描边圆圈，但触摸区撑到 [WeightDiaryTheme.dimens.minTouchTarget]（48dp）——
 * 视觉可以小，手指够不着才是问题。
 */
@Composable
fun CirclePlusButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = stringResource(R.string.action_add_record)

    Box(
        modifier = modifier
            .size(dimen.minTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .border(1.5.dp, colors.textPrimary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(14.dp)) {
                val stroke = 1.5.dp.toPx()
                drawLine(
                    color = colors.textPrimary,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = colors.textPrimary,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/**
 * 目标卡片上的编辑铅笔。点开的是「编辑个人资料」弹窗（身高 + 目标体重），
 * 这是全 App 唯一的身高修改入口（决策 A5）。
 */
@Composable
fun PencilIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = stringResource(R.string.action_edit_profile)

    Box(
        modifier = modifier
            // 触摸区撑到 48dp（视觉仍是 16dp 的铅笔）—— 设计规范 §8 要求触摸目标 ≥ 48×48dp
            .size(dimen.minTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(16.dp)) {
            // 画一支斜 45° 的铅笔：笔杆（平行四边形）+ 笔尖（三角形）。
            // 坐标以 16 单位网格描述，再按实际尺寸缩放。
            val u = size.width / 16f
            fun px(v: Float) = v * u

            val body = Path().apply {
                moveTo(px(6.24f), px(14.08f))
                lineTo(px(12.60f), px(7.72f))
                lineTo(px(9.78f), px(4.90f))
                lineTo(px(3.42f), px(11.26f))
                close()
            }
            drawPath(body, colors.textSecondary)

            val tip = Path().apply {
                moveTo(px(1.6f), px(15.6f))
                lineTo(px(6.24f), px(14.08f))
                lineTo(px(3.42f), px(11.26f))
                close()
            }
            drawPath(tip, colors.textSecondary)

            // 笔尾的一道浅色分隔，让轮廓不至于糊成一块
            drawLine(
                color = colors.cardFill,
                start = Offset(px(10.4f), px(5.6f)),
                end = Offset(px(11.6f), px(6.8f)),
                strokeWidth = px(1.1f),
            )
        }
    }
}
