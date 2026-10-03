package com.weightdiary.app.ui.sheet.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import com.weightdiary.app.ui.theme.tabular

/**
 * 有错误时才加描边。
 *
 * 不能写成 `Modifier.border(if (error != null) 1.dp else 0.dp, ...)` ——
 * Compose 里 0dp 的 border 会退化成 1px 发丝线，没有错误时也会显出一道红边。
 */
@Composable
private fun errorBorder(error: String?, shape: Shape): Modifier {
    val colors = WeightDiaryTheme.colors
    return if (error != null) Modifier.border(1.dp, colors.deltaUp, shape) else Modifier
}

@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {    val typo = WeightDiaryTheme.typography
    val colors = WeightDiaryTheme.colors
    Text(
        text = text,
        style = typo.screenTitle,
        color = colors.textPrimary,
        modifier = modifier,
    )
}

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    val typo = WeightDiaryTheme.typography
    val colors = WeightDiaryTheme.colors
    Text(text = text, style = typo.cardLabel, color = colors.textSecondary, modifier = modifier)
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    val typo = WeightDiaryTheme.typography
    val colors = WeightDiaryTheme.colors
    Text(
        text = text,
        style = typo.axis,
        color = colors.deltaUp,
        modifier = modifier.padding(top = 4.dp),
    )
}

/**
 * 大号数字输入（体重 / 体脂率）。
 *
 * 用 `BasicTextField` 而不是 `OutlinedTextField`：设计稿要的是「灰底 + 30sp 粗体数字 + 单位后缀」，
 * Material 的文本框自带标签浮动、边框、内边距，改起来比自绘还费劲。
 */
@Composable
fun BigNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val shape = RoundedCornerShape(WeightDiaryTheme.dimens.radiusField)
    val numberStyle = typo.valueLarge.copy(fontSize = 30.sp, color = colors.textPrimary).tabular

    Column(modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(shape)
                .background(colors.fieldFill)
                // 注意：不能写成 border(if (error != null) 1.dp else 0.dp)。
                // Compose 里 0dp 的 border 会退化成 1px 发丝线，没错误时也会显出一道边。
                .then(errorBorder(error, shape))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, style = numberStyle, color = colors.textDisabled)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = numberStyle,
                    singleLine = true,
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(unit, style = typo.caption, color = colors.textSecondary)
        }
        error?.let { ErrorText(it) }
    }
}

/**
 * 单行字段。`readOnly = true` + `onClick` 时表现为可点击的选择行（日期时间）。
 */
@Composable
fun PlainField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit = {},
    placeholder: String = "",
    trailing: (@Composable () -> Unit)? = null,
    error: String? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val shape = RoundedCornerShape(WeightDiaryTheme.dimens.radiusField)
    val bodyStyle = typo.body.copy(color = colors.textPrimary)

    Column(modifier) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(colors.fieldFill)
                .then(errorBorder(error, shape))
                .then(
                    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (readOnly) {
                Text(
                    text = value.ifEmpty { placeholder },
                    style = bodyStyle,
                    color = if (value.isEmpty()) colors.textDisabled else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = bodyStyle, color = colors.textDisabled)
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = bodyStyle,
                        singleLine = true,
                        cursorBrush = SolidColor(colors.accent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            trailing?.invoke()
        }
        error?.let { ErrorText(it) }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(WeightDiaryTheme.dimens.radiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = Color.White,
            disabledContainerColor = colors.divider,
            disabledContentColor = colors.textDisabled,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {
        Text(text, style = typo.body.copy(fontWeight = FontWeight.Bold))
    }
}

/** 选择行右侧的下拉小箭头 */
@Composable
fun ChevronDown(modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    Canvas(modifier.size(14.dp)) {
        val stroke = 1.5.dp.toPx()
        drawLine(
            color = colors.textSecondary,
            start = Offset(size.width * 0.15f, size.height * 0.35f),
            end = Offset(size.width * 0.5f, size.height * 0.7f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = colors.textSecondary,
            start = Offset(size.width * 0.5f, size.height * 0.7f),
            end = Offset(size.width * 0.85f, size.height * 0.35f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
