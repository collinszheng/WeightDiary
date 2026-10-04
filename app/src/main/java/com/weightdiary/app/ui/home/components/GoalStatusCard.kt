package com.weightdiary.app.ui.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weightdiary.app.R
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.formatTrimmed
import com.weightdiary.app.ui.common.label
import com.weightdiary.app.ui.home.HomeUiState
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import com.weightdiary.app.ui.theme.tabular

/**
 * 目标与水平卡片。
 *
 * - 左栏：当前 / 目标体重 + 目标进度条。铅笔图标打开「编辑个人资料」弹窗（身高 + 目标体重）
 * - 右栏：水平四色条 + 三角滑块 + 等级文字
 *
 * 达成目标时整卡高亮（决策 B9），由 [goalReached] 控制。
 */
@Composable
fun GoalStatusCard(
    state: HomeUiState,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    goalReached: Boolean = false,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val shape = RoundedCornerShape(dimen.radiusCard)
    val goal = state.goal

    // 达成目标时一次性脉冲。不弹窗打断（决策 B9）—— 用户正在看数据，
    // 一个弹窗要额外点一次才能消掉，反而打断了「刚达成」这个瞬间。
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(goalReached) {
        if (goalReached) {
            pulse.snapTo(1f)
            pulse.animateTo(1.025f, tween(durationMillis = 160, easing = LinearEasing))
            pulse.animateTo(1f, tween(durationMillis = 280, easing = FastOutSlowInEasing))
        } else {
            pulse.snapTo(1f)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // heightIn 而不是 height：系统字体放大到 1.5× 时内容要能撑开，不能被裁掉（设计规范 §8）
            .heightIn(min = dimen.goalCardHeight)
            .graphicsLayer {
                scaleX = pulse.value
                scaleY = pulse.value
            }
            .clip(shape)
            .background(if (goalReached) colors.accentSoft else colors.cardFill)
            .border(0.5.dp, colors.cardBorder, shape)
            .padding(horizontal = dimen.cardPadding, vertical = 14.dp),
    ) {
        // ─────────── 左栏 ───────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.label_current_target_weight),
                style = typo.cardLabel,
                color = colors.textSecondary,
                maxLines = 1,
            )

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = goal.currentWeightKg?.format1()
                        ?: stringResource(R.string.value_placeholder),
                    style = typo.valueLarge.tabular,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "/",
                    style = typo.valueLarge.copy(fontSize = 24.sp).tabular,
                    color = colors.textDisabled,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = goal.targetWeightKg?.format1()
                        ?: stringResource(R.string.goal_not_set),
                    style = typo.valueMedium,
                    color = colors.textSecondary,
                )
                PencilIconButton(onClick = onEditProfile)
            }

            Spacer(Modifier.weight(1f))

            // 目标进度条：起点 = 设置目标时的体重，终点 = 目标体重
            val progress = goal.progress
            if (progress != null) {
                val barShape = RoundedCornerShape(1.5.dp)
                Box(
                    modifier = Modifier
                        .width(180.dp)
                        .height(3.dp)
                        .clip(barShape)
                        .background(colors.divider),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(colors.accent),
                    )
                }
            } else {
                Spacer(Modifier.height(3.dp))
            }

            // 最近一次**填写过的**体脂，不是最近一条记录的 —— 体脂测量频率低，
            // 跟着最新记录走的话，只要最新那条没填这一行就消失了。
            // 没填过就整行不出现，卡片高度随之自适应。
            state.latestBodyFatPercent?.let { fat ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.goal_body_fat, fat.format1()),
                    style = typo.axis.tabular,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // ─────────── 右栏 ───────────
        Column(
            modifier = Modifier.width(129.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(R.string.label_level),
                style = typo.cardLabel,
                color = colors.textSecondary,
            )

            BmiLevelBar(sliderPos = state.level?.sliderPos)

            Text(
                text = state.level?.level?.label()
                    ?: stringResource(R.string.value_placeholder),
                style = typo.body.copy(fontWeight = FontWeight.Bold),
                color = if (state.level != null) colors.textPrimary else colors.textDisabled,
            )

            Text(
                text = stringResource(
                    R.string.label_bmi_value,
                    state.bmi?.format1() ?: stringResource(R.string.value_placeholder),
                ),
                style = typo.axis,
                color = colors.textSecondary,
            )
        }
    }
}
