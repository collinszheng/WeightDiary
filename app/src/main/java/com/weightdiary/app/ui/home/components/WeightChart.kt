package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.weightdiary.app.domain.chart.MonotoneCubic
import com.weightdiary.app.ui.home.ChartUi
import com.weightdiary.app.ui.home.GoalOffscreen
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import java.time.Instant

private val Y_LABEL_WIDTH = 34.dp
private val Y_TITLE_HEIGHT = 16.dp
private val X_LABEL_HEIGHT = 18.dp
private val DOT_RADIUS = 4.dp
private val DOT_STROKE = 2.dp
private val LINE_WIDTH = 2.dp

/**
 * 折线图。自绘 Canvas（决策 Q3）。
 *
 * 绘制层级自下而上：次刻度 → 主刻度 + 数字 → 渐变填充 → 目标虚线 → 折线 → 空心实测点 → X 轴标签。
 *
 * 曲线用 [MonotoneCubic]（保单调、不过冲），跨空缺段同样是实线 —— 见决策记录「冲突 3」。
 */
@Composable
fun WeightChart(
    chart: ChartUi,
    yAxisTitle: String,
    formatY: (Double) -> String,
    formatX: (Instant) -> String,
    goalLabel: String?,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val axisStyle = typo.axis.copy(color = colors.textSecondary)
    val titleStyle = typo.axis.copy(color = colors.textSecondary)
    val goalStyle = typo.axisLabel.copy(color = colors.accent)
    val textMeasurer = rememberTextMeasurer()

    val axis = chart.yAxis
    val points = chart.points
    val start = chart.start
    val end = chart.end

    Canvas(modifier = modifier) {
        if (axis == null || points.isEmpty() || start == null || end == null) return@Canvas

        val yLabelWidthPx = Y_LABEL_WIDTH.toPx()
        val xLabelHeightPx = X_LABEL_HEIGHT.toPx()
        val titleHeightPx = Y_TITLE_HEIGHT.toPx()
        val plotLeft = yLabelWidthPx
        val plotRight = size.width - DOT_RADIUS.toPx()
        // 顶部给 Y 轴标题留一行，否则标题会和最高那条主刻度的数字叠在一起
        val plotTop = titleHeightPx
        val plotBottom = size.height - xLabelHeightPx

        val spanMs = (end.toEpochMilli() - start.toEpochMilli()).toDouble().coerceAtLeast(1.0)
        val dataWidth = plotRight - plotLeft

        fun yPx(value: Double): Float =
            (plotBottom - axis.normalize(value) * (plotBottom - plotTop)).toFloat()

        fun xPx(time: Instant): Float =
            (plotLeft + ((time.toEpochMilli() - start.toEpochMilli()) / spanMs) * dataWidth).toFloat()

        // ── 1. 次刻度线 ──
        axis.minorTicks.forEach { value ->
            val y = yPx(value)
            drawLine(
                color = colors.chartGridMinor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx() * 0.7f,
            )
        }

        // ── 2. 主刻度线 + 数字 ──
        axis.majorTicks.forEach { value ->
            val y = yPx(value)
            drawLine(
                color = colors.chartGridMajor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx(),
            )
            drawRightAligned(
                textMeasurer, formatY(value), axisStyle,
                rightEdge = plotLeft - 6.dp.toPx(),
                centerY = y,
            )
        }

        // ── 3. 渐变填充 ──
        val xs = points.map { xPx(it.time).toDouble() }
        val ys = points.map { yPx(it.value).toDouble() }
        val linePath = buildSmoothPath(xs, ys)

        if (linePath != null) {
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(xs.last().toFloat(), plotBottom)
                lineTo(xs.first().toFloat(), plotBottom)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(colors.accent.copy(alpha = 0.20f), colors.accent.copy(alpha = 0f)),
                    startY = plotTop,
                    endY = plotBottom,
                ),
            )
        }

        // ── 4. 目标虚线（在折线之下）──
        chart.goalLine?.let { target ->
            val y = yPx(target)
            drawLine(
                color = colors.accent,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 9f), 0f),
            )
            goalLabel?.let {
                drawText(
                    textMeasurer = textMeasurer,
                    text = it,
                    style = goalStyle,
                    topLeft = Offset(plotLeft + 4.dp.toPx(), y - 15.dp.toPx()),
                )
            }
        }

        // 目标离数据太远、不撑开 Y 轴时，用边缘的方向箭头代替虚线（决策 B8）
        if (chart.goalOffscreen != GoalOffscreen.NONE) {
            val pointsUp = chart.goalOffscreen == GoalOffscreen.ABOVE
            val centerX = plotLeft + 10.dp.toPx()
            val centerY = if (pointsUp) plotTop + 8.dp.toPx() else plotBottom - 8.dp.toPx()
            val half = 5.dp.toPx()
            val tipY = if (pointsUp) centerY - half else centerY + half
            val baseY = if (pointsUp) centerY + half else centerY - half
            drawLine(
                color = colors.accent,
                start = Offset(centerX, baseY),
                end = Offset(centerX, tipY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.accent,
                start = Offset(centerX - half * 0.7f, tipY + if (pointsUp) half * 0.7f else -half * 0.7f),
                end = Offset(centerX, tipY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.accent,
                start = Offset(centerX + half * 0.7f, tipY + if (pointsUp) half * 0.7f else -half * 0.7f),
                end = Offset(centerX, tipY),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // ── 5. 折线 ──
        if (linePath != null) {
            drawPath(
                path = linePath,
                color = colors.accent,
                style = Stroke(
                    width = LINE_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        // ── 6. 空心实测点（唯一的「真实测量」标记）──
        points.forEachIndexed { index, _ ->
            val center = Offset(xs[index].toFloat(), ys[index].toFloat())
            drawCircle(Color.White, radius = DOT_RADIUS.toPx(), center = center)
            drawCircle(
                color = colors.accent,
                radius = DOT_RADIUS.toPx(),
                center = center,
                style = Stroke(width = DOT_STROKE.toPx()),
            )
        }

        // ── 7. X 轴标签（不画刻度线）──
        chart.xLabels.forEachIndexed { index, time ->
            val layout = textMeasurer.measure(formatX(time), axisStyle)
            val x = when {
                index == 0 -> plotLeft
                index == chart.xLabels.lastIndex -> plotRight - layout.size.width
                else -> xPx(time) - layout.size.width / 2f
            }
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x, plotBottom + 5.dp.toPx()),
            )
        }

        // ── Y 轴标题 ──
        drawText(
            textMeasurer = textMeasurer,
            text = yAxisTitle,
            style = titleStyle,
            topLeft = Offset(plotLeft, 0f),
        )
    }
}

/** 只有 1 个点时画不出路径，返回 null，由调用方退化为单点 */
private fun buildSmoothPath(xs: List<Double>, ys: List<Double>): Path? {
    if (xs.size < 2) return null
    val path = Path()
    path.moveTo(xs[0].toFloat(), ys[0].toFloat())
    MonotoneCubic.bezierSegments(xs, ys).forEach { s ->
        path.cubicTo(
            s.c1x.toFloat(), s.c1y.toFloat(),
            s.c2x.toFloat(), s.c2y.toFloat(),
            s.x1.toFloat(), s.y1.toFloat(),
        )
    }
    return path
}

/** 右对齐绘制，纵向以 [centerY] 居中 */
private fun DrawScope.drawRightAligned(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    rightEdge: Float,
    centerY: Float,
) {
    val layout = textMeasurer.measure(text, style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(rightEdge - layout.size.width, centerY - layout.size.height / 2f),
    )
}

/** 空态：与图表同高，居中一行提示 */
@Composable
fun ChartEmptyState(text: String, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    androidx.compose.foundation.layout.Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = typo.caption, color = colors.textDisabled)
    }
}
