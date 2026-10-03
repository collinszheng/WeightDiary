package com.weightdiary.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 字号规范。对应 [docs/02-设计规范.md §2.4]。
 *
 * 设计稿的档位与 Material3 的 TypeScale 对不上，所以单独定义而不是塞进 `Typography`。
 */
@Immutable
data class WeightDiaryTypography(
    /** 页面标题 17sp SemiBold */
    val screenTitle: TextStyle,
    /** 卡片标签 12sp Medium */
    val cardLabel: TextStyle,
    /** 主数值 28sp Bold */
    val valueLarge: TextStyle,
    /** 次级数值 20sp Medium */
    val valueMedium: TextStyle,
    /** 正文 15sp Regular */
    val body: TextStyle,
    /** 辅助文字 13sp Regular */
    val caption: TextStyle,
    /** Tab 13sp Medium */
    val tab: TextStyle,
    /** 图表轴文字 11sp Regular */
    val axis: TextStyle,
    /** 图表目标线标签 10sp Regular */
    val axisLabel: TextStyle,
    /** 数值后的单位小字 11sp */
    val unit: TextStyle,
)

private val Sans = FontFamily.SansSerif

internal val WeightDiaryType = WeightDiaryTypography(
    screenTitle = TextStyle(fontFamily = Sans, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    cardLabel = TextStyle(fontFamily = Sans, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    valueLarge = TextStyle(fontFamily = Sans, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    valueMedium = TextStyle(fontFamily = Sans, fontSize = 20.sp, fontWeight = FontWeight.Medium),
    body = TextStyle(fontFamily = Sans, fontSize = 15.sp, fontWeight = FontWeight.Normal),
    caption = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Normal),
    tab = TextStyle(fontFamily = Sans, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    axis = TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Normal),
    axisLabel = TextStyle(fontFamily = Sans, fontSize = 10.sp, fontWeight = FontWeight.Normal),
    unit = TextStyle(fontFamily = Sans, fontSize = 11.sp, fontWeight = FontWeight.Normal),
)

val LocalWeightDiaryTypography = staticCompositionLocalOf { WeightDiaryType }

/**
 * 等宽数字字形。
 *
 * 大号体重数字做动画变化时，非等宽数字（比如 `1` 比 `8` 窄）会让整块宽度抖动，
 * 所以凡是会变的主数值都要套这个。
 */
val TextStyle.tabular: TextStyle
    get() = copy(fontFeatureSettings = "tnum")
