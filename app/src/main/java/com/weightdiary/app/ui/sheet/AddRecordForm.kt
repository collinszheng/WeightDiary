package com.weightdiary.app.ui.sheet

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.domain.validation.RecordValidator
import com.weightdiary.app.ui.common.NumberInput
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.formatDateTime
import com.weightdiary.app.ui.common.message
import com.weightdiary.app.ui.sheet.components.BigNumberField
import com.weightdiary.app.ui.sheet.components.ChevronDown
import com.weightdiary.app.ui.sheet.components.PlainField
import com.weightdiary.app.ui.sheet.components.PrimaryButton
import com.weightdiary.app.ui.sheet.components.SheetTitle
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 「添加 / 编辑数据」的表单内容 —— **不含弹窗外壳**。
 *
 * 拆出来是因为「全部记录」弹窗需要在**不关闭自己的前提下**把内容换成编辑表单：
 * 关掉再弹一次会让用户看到「收回 → 弹出 → 收回 → 弹出」，很乱。
 *
 * 传了 [initial] 就进入编辑模式：预填原值、标题换成「编辑数据」。
 * 校验是**就地同步**的：非法值立刻在字段下方提示、保存按钮同时禁用（决策 B10）。
 * 不把校验推到 ViewModel 再回传，是因为那样用户得先点保存才知道哪填错了。
 *
 * @param onBack 非空时在标题上方显示「‹ 全部记录」，用于退回列表而不关闭弹窗
 */
