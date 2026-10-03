package com.weightdiary.app.ui.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.weightdiary.app.domain.chart.Granularity
import com.weightdiary.app.domain.chart.MonotoneCubic
import com.weightdiary.app.domain.chart.YAxis
import com.weightdiary.app.ui.home.ChartUi
import com.weightdiary.app.ui.home.GoalOffscreen
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private val Y_LABEL_WIDTH = 34.dp
private val Y_TITLE_HEIGHT = 16.dp
private val X_LABEL_HEIGHT = 18.dp
private val DOT_RADIUS = 4.dp
private val DOT_STROKE = 2.dp
private val LINE_WIDTH = 2.dp

/**
 * 横向视口的单位密度。只有「日历单位数 × 这个密度」超过绘图区宽度时才真正可滚动。
 *
 * 后果是**「日」「周」「年」视图滑动不会有任何效果**（单位太少，内容没占满），
 * 只有「月」（30 天 × 12dp = 360dp，勉强超出）和跨度很大的「总」才滚得动。
 * 这是 [docs/05-交付计划.md] 里 R10 记下的已知取舍。
 */
private val UNIT_DENSITY = 12.dp

/** 点击命中半径。只看横向距离 —— 点在某一点的「列」里就算命中，不必精确戳中圆点 */
private val HIT_RADIUS = 24.dp

/**
 * 折线图。自绘 Canvas（决策 Q3）。
 *
 * 绘制层级自下而上：次刻度 → 主刻度 + 数字 → 渐变填充 → 目标虚线 → 折线 → 空心实测点 → X 轴标签 → 气泡。
 *
 * **手势职责划分**（决策记录「冲突 1」）：
 * - 图表内左右滑动 = 滚动视口，**不切换日期范围**
 * - 切换范围只能点左右箭头
 * - 纵向滑动交给外层的页面滚动：`draggable` 只判横向，纵向达不到横向 slop，事件自然留给父级
 */
