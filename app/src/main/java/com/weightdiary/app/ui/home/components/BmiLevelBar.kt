package com.weightdiary.app.ui.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * BMI 水平的四色条 + 三角滑块。
 *
 * 四段**等宽**（决策 B13），所以滑块位置不能按 BMI 线性映射，必须用
 * [com.weightdiary.app.domain.bmi.BmiClassifier.sliderPosition] 算出的「段索引 + 段内比例」。
 *
 * @param sliderPos 0f..1f；传 null 表示没有身高、算不出 BMI，此时四色条降透明度且不画滑块
 */
@Composable
fun BmiLevelBar(
    sliderPos: Float?,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors

    val barHeight = 10.dp
    val triWidth = 10.dp
    val triHeight = 7.dp
    val gap = 1.dp
    val segments = listOf(
        colors.bmiUnderweight,
        colors.bmiNormal,
        colors.bmiOverweight,
        colors.bmiObese,
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(triHeight + gap + barHeight),
    ) {
        val fullWidth = maxWidth
        val active = sliderPos != null

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.BottomStart)
                .alpha(if (active) 1f else 0.35f)
                .clip(RoundedCornerShape(barHeight / 2)),
        ) {
            segments.forEach { color ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(color),
                )
            }
        }

        if (sliderPos != null) {
            val clamped = sliderPos.coerceIn(0f, 1f)
            val travel = fullWidth - triWidth
            Canvas(
                modifier = Modifier
                    .offset(x = travel * clamped)
                    .size(triWidth, triHeight)
                    .align(Alignment.TopStart),
            ) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, colors.textPrimary)
            }
        }
    }
}