@Composable
fun AddRecordForm(
    initial: RecordRow?,
    onSave: (weightKg: Double, measuredAt: Instant, bodyFatPercent: Double?, note: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** 非空时在保存按钮下方显示「删除」。长按和左滑都没有视觉提示，这里是唯一看得见的删除入口 */
    onDelete: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    // 换一条记录编辑时要重新预填，所以 key 到记录 id
    val formKey = initial?.id
    var weightText by rememberSaveable(formKey) {
        mutableStateOf(initial?.weightKg?.format1().orEmpty())
    }
    var bodyFatText by rememberSaveable(formKey) {
        mutableStateOf(initial?.bodyFatPercent?.format1().orEmpty())
    }
    var note by rememberSaveable(formKey) { mutableStateOf(initial?.note.orEmpty()) }
    var measuredAtMillis by rememberSaveable(formKey) {
        mutableStateOf(initial?.measuredAt?.toEpochMilli() ?: System.currentTimeMillis())
    }
    var submitted by rememberSaveable(formKey) { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val measuredAt = Instant.ofEpochMilli(measuredAtMillis)
    val weight = weightText.toDoubleOrNull()
    val bodyFat = bodyFatText.toDoubleOrNull()

    val weightError = RecordValidator.validateWeight(weight)
    val bodyFatError = RecordValidator.validateBodyFat(bodyFat)
    val dateError = RecordValidator.validateMeasuredAt(measuredAt)
    val canSave = weightError == null && bodyFatError == null && dateError == null

    // 只在用户动过这个字段（或点过保存）之后才报红，避免一打开就满屏错误
    val shownWeightError = weightError?.takeIf { weightText.isNotEmpty() || submitted }
    val shownBodyFatError = bodyFatError?.takeIf { bodyFatText.isNotEmpty() }
    val shownDateError = dateError?.takeIf { submitted }

    DateTimePickers(
        measuredAtMillis = measuredAtMillis,
        showDatePicker = showDatePicker,
        showTimePicker = showTimePicker,
        onDatePicked = { measuredAtMillis = it; showDatePicker = false },
        onTimePicked = { measuredAtMillis = it; showTimePicker = false },
        onDismissDate = { showDatePicker = false },
        onDismissTime = { showTimePicker = false },
    )

    Column(modifier = modifier) {
        if (onBack != null) {
            BackToRecordsRow(onClick = onBack)
            Spacer(Modifier.height(4.dp))
        }

        SheetTitle(
            stringResource(
                if (initial != null) R.string.sheet_edit_title else R.string.sheet_add_title,
            )
        )

        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlainField(
                label = stringResource(R.string.field_datetime),
                value = measuredAt.formatDateTime(),
                readOnly = true,
                onClick = { showDatePicker = true },
                trailing = { ChevronDown() },
                error = shownDateError?.message(),
            )

            BigNumberField(
                label = stringResource(R.string.field_weight),
                value = weightText,
                onValueChange = { weightText = NumberInput.sanitizeDecimal(it, maxDecimals = 1) },
                unit = stringResource(R.string.unit_kg),
                error = shownWeightError?.message(),
            )

            BigNumberField(
                label = stringResource(R.string.field_body_fat),
                value = bodyFatText,
                onValueChange = { bodyFatText = NumberInput.sanitizeDecimal(it, maxDecimals = 1) },
                unit = stringResource(R.string.unit_percent),
                placeholder = stringResource(R.string.placeholder_optional),
                error = shownBodyFatError?.message(),
            )

            PlainField(
                label = stringResource(R.string.field_note),
                value = note,
                onValueChange = { note = it },
                placeholder = stringResource(R.string.placeholder_note),
            )

            Text(
                text = stringResource(R.string.privacy_local_only),
                style = typo.cardLabel,
                color = colors.textDisabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }

        PrimaryButton(
            text = stringResource(R.string.action_save),
            enabled = canSave,
            onClick = {
                submitted = true
                val w = weight
                if (w != null) {
                    onSave(w, measuredAt, bodyFat, note.trim().takeIf { it.isNotEmpty() })
                }
            },
            modifier = Modifier.padding(top = 16.dp),
        )

        if (onDelete != null) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    style = typo.body,
                    color = colors.bmiObese,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 「‹ 全部记录」：在弹窗内部退回列表，而不是把弹窗关掉 */
@Composable
private fun BackToRecordsRow(onClick: () -> Unit) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(9.dp)) {
            val stroke = 1.5.dp.toPx()
            drawLine(
                color = colors.textSecondary,
                start = Offset(size.width * 0.85f, size.height * 0.1f),
                end = Offset(size.width * 0.15f, size.height * 0.5f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.textSecondary,
                start = Offset(size.width * 0.15f, size.height * 0.5f),
                end = Offset(size.width * 0.85f, size.height * 0.9f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.sheet_all_records_title),
            style = typo.caption,
            color = colors.textSecondary,
        )
    }
}

/**
 * 日期 / 时间选择器。
 *
 * 用平台自带的 `DatePickerDialog` / `TimePickerDialog` 而不是 Material3 的实验性
 * `DatePicker`：它们能直接设 `maxDate` 来禁止未来日期（决策 B11），
 * 也省掉一堆 `@OptIn(ExperimentalMaterial3Api::class)` 的 API 变动风险。
 */
@Composable
private fun DateTimePickers(
    measuredAtMillis: Long,
    showDatePicker: Boolean,
    showTimePicker: Boolean,
    onDatePicked: (Long) -> Unit,
    onTimePicked: (Long) -> Unit,
    onDismissDate: () -> Unit,
    onDismissTime: () -> Unit,
) {
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    if (showDatePicker) {
        DisposableEffect(measuredAtMillis) {
            val zoned = Instant.ofEpochMilli(measuredAtMillis).atZone(zone)
            val dialog = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    val date = LocalDate.of(year, month + 1, dayOfMonth)
                    onDatePicked(
                        ZonedDateTime.of(date, zoned.toLocalTime(), zone).toInstant().toEpochMilli()
                    )
                },
                zoned.year,
                zoned.monthValue - 1,
                zoned.dayOfMonth,
            )
            dialog.datePicker.maxDate = System.currentTimeMillis()
            dialog.setOnDismissListener { onDismissDate() }
            dialog.show()
            onDispose { if (dialog.isShowing) dialog.dismiss() }
        }
    }

    if (showTimePicker) {
        DisposableEffect(measuredAtMillis) {
            val zoned = Instant.ofEpochMilli(measuredAtMillis).atZone(zone)
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    val time = LocalTime.of(hour, minute)
                    onTimePicked(
                        ZonedDateTime.of(zoned.toLocalDate(), time, zone).toInstant().toEpochMilli()
                    )
                },
                zoned.hour,
                zoned.minute,
                DateFormat.is24HourFormat(context),
            )
            dialog.setOnDismissListener { onDismissTime() }
            dialog.show()
            onDispose { if (dialog.isShowing) dialog.dismiss() }
        }
    }
}
