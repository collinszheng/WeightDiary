package com.weightdiary.app.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.ui.common.formatShortDateTime
import com.weightdiary.app.ui.home.ExperimentalUi
import com.weightdiary.app.ui.home.HealthConnectUi
import com.weightdiary.app.ui.home.SyncAvailabilityUi
import com.weightdiary.app.ui.home.SyncRowAction
import com.weightdiary.app.ui.home.syncRowAction
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 设置。**整屏**，不是底部弹窗。
 *
 * 入口按钮在右上角，再用从下往上弹的弹窗就不呼应了 ——
 * 和「添加数据」那个悬浮按钮是同一个道理。
 *
 * 四块：BMI 标准、数据管理、实验功能、关于。
 * 数据管理走 SAF（系统文件选择器）而不是自己写文件：不需要存储权限，
 * 用户自己决定存到哪，也不会有「App 偷偷写了什么」的疑虑。
 */
@Composable
fun SettingsScreen(
    bmiStandard: BmiStandard,
    recordCount: Int,
    versionName: String,
    healthConnect: HealthConnectUi,
    experimental: ExperimentalUi,
    onBack: () -> Unit,
    onBmiStandardChange: (BmiStandard) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onHealthConnectClick: () -> Unit,
    onExperimentalEnabledChange: (Boolean) -> Unit,
    onManualSyncChange: (Boolean) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onClearData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            // 背景铺满含状态栏区域，只把内容下移避开
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        // ─────────── 顶栏：返回 + 居中标题 ───────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimen.topBarHeight),
        ) {
            Text(
                text = stringResource(R.string.sheet_settings_title),
                style = typo.screenTitle,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
            BackArrowButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // 让 20dp 的视觉箭头落在距左边缘 16dp 处
                    .padding(start = dimen.pageHorizontal - (dimen.minTouchTarget - 20.dp) / 2),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimen.pageHorizontal),
        ) {
            Spacer(Modifier.height(4.dp))

            // ─────────── BMI 标准 ───────────
            SectionLabel(stringResource(R.string.settings_section_bmi))
            SettingsGroup {
                Column(modifier = Modifier.padding(dimen.cardPadding)) {
                    BmiStandardSelector(selected = bmiStandard, onSelect = onBmiStandardChange)
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

            // 换机即丢是纯本地方案的固有代价（决策 Q4），而导出是唯一的兜底。
            // 但用户只会在丢了数据之后才意识到，所以这里常驻一句提醒。
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.settings_data_hint),
                style = typo.cardLabel,
                color = colors.textSecondary,
                modifier = Modifier.padding(start = 4.dp),
            )

            // ─────────── 实验功能 ───────────
            //
            // 这一节先给一个**总开关**（默认关），打开后才露出体脂秤同步和两种同步方式。
            // 理由：它能不能用**取决于第三方 App 愿不愿意往 HC 写**，不是本 App 的能力 ——
            // 小米官方那个 App 就不写（实测）。默认亮着会让人以为这是自带功能，
            // 用不了时只会觉得是坏的。
            //
            // API < 28 时**整节不出现**（连标题一起）：宁可功能不出现，也不能留一张
            // 空卡片配一句「此功能不支持」（docs/06 §2.2）
            if (healthConnect.availability != SyncAvailabilityUi.UNSUPPORTED) {
                // 那一行只在**有动作可做**时可点（建锚点 / 授权 / 装 HC），
                // 配好之后退化成纯信息 —— 挂着箭头却点不动比没有箭头更糟
                val action = syncRowAction(healthConnect)

                Spacer(Modifier.height(dimen.sectionGap))

                SectionLabel(stringResource(R.string.settings_section_experimental))
                SettingsGroup {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_experimental_enable),
                        description = stringResource(R.string.settings_experimental_enable_desc),
                        checked = experimental.enabled,
                        onCheckedChange = onExperimentalEnabledChange,
                    )

                    if (experimental.enabled) {
                        GroupDivider()
                        SettingsRow(
                            title = stringResource(R.string.settings_health_connect),
                            description = healthConnectDescription(healthConnect),
                            onClick = onHealthConnectClick.takeIf {
                                action != SyncRowAction.NONE &&
                                    action != SyncRowAction.UNSUPPORTED
                            },
                        )
                        GroupDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_manual_sync),
                            description = stringResource(R.string.settings_manual_sync_desc),
                            checked = experimental.manualSync,
                            onCheckedChange = onManualSyncChange,
                        )
                        GroupDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_auto_sync),
                            description = stringResource(R.string.settings_auto_sync_desc),
                            checked = experimental.autoSync,
                            onCheckedChange = onAutoSyncChange,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_hc_experiment_hint),
                    style = typo.cardLabel,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 4.dp),
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

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** 顶栏左侧的返回箭头 */
@Composable
private fun BackArrowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = WeightDiaryTheme.colors
    val dimen = WeightDiaryTheme.dimens
    val description = stringResource(R.string.action_back)

    Box(
        modifier = modifier
            .size(dimen.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = 1.8.dp.toPx()
            val midY = size.height * 0.5f
            val tipX = size.width * 0.28f
            val backX = size.width * 0.78f

            drawLine(
                color = colors.textPrimary,
                start = Offset(backX, size.height * 0.16f),
                end = Offset(tipX, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = colors.textPrimary,
                start = Offset(tipX, midY),
                end = Offset(backX, size.height * 0.84f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
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

/**
 * 列表行。
 *
 * @param onClick 传 null 表示**这一行当前没有动作可做**：此时不可点、也不画箭头，
 *   退化成纯信息行。留着箭头却点不动，比没有箭头更让人困惑
 */
@Composable
private fun SettingsRow(
    title: String,
    description: String,
    onClick: (() -> Unit)? = null,
    danger: Boolean = false,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
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
        if (onClick != null) {
            Spacer(Modifier.size(8.dp))
            ChevronRight(tint = colors.textDisabled)
        }
    }
}

/**
 * 带开关的列表行。
 *
 * 开关的未选中态必须**显式配色**：主题里 `surfaceVariant = cardFill`、`outline = cardBorder`，
 * 而这一行本身就画在 cardFill 上 —— 用 M3 默认值的话轨道与卡片同色，只剩一圈浅灰边，
 * 看起来像坏的。选中态用主题默认值（accent 轨道 + 白滑块）即可。
 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整行可点：开关本身只有 32×20，让整行都能切更符合预期
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = dimen.cardPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typo.body,
                color = colors.textPrimary,
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
        Switch(
            checked = checked,
            // 整行已经处理点击了。这里传 null，免得读屏把同一件事报两遍
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = colors.textDisabled,
                uncheckedTrackColor = colors.divider,
                uncheckedBorderColor = colors.cardBorder,
            ),
        )
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

/**
 * 体脂秤那一行的说明文字。
 *
 * 顺序有讲究：**首次接入的引导排在所有状态之前** —— 库里一条记录都没有时，
 * 时间边界和异常过滤的锚点都不存在，这时候去读 HC 会把一家人的数据无差别吞进来
 * （`docs/08` §6.2）。所以必须先让用户记一条自己的体重。
 */
@Composable
private fun healthConnectDescription(state: HealthConnectUi): String = when {
    state.needsAnchor -> stringResource(R.string.settings_hc_needs_anchor)

    state.availability == SyncAvailabilityUi.NOT_INSTALLED ->
        stringResource(R.string.settings_hc_not_installed)

    state.availability == SyncAvailabilityUi.NEEDS_UPDATE ->
        stringResource(R.string.settings_hc_needs_update)

    state.syncing -> stringResource(R.string.settings_hc_syncing)

    !state.granted -> stringResource(R.string.settings_hc_needs_permission)

    state.lastSyncAt != null -> stringResource(
        R.string.settings_hc_synced,
        state.lastSyncAt.formatShortDateTime(),
        state.syncedCount,
    )

    else -> stringResource(R.string.settings_hc_ready)
}
