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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 悬浮的「添加数据」按钮。
 *
 * 早先它是顶栏右侧一个描边圆圈，但点下去弹窗从**下方**升起 ——
 * 按钮在上、结果在下，操作与反馈在空间上不呼应。改成悬浮按钮后就顺了。
 *
 * 这里带一点阴影：不加的话它读起来不像「浮」在内容之上，而像一个贴在角落的圆。
 * 全 App 只有它用阴影 —— 卡片仍然一律描边不用 elevation。
 */
@Composable
fun AddRecordFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = stringResource(R.string.action_add_record)

    Box(
        modifier = modifier
            .size(dimen.fabSize)
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(colors.accent)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 2.dp.toPx()
            drawLine(
                color = Color.White,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * 顶栏右侧的「设置」按钮。
 *
 * 功能尚未定，点它只弹一条 Snackbar。位置正好是原先「添加数据」按钮待过的地方 ——
 * 那个按钮已经改成右下角的悬浮按钮了。
 */
@Composable
fun SettingsIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = stringResource(R.string.action_settings)

    Box(
        modifier = modifier
            // 触摸区撑到 48dp，视觉上的齿轮只有 22dp
            .size(dimen.minTouchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // 三条带滑块的横线，也就是通常说的「tune」图标。
        // 原先画的是八齿齿轮 + 内外两圈，22dp 下细节糊在一起，看着很吵。
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.6.dp.toPx()
            val knob = 2.4.dp.toPx()
            // 每条线：纵向位置 + 滑块横向位置，刻意错开，不然像三条等长的横杠
            val rows = listOf(0.25f to 0.68f, 0.5f to 0.34f, 0.75f to 0.58f)

            rows.forEach { (yRatio, xRatio) ->
                val y = size.height * yRatio
                drawLine(
                    color = colors.textPrimary,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                val cx = size.width * xRatio
                // 滑块用底色填实再描边，线条才像是从它背后穿过
                drawCircle(color = colors.background, radius = knob, center = Offset(cx, y))
                drawCircle(
                    color = colors.textPrimary,
                    radius = knob,
                    center = Offset(cx, y),
                    style = Stroke(width = stroke),
                )
            }
        }    }
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
