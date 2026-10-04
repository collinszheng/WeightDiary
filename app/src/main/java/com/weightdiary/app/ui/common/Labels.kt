package com.weightdiary.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.weightdiary.app.R
import com.weightdiary.app.domain.model.Level
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * BMI 等级的显示文案。
 *
 * 放在 UI 层而不是 domain 层，是因为它需要 `stringResource`（决策 C5：字符串走 strings.xml）。
 * domain 里的 [Level] 只表达语义，不携带文案。
 */
@Composable
fun Level.label(): String = stringResource(
    when (this) {
        Level.UNDERWEIGHT -> R.string.level_underweight
        Level.NORMAL -> R.string.level_normal
        Level.OVERWEIGHT -> R.string.level_overweight
        Level.OBESE -> R.string.level_obese
    }
)

/**
 * BMI 等级对应的主题色。与目标卡的四色条、图表阈值线共用同一套 token，
 * 免得同一个「超重」在三个地方是三种红。
 */
@Composable
fun Level.color(): Color = with(WeightDiaryTheme.colors) {
    when (this@color) {
        Level.UNDERWEIGHT -> bmiUnderweight
        Level.NORMAL -> bmiNormal
        Level.OVERWEIGHT -> bmiOverweight
        Level.OBESE -> bmiObese
    }
}
