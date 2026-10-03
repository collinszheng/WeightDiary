package com.weightdiary.app.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.ui.sheet.components.SheetTitle
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 设置弹窗。
 *
 * 三块：BMI 标准、数据管理、关于。
 *
 * 数据管理走 SAF（系统文件选择器）而不是自己写文件：不需要存储权限，
 * 用户自己决定存到哪，也不会有「App 偷偷写了什么」的疑虑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    bmiStandard: BmiStandard,
    recordCount: Int,
    versionName: String,
    onBmiStandardChange: (BmiStandard) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearData: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var confirmingClear by remember { mutableStateOf(false) }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.settings_clear_title)) },
            text = { Text(stringResource(R.string.settings_clear_message, recordCount)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingClear = false
                    onClearData()
                }) {
                    Text(stringResource(R.string.action_clear), color = colors.bmiObese)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) {
                    Text(stringResource(R.string.action_cancel), color = colors.textSecondary)
                }
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.background,
        shape = RoundedCornerShape(topStart = dimen.radiusSheetTop, topEnd = dimen.radiusSheetTop),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = dimen.pageHorizontal),
        ) {
            SheetTitle(stringResource(R.string.sheet_settings_title))

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // ─────────── BMI 标准 ───────────
                SectionLabel(stringResource(R.string.settings_section_bmi))
                SettingsGroup {
                    Column(modifier = Modifier.padding(dimen.cardPadding)) {
                        BmiStandardSelector(
                            selected = bmiStandard,
                            onSelect = onBmiStandardChange,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_bmi_hint),
                            style = typo.cardLabel,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(dimen.sectionGap))

                // ─────────── 数据管理 ───────────
                SectionLabel(stringResource(R.string.settings_section_data))
                SettingsGroup {
                    SettingsRow(
                        title = stringResource(R.string.settings_export),
                        description = stringResource(R.string.settings_export_desc),
                        onClick = onExport,
                    )
                    GroupDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_import),
                        description = stringResource(R.string.settings_import_desc),
                        onClick = onImport,
                    )
                    GroupDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_clear),
                        description = stringResource(R.string.settings_clear_desc),
                        onClick = { confirmingClear = true },
                        danger = true,
                    )
                }

                Spacer(Modifier.height(dimen.sectionGap))

                // ─────────── 关于 ───────────
                SectionLabel(stringResource(R.string.settings_section_about))
                SettingsGroup {
                    Column(modifier = Modifier.padding(dimen.cardPadding)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = typo.body,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = stringResource(R.string.about_version, versionName),
                                style = typo.cardLabel,
                                color = colors.textSecondary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.about_privacy),
                            style = typo.cardLabel,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(Modifier.height(dimen.sectionGap))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val typo = WeightDiaryTheme.typography
    val colors = WeightDiaryTheme.colors

    Text(
        text = text,
        style = typo.cardLabel,
        color = colors.textSecondary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
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
private fun GroupDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = WeightDiaryTheme.dimens.cardPadding)
            .height(0.5.dp)
            .background(WeightDiaryTheme.colors.divider),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
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
        Spacer(Modifier.size(8.dp))
        ChevronRight(tint = colors.textDisabled)
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

/**
 * BMI 标准二选一。样式与图表 Tab 栏一致 —— 同一类「选一个」的控件不该有两种长相。
 */
@Composable
private fun BmiStandardSelector(
    selected: BmiStandard,
    onSelect: (BmiStandard) -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens
    val containerShape = RoundedCornerShape(dimen.radiusTabContainer)

    val options = listOf(
        BmiStandard.CHINA to stringResource(R.string.bmi_standard_china),
        BmiStandard.WHO to stringResource(R.string.bmi_standard_who),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(containerShape)
            .background(colors.divider)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        options.forEach { (standard, label) ->
            val isSelected = standard == selected
            val itemShape = RoundedCornerShape(8.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(itemShape)
                    .then(
                        if (isSelected) {
                            Modifier
                                .background(colors.background)
                                .border(0.5.dp, colors.cardBorder, itemShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable(role = Role.RadioButton) { onSelect(standard) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = typo.tab,
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
