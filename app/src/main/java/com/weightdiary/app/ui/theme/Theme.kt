package com.weightdiary.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * 把设计 token 接进 Compose。
 *
 * 目前的策略是**只用浅色**（决策 C1：深色模式本期不做）。因为颜色、字号、尺寸
 * 全部走 CompositionLocal 而不是硬编码，将来加深色只需要在这里按
 * `isSystemInDarkTheme()` 选一套 `WeightDiaryColors` 即可，业务代码不用动。
 */
private val MaterialLightScheme = lightColorScheme(
    primary = LightColors.accent,
    onPrimary = Color.White,
    background = LightColors.background,
    onBackground = LightColors.textPrimary,
    surface = LightColors.background,
    onSurface = LightColors.textPrimary,
    surfaceVariant = LightColors.cardFill,
    onSurfaceVariant = LightColors.textSecondary,
    outline = LightColors.cardBorder,
    error = LightColors.bmiObese,
    onError = Color.White,
)

@Composable
fun WeightDiaryTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWeightDiaryColors provides LightColors,
        LocalWeightDiaryTypography provides WeightDiaryType,
        LocalWeightDiaryDimens provides DefaultDimens,
    ) {
        MaterialTheme(
            colorScheme = MaterialLightScheme,
            shapes = WeightDiaryShapes,
            content = content,
        )
    }
}

/**
 * 设计 token 的统一入口，用法：
 *
 * ```
 * val colors = WeightDiaryTheme.colors
 * val typo   = WeightDiaryTheme.typography
 * val dimen  = WeightDiaryTheme.dimens
 * ```
 */
object WeightDiaryTheme {
    val colors: WeightDiaryColors
        @Composable @ReadOnlyComposable get() = LocalWeightDiaryColors.current

    val typography: WeightDiaryTypography
        @Composable @ReadOnlyComposable get() = LocalWeightDiaryTypography.current

    val dimens: WeightDiaryDimens
        @Composable @ReadOnlyComposable get() = LocalWeightDiaryDimens.current
}