@Composable
fun WeightChart(
    chart: ChartUi,
    yAxisTitle: String,
    formatY: (Double) -> String,
    formatX: (Instant) -> String,
    formatTooltip: (ChartPoint) -> String,
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

    // 换 Tab / 换范围时重置滚动位置与选中点
    val rangeKey = listOf(chart.tab, start, end)
    val scroll = remember(rangeKey) { Animatable(0f) }
    var selected by remember(rangeKey) { mutableStateOf<ChartPoint?>(null) }
    val scope = rememberCoroutineScope()

    if (axis == null || points.isEmpty() || start == null || end == null) {
        ChartEmptyState(text = "", modifier = modifier)
        return
    }

    BoxWithConstraints(modifier = modifier) {
        // ── 几何全部在这里算好 ──
        // 命中测试要在点击回调里跑，而回调不在绘制作用域内，所以不能等到绘制阶段才算坐标
        val plotLeft = with(density) { Y_LABEL_WIDTH.toPx() }
        val plotRight = with(density) { maxWidth.toPx() - DOT_RADIUS.toPx() }
        val plotTop = with(density) { Y_TITLE_HEIGHT.toPx() }
        val plotBottom = with(density) { maxHeight.toPx() - X_LABEL_HEIGHT.toPx() }
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val hitRadiusPx = with(density) { HIT_RADIUS.toPx() }

        val contentWidthPx = countUnits(chart) * with(density) { UNIT_DENSITY.toPx() }
        val maxScrollPx = (contentWidthPx - plotWidth).coerceAtLeast(0f)
        val scrollPx = scroll.value.coerceIn(0f, maxScrollPx)

        // 内容比视口窄时（周视图只有 7×12dp）必须**拉伸铺满**，否则下面按比例算出的
        // 可视窗口会比整段范围还宽，X 轴标签跑到范围之外、曲线挤在左边一小块
        val effectiveContentWidthPx = contentWidthPx.coerceAtLeast(plotWidth)

        val rangeMs = (end.toEpochMilli() - start.toEpochMilli()).toDouble().coerceAtLeast(1.0)
        val visibleStartMs = start.toEpochMilli() + (scrollPx / effectiveContentWidthPx) * rangeMs
        val visibleSpanMs = (plotWidth / effectiveContentWidthPx) * rangeMs

        fun xOf(time: Instant): Float =
            (plotLeft + ((time.toEpochMilli() - visibleStartMs) / visibleSpanMs) * plotWidth).toFloat()

        fun yOf(value: Double): Float =
            (plotBottom - axis.normalize(value) * (plotBottom - plotTop)).toFloat()

        val visible = pointsWithNeighbours(points, visibleStartMs, visibleStartMs + visibleSpanMs)

        // 每次重组都会生成新的闭包，用 rememberUpdatedState 保证点击回调用的是最新那份，
        // 同时 pointerInput 的 key 保持稳定、不打断手势
        val hitTest by rememberUpdatedState<(Offset) -> ChartPoint?> { tap ->
            if (tap.y !in plotTop..plotBottom) {
                null
            } else {
                visible
                    .minByOrNull { abs(xOf(it.time) - tap.x) }
                    ?.takeIf { abs(xOf(it.time) - tap.x) <= hitRadiusPx }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = maxScrollPx > 0f,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            scroll.snapTo((scroll.value - delta).coerceIn(0f, maxScrollPx))
                        }
                    },
                    onDragStarted = { selected = null },
                    onDragStopped = { velocity ->
                        scope.launch {
                            scroll.updateBounds(0f, maxScrollPx)
                            // 惯性滑动，到边界停住（不自动翻页）
                            scroll.animateDecay(-velocity, splineBasedDecay(density))
                        }
                    },
                )
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
            val xs = visible.map { xOf(it.time).toDouble() }
            val ys = visible.map { yOf(it.value).toDouble() }
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
                visible.forEachIndexed { index, point ->
                    val center = Offset(xs[index].toFloat(), ys[index].toFloat())
                    if (point.sourceRecordId == selected?.sourceRecordId) {
                        drawCircle(colors.accent, radius = DOT_RADIUS.toPx(), center = center)
                    } else {
                        drawCircle(Color.White, radius = DOT_RADIUS.toPx(), center = center)
                        drawCircle(
                            color = colors.accent,
                            radius = DOT_RADIUS.toPx(),
                            center = center,
                            style = Stroke(width = DOT_STROKE.toPx()),
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

            // 目标离数据太远、不撑开 Y 轴时，用边缘的方向箭头代替虚线（决策 B8）
            if (chart.goalOffscreen != GoalOffscreen.NONE) {
                drawGoalArrow(colors.accent, chart.goalOffscreen, plotLeft, plotTop, plotBottom)
            }

            // ── 7. X 轴标签：按**可视窗口**重新四等分（滚动时实时重算）──
            val labelTimes = (0..4).map { i ->
                Instant.ofEpochMilli((visibleStartMs + visibleSpanMs * i / 4).toLong())
            }
            labelTimes.forEachIndexed { index, time ->
                val layout = textMeasurer.measure(formatX(time), axisStyle)
                val x = when (index) {
                    0 -> plotLeft
                    labelTimes.lastIndex -> plotRight - layout.size.width
                    else -> xOf(time) - layout.size.width / 2f
                }
                drawText(layout, topLeft = Offset(x, plotBottom + 5.dp.toPx()))
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
                        dotRadius = DOT_RADIUS.toPx(),
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

/**
 * 内容宽度按「日历单位数 × 密度」算。
 *
 * 单位随粒度变：按小时 / 按天 / 按月。这就决定了哪些视图真的需要滚动 ——
 * 日 24 个单位、周 7 个、月 30 个、年 12 个。
 */
private fun countUnits(chart: ChartUi): Int {
    val start = chart.start ?: return 0
    val end = chart.end ?: return 0
    return when (chart.granularity) {
        Granularity.RAW -> 24

        Granularity.DAILY ->
            (((end.toEpochMilli() - start.toEpochMilli()) / 86_400_000L) + 1).toInt().coerceAtLeast(1)

        Granularity.MONTHLY -> {
            val zone = ZoneId.systemDefault()
            val from = start.atZone(zone).toLocalDate().withDayOfMonth(1)
            val to = end.atZone(zone).toLocalDate().withDayOfMonth(1)
            (ChronoUnit.MONTHS.between(from, to) + 1).toInt().coerceAtLeast(1)
        }
    }
}

/** 取可视窗口内的点，并把两侧各一个相邻点带上，保证线段在窗口边缘不断 */
private fun pointsWithNeighbours(
    points: List<ChartPoint>,
    visibleStartMs: Double,
    visibleEndMs: Double,
): List<ChartPoint> {
    if (points.isEmpty()) return emptyList()
    val first = points.indexOfFirst { it.time.toEpochMilli() >= visibleStartMs }
    val last = points.indexOfLast { it.time.toEpochMilli() <= visibleEndMs }
    if (first < 0 || last < 0 || first > last) return emptyList()
    val from = (first - 1).coerceAtLeast(0)
    val to = (last + 1).coerceAtMost(points.lastIndex)
    return points.subList(from, to + 1)
}

private fun DrawScope.drawGoalArrow(
    color: Color,
    direction: GoalOffscreen,
    plotLeft: Float,
    plotTop: Float,
    plotBottom: Float,
) {
    val pointsUp = direction == GoalOffscreen.ABOVE
    val centerX = plotLeft + 10.dp.toPx()
    val centerY = if (pointsUp) plotTop + 8.dp.toPx() else plotBottom - 8.dp.toPx()
    val half = 5.dp.toPx()
    val tipY = if (pointsUp) centerY - half else centerY + half
    val baseY = if (pointsUp) centerY + half else centerY - half
    val back = if (pointsUp) half * 0.7f else -half * 0.7f

    drawLine(color, Offset(centerX, baseY), Offset(centerX, tipY), 1.5.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(centerX - half * 0.7f, tipY + back), Offset(centerX, tipY), 1.5.dp.toPx(), StrokeCap.Round)
    drawLine(color, Offset(centerX + half * 0.7f, tipY + back), Offset(centerX, tipY), 1.5.dp.toPx(), StrokeCap.Round)
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
