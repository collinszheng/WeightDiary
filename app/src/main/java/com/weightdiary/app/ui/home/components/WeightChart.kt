package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.weightdiary.app.domain.chart.ChartPoint
import com.weightdiary.app.domain.chart.MonotoneCubic
import com.weightdiary.app.domain.chart.XLabelKind
import com.weightdiary.app.ui.home.ChartUi
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import java.time.Instant
import kotlin.math.abs

private val Y_LABEL_WIDTH = 34.dp
private val Y_TITLE_HEIGHT = 16.dp
private val X_LABEL_HEIGHT = 18.dp
private val LINE_WIDTH = 2.dp

/** 空心实测点的半径上限。点数多时会按密度自动缩小 */
private val DOT_RADIUS_MAX = 4.dp
private val DOT_RADIUS_MIN = 1.dp

/** 相邻两点的像素间距乘这个系数就是点半径 —— 保证点之间始终留得出空隙 */
private const val DOT_RADIUS_SPACING_RATIO = 0.22f

/** 点击命中半径。只看横向距离 —— 点在某一点的「列」里就算命中，不必精确戳中圆点 */
private val HIT_RADIUS = 24.dp

/**
 * 折线图。自绘 Canvas（决策 Q3）。
 *
 * **整段范围一次画完，图表区不响应横向手势。** 早先做过「内容超出视口就横向滚动」，
 * 但月/年/总三个视图的数据都会被塞进一屏，滚动既难发现、又和页面纵向滚动打架，
 * 收益不抵复杂度，已移除。切换时间段只走左右的箭头。
 *
 * 绘制层级自下而上：次刻度 → 主刻度 + 数字 → 渐变填充 → 目标虚线 → 折线 → 空心实测点 → X 轴标签 → 气泡。
 */
