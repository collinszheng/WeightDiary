package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.weightdiary.app.R
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 首次使用的图表区空状态：引导文案 + 淡色插画（设计规范 §6）。
 *
 * 与「这段时间还没有记录」区分开 —— 后者是**有数据但当前区间没有**，
 * 用户该做的是换时间段；前者是**一条都还没有**，用户该做的是去添加。
 * 两种情况的下一步动作完全不同，文案不能混用。
 */
@Composable
fun FirstUseEmptyState(modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ChartGlyphIllustration()
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_no_records_title),
            style = typo.caption,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.empty_no_records_hint),
            style = typo.cardLabel,
            color = colors.textDisabled,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 极淡的折线插画。用画图而不是引入位图资源 —— 只有几笔，
 * 走矢量既省 apk 体积，也能自动跟着主题色走。
 */
@Composable
private fun ChartGlyphIllustration() {
    val colors = WeightDiaryTheme.colors
    val line = colors.accent.copy(alpha = 0.30f)
    val dot = colors.accent.copy(alpha = 0.45f)

    Canvas(modifier = Modifier.size(width = 108.dp, height = 44.dp)) {
        // 五个点构成一条先降后升的柔和曲线，刻意不对称，避免看着像个图标
        val ys = listOf(0.62f, 0.78f, 0.55f, 0.30f, 0.42f)
        val step = size.width / (ys.size - 1)
        val points = ys.mapIndexed { i, f -> Offset(i * step, size.height * f) }

        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val mid = (a.x + b.x) / 2f
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        drawPath(
            path = path,
            color = line,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        points.forEach { drawCircle(color = dot, radius = 3.dp.toPx(), center = it) }
    }
}

/**
 * 首屏骨架屏。用灰块而不是转圈（设计规范 §6）——
 * 骨架屏能让用户提前看到布局形状，转圈只传达「在等」，而且会让后面的内容跳一下。
 *
 * 形状与真实布局一一对应，切换时才不会「跳版」。
 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = stringResource(R.string.loading)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .semantics { contentDescription = description },
    ) {
        Spacer(Modifier.height(dimen.topBarHeight))

        // 与 HomeScreen 一致：顶栏与目标卡之间隔 8dp
        Spacer(Modifier.height(8.dp))

        // 目标与水平卡片。填过体脂时实际会更高，但骨架屏只存在一两帧，
        // 按无体脂的最小高度画即可，不值得为它去查一次数据。
        Bone(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimen.pageHorizontal)
                .height(dimen.goalCardHeight),
        )

        Spacer(Modifier.height(dimen.cardGap))

        Bone(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimen.pageHorizontal)
                .height(dimen.chartCardHeight),
        )
    }
}

@Composable
private fun Bone(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(WeightDiaryTheme.dimens.radiusCard)
    Box(
        modifier = modifier
            .clip(shape)
            .background(WeightDiaryTheme.colors.divider),
    )
}
