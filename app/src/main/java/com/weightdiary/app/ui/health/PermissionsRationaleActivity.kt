package com.weightdiary.app.ui.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.weightdiary.app.R
import com.weightdiary.app.ui.theme.WeightDiaryTheme

/**
 * Health Connect 的**权限说明页**。
 *
 * ⚠️ **这个 Activity 不是「上架合规的可选装饰」，缺了它同步功能直接不通。**
 * Health Connect 的授权页在弹出来之前会先解析 `ACTION_SHOW_PERMISSIONS_RATIONALE`，
 * 解析不到就直接 `finish()`，日志只留一句
 * `E PermissionsActivity: App should support rationale intent, finishing!`
 * —— 用户看到的现象是「点了那一行，什么都没发生」。
 *
 * 这个坑是在模拟器上实跑才发现的（`docs/04` §7.4）。当初以为它只影响 Play 审核。
 *
 * 两个 action 都是从实机提取核实的，不是凭记忆写的：
 * - `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` ← HC 控制器的 dex
 * - `android.intent.action.VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS` ← HC dex + 平台 `android.jar`
 */
class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            WeightDiaryTheme {
                PermissionsRationaleScreen(onDismiss = { finish() })
            }
        }
    }
}

@Composable
private fun PermissionsRationaleScreen(onDismiss: () -> Unit) {
    val colors = WeightDiaryTheme.colors
    val typo = WeightDiaryTheme.typography
    val dimen = WeightDiaryTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = dimen.pageHorizontal),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.settings_health_connect),
            style = typo.screenTitle,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.rationale_body),
            style = typo.body,
            color = colors.textPrimary,
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.rationale_privacy),
            style = typo.cardLabel,
            color = colors.textSecondary,
        )

        Spacer(Modifier.height(32.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(
                text = stringResource(R.string.rationale_dismiss),
                style = typo.body,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
