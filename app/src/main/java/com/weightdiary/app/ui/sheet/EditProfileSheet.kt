package com.weightdiary.app.ui.sheet

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.validation.RecordValidator
import com.weightdiary.app.ui.common.NumberInput
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.common.formatTrimmed
import com.weightdiary.app.ui.common.message
import com.weightdiary.app.ui.sheet.components.BigNumberField
import com.weightdiary.app.ui.sheet.components.PrimaryButton
import com.weightdiary.app.ui.sheet.components.SheetTitle
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import kotlinx.coroutines.launch

/**
 * 「编辑个人资料」底部弹窗：身高 + 目标体重。
 *
 * 这是全 App **唯一的身高修改入口**（决策 A5）—— 概览卡片上的身高卡不可点，
 * 入口收在目标卡片右侧的铅笔图标上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    initialHeightCm: Double?,
    initialTargetWeightKg: Double?,
    onDismiss: () -> Unit,
    onSave: (heightCm: Double?, targetWeightKg: Double?) -> Unit,
    /** 从 BMI 卡点进来时给的说明：BMI 算不出来是因为缺身高 */
    hint: String? = null,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var heightText by rememberSaveable {
        mutableStateOf(initialHeightCm?.formatTrimmed().orEmpty())
    }
    var targetText by rememberSaveable {
        mutableStateOf(initialTargetWeightKg?.format1().orEmpty())
    }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val height = heightText.toDoubleOrNull()
    val target = targetText.toDoubleOrNull()

    val heightError = RecordValidator.validateHeight(height)
    val targetError = RecordValidator.validateTargetWeight(target)
    val canSave = heightError == null && targetError == null

    val shownHeightError = heightError?.takeIf { heightText.isNotEmpty() || submitted }
    val shownTargetError = targetError?.takeIf { targetText.isNotEmpty() || submitted }

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
            SheetTitle(stringResource(R.string.sheet_edit_profile_title))

            if (hint != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = typo.caption,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BigNumberField(
                    label = stringResource(R.string.field_height),
                    value = heightText,
                    onValueChange = { heightText = NumberInput.sanitizeDecimal(it, maxDecimals = 1) },
                    unit = stringResource(R.string.unit_cm),
                    error = shownHeightError?.message(),
                )

                BigNumberField(
                    label = stringResource(R.string.field_target_weight),
                    value = targetText,
                    onValueChange = { targetText = NumberInput.sanitizeDecimal(it, maxDecimals = 1) },
                    unit = stringResource(R.string.unit_kg),
                    placeholder = stringResource(R.string.placeholder_optional),
                    error = shownTargetError?.message(),
                )
            }

            PrimaryButton(
                text = stringResource(R.string.action_save),
                enabled = canSave,
                onClick = {
                    submitted = true
                    if (canSave) {
                        scope.launch {
                            sheetState.hide()
                            onSave(height, target)
                        }
                    }
                },
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}
