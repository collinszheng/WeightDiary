package com.weightdiary.app.ui.sheet

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.validation.RecordValidator
import com.weightdiary.app.domain.record.RecordRow
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 「添加数据」底部弹窗。占约 3/4 屏高。
 *
 * 传了 [initial] 就进入**编辑模式**：预填原值、标题换成「编辑数据」。
 * 保存动作由调用方决定走新增还是更新 —— 这个弹窗只管收集与校验。
 *
 * 校验是**就地同步**的：非法值立刻在字段下方提示、保存按钮同时禁用（决策 B10）。
 * 不把校验推到 ViewModel 再回传，是因为那样用户得先点保存才知道哪填错了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordSheet(
    onDismiss: () -> Unit,
    onSubmit: (weightKg: Double, measuredAt: Instant, bodyFatPercent: Double?, note: String?) -> Unit,
    initial: RecordRow? = null,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var weightText by rememberSaveable {
        mutableStateOf(initial?.weightKg?.format1().orEmpty())
    }
    var bodyFatText by rememberSaveable {
        mutableStateOf(initial?.bodyFatPercent?.format1().orEmpty())
    }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var measuredAtMillis by rememberSaveable {
        mutableStateOf(initial?.measuredAt?.toEpochMilli() ?: System.currentTimeMillis())
    }
    var submitted by rememberSaveable { mutableStateOf(false) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(
            topStart = dimen.radiusSheetTop,
            topEnd = dimen.radiusSheetTop,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = dimen.pageHorizontal),
        ) {
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
                        scope.launch {
                            sheetState.hide()
                            onSubmit(
                                w,
                                measuredAt,
                                bodyFat,
                                note.trim().takeIf { it.isNotEmpty() },
                            )
                        }
                    }
                },
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
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
