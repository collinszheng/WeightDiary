package com.weightdiary.app.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.validation.RecordValidator
import com.weightdiary.app.ui.common.NumberInput
import com.weightdiary.app.ui.common.message
import com.weightdiary.app.ui.sheet.components.BigNumberField
import com.weightdiary.app.ui.sheet.components.PrimaryButton
import com.weightdiary.app.ui.sheet.components.SheetTitle
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import kotlinx.coroutines.launch

/**
 * 首次启动的身高引导。
 *
 * 身高是 BMI 的前置条件，不填的话首页有张卡片永远显示 `--`。
 * 但**允许跳过** —— 只想记体重不关心 BMI 的用户不该被拦住。
 * 跳过之后不再弹（`UserProfile.onboardingCompleted`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingSheet(
    onDismiss: () -> Unit,
    onSave: (heightCm: Double) -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var heightText by rememberSaveable { mutableStateOf("") }
    val height = heightText.toDoubleOrNull()
    val heightError = RecordValidator.validateHeight(height)
    val canSave = heightText.isNotEmpty() && heightError == null

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
                .fillMaxHeight(0.6f)
                .padding(horizontal = dimen.pageHorizontal),
        ) {
            SheetTitle(stringResource(R.string.sheet_onboarding_title))

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.sheet_onboarding_desc),
                style = typo.caption,
                color = colors.textSecondary,
            )

            Spacer(Modifier.height(20.dp))

            BigNumberField(
                label = stringResource(R.string.field_height),
                value = heightText,
                onValueChange = { heightText = NumberInput.sanitizeDecimal(it, maxDecimals = 1) },
                unit = stringResource(R.string.unit_cm),
                error = heightError?.takeIf { heightText.isNotEmpty() }?.message(),
            )

            Spacer(Modifier.weight(1f))

            PrimaryButton(
                text = stringResource(R.string.action_save),
                enabled = canSave,
                onClick = {
                    val h = height
                    if (h != null) {
                        scope.launch {
                            sheetState.hide()
                            onSave(h)
                        }
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.action_skip),
                        style = typo.body,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
