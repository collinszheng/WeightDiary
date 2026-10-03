package com.weightdiary.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * 原始色值。UI 代码里**不要直接引用这个文件里的私有常量**，
 * 一律通过 `WeightDiaryTheme.colors.<语义名>` 取，这样才能在加深色模式时只改一处。
 */

private val White = Color(0xFFFFFFFF)
private val GreyFA = Color(0xFFFAFAFA)
private val GreyF7 = Color(0xFFF7F7F9)
private val GreyF5 = Color(0xFFF5F5F7)
private val GreyF2 = Color(0xFFF2F2F7)
private val GreyF0 = Color(0xFFF0F0F0)
private val GreyC7 = Color(0xFFC7C7CC)
private val Grey8E = Color(0xFF8E8E93)
private val Grey1C = Color(0xFF1C1C1E)

private val Mint = Color(0xFF3DD68C)
private val MintSoft = Color(0x1F3DD68C) // 12% 透明

private val BmiYellow = Color(0xFFFFCC00)
private val BmiRedLight = Color(0xFFFF6B6B)
private val BmiRedDeep = Color(0xFFD0021B)

/**
 * 语义化色板。
 *
 * 命名对应 [docs/02-设计规范.md] 的 token 表，改色时两边一起改。
 */
@Immutable
data class WeightDiaryColors(
    val background: Color,
    /** 卡片填充。刻意不是纯白 —— 纯白卡片放在纯白背景上，阴影看不见 */
    val cardFill: Color,
    /** 0.5dp 发丝描边，用来替代 elevation */
    val cardBorder: Color,
    /** 选中态卡片填充 */
    val cardSelectedFill: Color,

    val accent: Color,
    /** 强调色 12% 透明，用于达成目标时的卡片高亮 */
    val accentSoft: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val divider: Color,
    val fieldFill: Color,

    val chartGridMajor: Color,
    val chartGridMinor: Color,

    val bmiUnderweight: Color,
    val bmiNormal: Color,
    val bmiOverweight: Color,
    val bmiObese: Color,

    /** 变化量：下降用强调绿 */
    val deltaDown: Color,
    /** 变化量：上升 */
    val deltaUp: Color,
)

internal val LightColors = WeightDiaryColors(
    background = White,
    cardFill = GreyFA,
    cardBorder = GreyF0,
    cardSelectedFill = GreyF7,

    accent = Mint,
    accentSoft = MintSoft,

    textPrimary = Grey1C,
    textSecondary = Grey8E,
    textDisabled = GreyC7,
    divider = GreyF2,
    fieldFill = GreyF5,

    chartGridMajor = GreyF0,
    chartGridMinor = GreyF2,

    bmiUnderweight = BmiYellow,
    bmiNormal = Mint,
    bmiOverweight = BmiRedLight,
    bmiObese = BmiRedDeep,

    deltaDown = Mint,
    deltaUp = BmiRedLight,
)

val LocalWeightDiaryColors = staticCompositionLocalOf { LightColors }
