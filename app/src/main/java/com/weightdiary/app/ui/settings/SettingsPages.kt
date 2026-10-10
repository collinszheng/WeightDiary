package com.weightdiary.app.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
 * 设置主页点进去的四页。
 *
 * 每页只装原来那一节的内容，顶栏标题就是那一节的名称 ——
 * 所以从「设置」点进来之后，用户看到的标题能确认自己进对了地方。
 */

/** BMI 标准。二选一，选中即生效 */
@Composable
fun BmiStandardPage(
    bmiStandard: BmiStandard,
    onSelect: (BmiStandard) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    SettingsPageScaffold(
        title = stringResource(R.string.settings_section_bmi),
        onBack = onBack,
        modifier = modifier,
    ) {
        Spacer(Modifier.height(4.dp))

        SettingsGroup {
            Column(modifier = Modifier.padding(dimen.cardPadding)) {
                BmiStandardSelector(selected = bmiStandard, onSelect = onSelect)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_bmi_hint),
                    style = typo.cardLabel,
                    color = colors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(dimen.sectionGap))
    }
}

/** 数据管理：导出、导入、清空 */
@Composable
fun DataManagementPage(
    recordCount: Int,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onClearData: () -> Unit,
    onBack: () -> Unit,
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

    SettingsPageScaffold(
        title = stringResource(R.string.settings_section_data),
        onBack = onBack,
        modifier = modifier,
    ) {
        Spacer(Modifier.height(4.dp))

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

        Spacer(Modifier.height(dimen.sectionGap))
    }
}

/**
 * 实验功能：总开关 + 体脂秤同步 + 两种同步方式。
 *
 * 先给一个**总开关**（默认关），打开后才露出后面几行。
 * 理由：它能不能用**取决于第三方 App 愿不愿意往 HC 写**，不是本 App 的能力 ——
 * 小米官方那个 App 就不写（实测）。默认亮着会让人以为这是自带功能，
 * 用不了时只会觉得是坏的。
 */
@Composable
fun ExperimentalPage(
    healthConnect: HealthConnectUi,
    experimental: ExperimentalUi,
    onHealthConnectClick: () -> Unit,
    onExperimentalEnabledChange: (Boolean) -> Unit,
    onManualSyncChange: (Boolean) -> Unit,
    onAutoSyncChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    // 那一行只在**有动作可做**时可点（建锚点 / 授权 / 装 HC），
    // 配好之后退化成纯信息 —— 挂着箭头却点不动比没有箭头更糟
    val action = syncRowAction(healthConnect)

    SettingsPageScaffold(
        title = stringResource(R.string.settings_section_experimental),
        onBack = onBack,
        modifier = modifier,
    ) {
        Spacer(Modifier.height(4.dp))

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

        Spacer(Modifier.height(dimen.sectionGap))
    }
}

/** 关于：App 名、版本、隐私说明，以及把发布页交给浏览器的那一行 */
@Composable
fun AboutPage(
    versionName: String,
    onViewReleases: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    SettingsPageScaffold(
        title = stringResource(R.string.settings_section_about),
        onBack = onBack,
        modifier = modifier,
    ) {
        Spacer(Modifier.height(4.dp))

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
            GroupDivider()
            // 这一行只是把发布页**交给浏览器**打开 —— 本 App 不声明 INTERNET，
            // 所以它做不到「替你查有没有新版」，文案也照实说是「查看」而不是「检测」
            SettingsRow(
                title = stringResource(R.string.about_view_releases),
                description = stringResource(R.string.about_view_releases_desc),
                onClick = onViewReleases,
            )
        }

        Spacer(Modifier.height(dimen.sectionGap))
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
