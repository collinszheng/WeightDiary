package com.weightdiary.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * 设置主页。**整屏**，而且只是一张导航表。
 *
 * 入口按钮在右上角，再用从下往上弹的弹窗就不呼应了 ——
 * 和「添加数据」那个悬浮按钮是同一个道理。
 *
 * 原来四节（BMI 标准 / 数据管理 / 实验功能 / 关于）全铺在一屏里，加起来十来行，
 * 越加越像说明书。现在收起来：主页只留四行入口，点进去才是具体设置，
 * 各页在 `SettingsPages.kt` 里。
 *
 * 两个刻意的取舍（决策 §7.10）：
 * - **不加搜索**。一共四行，搜索框本身比它要找的东西还占地方
 * - **主页不留副标题**，四行都是纯导航、右侧只有箭头。代价是主页看不出
 *   「实验功能」开着没有 —— 要状态就得点进去
 *
 * 数据管理走 SAF（系统文件选择器）而不是自己写文件：不需要存储权限，
 * 用户自己决定存到哪，也不会有「App 偷偷写了什么」的疑虑。
 */
@Composable
fun SettingsScreen(
    healthConnectSupported: Boolean,
    onBack: () -> Unit,
    onOpenBmiStandard: () -> Unit,
    onOpenData: () -> Unit,
    onOpenExperimental: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimen = WeightDiaryTheme.dimens

    SettingsPageScaffold(
        title = stringResource(R.string.sheet_settings_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Spacer(Modifier.height(4.dp))

        SettingsGroup {
            SettingsNavRow(
                title = stringResource(R.string.settings_section_bmi),
                onClick = onOpenBmiStandard,
            )
            GroupDivider()
            SettingsNavRow(
                title = stringResource(R.string.settings_section_data),
                onClick = onOpenData,
            )
            // API < 28 时**这一行也不出现**（和原来整节不出现是同一个规则）：
            // 宁可功能不出现，也不能留一个点进去什么都没有的入口（docs/06 §2.2）
            if (healthConnectSupported) {
                GroupDivider()
                SettingsNavRow(
                    title = stringResource(R.string.settings_section_experimental),
                    onClick = onOpenExperimental,
                )
            }
            GroupDivider()
            SettingsNavRow(
                title = stringResource(R.string.settings_section_about),
                onClick = onOpenAbout,
            )
        }

        Spacer(Modifier.height(dimen.sectionGap))
    }
}
