package com.weightdiary.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 设置各页共用的零件。
 *
 * 单独放一个文件，是为了让 [SettingsScreen]（主页，只是一张导航表）和
 * `SettingsPages.kt`（主页点进去的四页）都只管自己的布局 ——
 * 行、组、分割线、顶栏的长相只有这一处定义。
 */

/**
 * 设置某一页的外壳：顶栏（返回箭头 + 居中标题）+ 可滚动内容 + 底部安全区。
 *
 * 设置主页和它的四个子页长得完全一样，差别只有标题和内容 ——
 * 所以主页也用它，标题就是「设置」。
 */
@Composable
internal fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // 背景铺满含状态栏区域，只把内容下移避开
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimen.topBarHeight),
        ) {
            Text(
                text = title,
                style = typo.screenTitle,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            BackArrowButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // 让 20dp 的视觉箭头落在距左边缘 16dp 处
                    .padding(start = dimen.pageHorizontal - (dimen.minTouchTarget - 20.dp) / 2),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimen.pageHorizontal),
            content = content,
        )

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** 顶栏左侧的返回箭头 */
@Composable
private fun BackArrowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = stringResource(R.string.action_back)

    Box(
        modifier = modifier
            .size(dimen.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.8.dp.toPx()
            val midY = size.height * 0.5f
            val tipX = size.width * 0.28f
            val backX = size.width * 0.78f

            drawLine(
                color = colors.textPrimary,
                start = Offset(backX, size.height * 0.16f),
                end = Offset(tipX, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.textPrimary,
                start = Offset(tipX, midY),
                end = Offset(backX, size.height * 0.84f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(WeightDiaryTheme.dimens.radiusCard)
    val colors = WeightDiaryTheme.colors

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.cardFill)
            .border(0.5.dp, colors.cardBorder, shape),
    ) {
        content()
    }
}

@Composable
internal fun GroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WeightDiaryTheme.dimens.cardPadding)
            .height(0.5.dp)
            .background(WeightDiaryTheme.colors.divider),
    )
}

/**
 * 列表行：标题 + 一句说明 + 箭头。
 *
 * @param onClick 传 null 表示**这一行当前没有动作可做**：此时不可点、也不画箭头，
 *   退化成纯信息行。留着箭头却点不动，比没有箭头更让人困惑
 */
@Composable
internal fun SettingsRow(
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = dimen.cardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typo.body,
                color = if (danger) colors.bmiObese else colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = typo.cardLabel,
                color = colors.textSecondary,
            )
        }
        if (onClick != null) {
            Spacer(Modifier.size(8.dp))
            ChevronRight(tint = colors.textDisabled)
        }
    }
}

/**
 * 纯导航行：只有标题和一个箭头。
 *
 * 设置主页用它。**刻意不留副标题**（决策 §7.10）：四行都是入口，
 * 有副标题就又要读第二行字，主页又变回一屏文字。
 * 代价是主页看不出「实验功能」开着没有 —— 要状态就得点进去。
 */
@Composable
internal fun SettingsNavRow(title: String, onClick: () -> Unit) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            // 比 SettingsRow 略胖：它只有一行字，12dp 会显得太扁
            .padding(horizontal = dimen.cardPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = typo.body,
            color = colors.textPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.size(8.dp))
        ChevronRight(tint = colors.textDisabled)
    }
}

/**
 * 带开关的列表行。
 *
 * 开关的未选中态必须**显式配色**：主题里 `surfaceVariant = cardFill`、`outline = cardBorder`，
 * 而这一行本身就画在 cardFill 上 —— 用 M3 默认值的话轨道与卡片同色，只剩一圈浅灰边，
 * 看起来像坏的。选中态用主题默认值（accent 轨道 + 白滑块）即可。
 */
@Composable
internal fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整行可点：开关本身只有 32×20，让整行都能切更符合预期
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = dimen.cardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typo.body,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = typo.cardLabel,
                color = colors.textSecondary,
            )
        }
        Spacer(Modifier.size(8.dp))
        Switch(
            checked = checked,
            // 整行已经处理点击了。这里传 null，免得读屏把同一件事报两遍
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = colors.textDisabled,
                uncheckedTrackColor = colors.divider,
                uncheckedBorderColor = colors.cardBorder,
            ),
        )
    }
}

/** 列表行右侧的指示箭头 */
@Composable
private fun ChevronRight(tint: Color) {
    Canvas(modifier = Modifier.size(10.dp)) {
        val stroke = 1.5.dp.toPx()
        drawLine(
            color = tint,
            start = Offset(size.width * 0.2f, size.height * 0.1f),
            end = Offset(size.width * 0.75f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.75f, size.height * 0.5f),
            end = Offset(size.width * 0.2f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
