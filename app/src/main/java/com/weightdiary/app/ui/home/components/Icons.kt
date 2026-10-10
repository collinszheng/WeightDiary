package com.weightdiary.app.ui.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
        // 六角螺母：六边形外框 + 中心圆。
        // 选它是因为在 22dp 的真实尺寸下依然结实 —— 齿轮那类高细节图形缩到这么小就糊了。
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.6.dp.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f
            val circum = size.minDimension * 0.40f

            // 尖角朝上的正六边形（外接圆半径 R 的六边形，宽 √3·R、高 2R）
            val hex = Path().apply {
                for (i in 0 until 6) {
                    val angle = (-PI / 2 + i * PI / 3).toFloat()
                    val x = cx + cos(angle) * circum
                    val y = cy + sin(angle) * circum
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path = hex,
                color = colors.textPrimary,
                style = Stroke(width = stroke, join = StrokeJoin.Round),
            )
            drawCircle(
                color = colors.textPrimary,
                radius = size.minDimension * 0.16f,
                center = Offset(cx, cy),
                style = Stroke(width = stroke),
            )
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

/**
 * 顶栏的「同步」按钮。只在设置里打开「手动同步」时出现。
 *
 * 画的是循环箭头（缺口朝上的圆弧 + 一个切向箭头），和设置按钮同一套规格：
 * 触摸区 48dp、视觉 20dp、描边 1.6dp —— 顶栏上两个图标必须看起来是一对，
 * 大小差一点点都很显眼。
 *
 * @param enabled 同步进行中传 false：禁止连点。
 * @param syncing 同步进行中把整个图标转起来并染成强调色。**只压成灰色是不够的** ——
 *   灰图标读起来像「坏了」，而同步要走设备内 IPC，慢的时候必须能看出「在动」。
 */
@Composable
fun SyncIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    syncing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = if (syncing) {
        stringResource(R.string.settings_hc_syncing)
    } else {
        stringResource(R.string.action_sync)
    }

    // 只在同步时挂动画：常驻的无限动画会一直占着帧时钟
    val rotation = if (syncing) {
        val transition = rememberInfiniteTransition(label = "sync")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
            ),
            label = "syncRotation",
        ).value
    } else {
        0f
    }

    Box(
        modifier = modifier
            .size(dimen.minTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val tint = when {
            syncing -> colors.accent
            !enabled -> colors.textDisabled
            else -> colors.textPrimary
        }

        Canvas(Modifier.size(20.dp).rotate(rotation)) {
            val stroke = 1.6.dp.toPx()
            val inset = stroke * 1.2f
            val diameter = size.minDimension - inset * 2f
            val radius = diameter / 2f
            val cx = size.width / 2f
            val cy = size.height / 2f

            // 缺口留在正上方（−55° 起、扫 265°，终点 210° 落在左上方）
            val startAngle = -55f
            val sweep = 265f
            drawArc(
                color = tint,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // 箭头长在弧的**终点**，沿顺时针切向指出去 —— 读起来就是「朝这个方向转」
            val endRad = (startAngle + sweep) * PI / 180.0
            val ex = cx + radius * cos(endRad).toFloat()
            val ey = cy + radius * sin(endRad).toFloat()
            // Compose 的 y 轴朝下，所以顺时针切向是 (−sin, cos)
            val tx = -sin(endRad).toFloat()
            val ty = cos(endRad).toFloat()
            // 半径方向（由圆心指向外），用来定箭头两翼
            val nx = cos(endRad).toFloat()
            val ny = sin(endRad).toFloat()

            val tipLength = stroke * 2.4f
            val halfWidth = stroke * 1.4f
            drawPath(
                path = Path().apply {
                    moveTo(ex + tx * tipLength, ey + ty * tipLength)
                    lineTo(ex + nx * halfWidth, ey + ny * halfWidth)
                    lineTo(ex - nx * halfWidth, ey - ny * halfWidth)
                    close()
                },
                color = tint,
            )
        }
    }
}