@Composable
fun WeightChart(
    chart: ChartUi,
    yAxisTitle: String,
    formatY: (Double) -> String,
    formatX: (Instant, XLabelKind) -> String,
    formatTooltip: (ChartPoint) -> String,
    /** 「日」视图最后一个标签显示成 `24:00`，而不是 `23:59` */
    endOfDayLabel: String,
    goalLabel: String?,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val axisStyle = typo.axis.copy(color = colors.textSecondary)
    val titleStyle = typo.axis.copy(color = colors.textSecondary)
    val goalStyle = typo.axisLabel.copy(color = colors.accent)
    val tooltipStyle = typo.axis.copy(color = Color.White)
    val textMeasurer = rememberTextMeasurer()

    val axis = chart.yAxis
    val points = chart.points
    val start = chart.start
    val end = chart.end
    val density = LocalDensity.current

    // 换 Tab / 换范围时清掉选中点
    val rangeKey = listOf(chart.tab, start, end)
    var selected by remember(rangeKey) { mutableStateOf<ChartPoint?>(null) }

    if (axis == null || points.isEmpty() || start == null || end == null) {
        ChartEmptyState(text = "", modifier = modifier)
        return
    }

    BoxWithConstraints(modifier = modifier) {
        // 几何全部在这里算好 —— 命中测试要在点击回调里跑，而回调不在绘制作用域内
        val plotLeft = with(density) { Y_LABEL_WIDTH.toPx() }
        val plotRight = with(density) { maxWidth.toPx() }
        val plotTop = with(density) { Y_TITLE_HEIGHT.toPx() }
        val plotBottom = with(density) { maxHeight.toPx() - X_LABEL_HEIGHT.toPx() }
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val hitRadiusPx = with(density) { HIT_RADIUS.toPx() }

        val rangeMs = (end.toEpochMilli() - start.toEpochMilli()).toDouble().coerceAtLeast(1.0)

        // 点半径随密度自适应：周视图 7 个点保持原来大小，
        // 总视图 110 个点自动缩小，否则点会挤成一条链
        val spacingPx = if (points.size > 1) plotWidth / (points.size - 1) else plotWidth
        val dotRadius = (spacingPx * DOT_RADIUS_SPACING_RATIO).coerceIn(
            with(density) { DOT_RADIUS_MIN.toPx() },
            with(density) { DOT_RADIUS_MAX.toPx() },
        )
        val dotStroke = (dotRadius * 0.5f).coerceIn(
            with(density) { 0.6.dp.toPx() },
            with(density) { 2.dp.toPx() },
        )

        // 数据的横向映射两侧各内缩一个点半径。
        // 不缩的话首尾两点正好落在裁剪边界上，空心圆只画得出一半 ——
        // 月视图的 1 号、以及任何只有零星数据点的视图都会中招。
        val dataLeft = plotLeft + dotRadius
        val dataRight = plotRight - dotRadius
        val dataWidth = (dataRight - dataLeft).coerceAtLeast(1f)

        fun xOf(time: Instant): Float =
            (dataLeft + ((time.toEpochMilli() - start.toEpochMilli()) / rangeMs) * dataWidth).toFloat()

        fun yOf(value: Double): Float =
            (plotBottom - axis.normalize(value) * (plotBottom - plotTop)).toFloat()

        // 每次重组都会生成新的闭包，用 rememberUpdatedState 保证点击回调用的是最新那份
        val hitTest by rememberUpdatedState<(Offset) -> ChartPoint?> { tap ->
            if (tap.y !in plotTop..plotBottom) {
                null
            } else {
                points
                    .minByOrNull { abs(xOf(it.time) - tap.x) }
                    ?.takeIf { abs(xOf(it.time) - tap.x) <= hitRadiusPx }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { tap -> selected = hitTest(tap) }
                },
        ) {
            // ── 1. 次刻度线 ──
            axis.minorTicks.forEach { value ->
                val y = yOf(value)
                drawLine(colors.chartGridMinor, Offset(plotLeft, y), Offset(plotRight, y), 1.dp.toPx() * 0.7f)
            }

            // ── 2. 主刻度线 + 数字 ──
            axis.majorTicks.forEach { value ->
                val y = yOf(value)
                drawLine(colors.chartGridMajor, Offset(plotLeft, y), Offset(plotRight, y), 1.dp.toPx())
                drawRightAligned(textMeasurer, formatY(value), axisStyle, plotLeft - 6.dp.toPx(), y)
            }

            // ── 3~6. 折线相关全部裁剪在绘图区内 ──
            val xs = points.map { xOf(it.time).toDouble() }
            val ys = points.map { yOf(it.value).toDouble() }
            val linePath = buildSmoothPath(xs, ys)

            clipRect(plotLeft, plotTop, plotRight, plotBottom) {
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
                            colors = listOf(
                                colors.accent.copy(alpha = 0.20f),
                                colors.accent.copy(alpha = 0f),
                            ),
                            startY = plotTop,
                            endY = plotBottom,
                        ),
                    )
                }

                chart.goalLine?.let { target ->
                    val y = yOf(target)
                    drawLine(
                        color = colors.accent,
                        start = Offset(plotLeft, y),
                        end = Offset(plotRight, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(11f, 9f), 0f),
                    )
                }

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

                // 空心圆点 = 真实测量；选中的那个改成实心
                points.forEachIndexed { index, point ->
                    val center = Offset(xs[index].toFloat(), ys[index].toFloat())
                    if (point.sourceRecordId == selected?.sourceRecordId) {
                        drawCircle(colors.accent, radius = dotRadius, center = center)
                    } else {
                        drawCircle(Color.White, radius = dotRadius, center = center)
                        drawCircle(
                            color = colors.accent,
                            radius = dotRadius,
                            center = center,
                            style = Stroke(width = dotStroke),
                        )
                    }
                }
            }

            chart.goalLine?.let { target ->
                goalLabel?.let {
                    val y = yOf(target)
                    if (y > plotTop && y < plotBottom) {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = it,
                            style = goalStyle,
                            topLeft = Offset(plotLeft + 4.dp.toPx(), y - 15.dp.toPx()),
                        )
                    }
                }
            }


            // ── 7. X 轴标签 ──
            // 位置由按 Tab 分类的规则给出：周 = 七个星期几、年 = 十二个月、月 = 1/10/20/月末。
            // 字体放大时它们会互相压住，按需要抽稀（隔一个、隔两个…）直到放得下。
            val labelInstants = chart.xLabels.instants
            val labelKind = chart.xLabels.kind
            if (labelInstants.isNotEmpty()) {
                val widest = labelInstants.maxOf {
                    textMeasurer.measure(formatX(it, labelKind), axisStyle).size.width
                }
                val gapPx = 6.dp.toPx()
                var step = 1
                while (
                    step < labelInstants.size &&
                    (labelInstants.size + step - 1) / step * (widest + gapPx) > plotWidth
                ) {
                    step++
                }
                labelInstants.forEachIndexed { index, time ->
                    if (index % step != 0) return@forEachIndexed
                    val text = if (labelKind == XLabelKind.HOUR && index == labelInstants.lastIndex) {
                        endOfDayLabel
                    } else {
                        formatX(time, labelKind)
                    }
                    val layout = textMeasurer.measure(text, axisStyle)
                    // 居中，但整体钳在绘图区内 —— 首尾标签因此自然贴边而不是被切掉
                    val x = (xOf(time) - layout.size.width / 2f)
                        .coerceIn(plotLeft, (plotRight - layout.size.width).coerceAtLeast(plotLeft))
                    drawText(layout, topLeft = Offset(x, plotBottom + 5.dp.toPx()))
                }
            }

            // ── Y 轴标题 ──
            drawText(
                textMeasurer = textMeasurer,
                text = yAxisTitle,
                style = titleStyle,
                topLeft = Offset(plotLeft, 0f),
            )

            // ── 8. 气泡 ──
            selected?.let { point ->
                val cx = xOf(point.time)
                if (cx in plotLeft..plotRight) {
                    drawTooltip(
                        textMeasurer = textMeasurer,
                        text = formatTooltip(point),
                        style = tooltipStyle,
                        background = colors.textPrimary,
                        anchor = Offset(cx, yOf(point.value)),
                        bounds = Rect(plotLeft, plotTop, plotRight, plotBottom),
                        dotRadius = dotRadius,
                        corner = 8.dp.toPx(),
                        padH = 10.dp.toPx(),
                        padV = 6.dp.toPx(),
                        gap = 10.dp.toPx(),
                    )
                }
            }
        }
    }
}



/** 深色圆角气泡，默认浮在数据点上方；贴到上边界时翻到下方 */
private fun DrawScope.drawTooltip(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    background: Color,
    anchor: Offset,
    bounds: Rect,
    dotRadius: Float,
    corner: Float,
    padH: Float,
    padV: Float,
    gap: Float,
) {
    val layout = textMeasurer.measure(text, style)
    val width = layout.size.width + padH * 2
    val height = layout.size.height + padV * 2

    val left = (anchor.x - width / 2f)
        .coerceIn(bounds.left, (bounds.right - width).coerceAtLeast(bounds.left))
    val above = anchor.y - gap - dotRadius - height
    val top = if (above >= bounds.top) above else anchor.y + gap + dotRadius

    drawRoundRect(
        color = background,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(corner, corner),
    )
    drawText(textLayoutResult = layout, topLeft = Offset(left + padH, top + padV))
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (text.isNotEmpty()) {
            Text(text = text, style = typo.caption, color = colors.textDisabled)
        }
    }
}
