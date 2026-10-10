package com.weightdiary.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.result.contract.ActivityResultContracts
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weightdiary.app.data.healthconnect.HealthConnectSource
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.home.ActiveSheet
import com.weightdiary.app.ui.home.HomeEvent
import com.weightdiary.app.ui.home.HomeScreen
import com.weightdiary.app.ui.home.HomeUiState
import com.weightdiary.app.domain.record.RecordCsv
import com.weightdiary.app.ui.home.HomeViewModel
import com.weightdiary.app.ui.home.Screen
import com.weightdiary.app.ui.home.SyncAvailabilityUi
import com.weightdiary.app.ui.home.SyncRowAction
import com.weightdiary.app.ui.home.syncRowAction
import com.weightdiary.app.ui.home.components.AddRecordFab
import com.weightdiary.app.ui.sheet.AddRecordSheet
import com.weightdiary.app.ui.sheet.AllRecordsSheet
import com.weightdiary.app.ui.sheet.EditProfileSheet
import com.weightdiary.app.ui.settings.SettingsScreen
import com.weightdiary.app.ui.sheet.OnboardingSheet
import com.weightdiary.app.ui.theme.WeightDiaryTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35+ 在 Android 15 上强制边到边。不调这个的话系统栏图标会按浅色绘制，
        // 在白底上等于不可见 —— 现象就是「状态栏消失了」。内容侧的 insets 避让在 HomeScreen 里做。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as WeightDiaryApp).container

        // 开发期造数，release 构建里是空操作
        DebugSeed.maybeSeed(intent, container.weightRepository, lifecycleScope)

        setContent {
            WeightDiaryTheme {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(
                        container.weightRepository,
                        container.recordBackup,
                        container.weightSyncCoordinator,
                    ),
                )
                val state by homeViewModel.uiState.collectAsStateWithLifecycle()

                HomeWithSheets(state = state, viewModel = homeViewModel)
            }
        }
    }
}

@Composable
private fun HomeWithSheets(state: HomeUiState, viewModel: HomeViewModel) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 导入导出走 SAF：不需要存储权限，用户自己决定文件存到哪 / 从哪读
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(RecordCsv.MIME_TYPE),
    ) { uri -> uri?.let(viewModel::exportData) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importData) }

    // Health Connect 的授权走它自己的系统弹窗，不是普通的运行时权限 ——
    // 契约由 HC 客户端提供，返回的是「实际授予了哪些」，允许只授一半。
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted -> viewModel.onPermissionsResult(granted) }

    // 一次性事件 → Snackbar。用 Channel 而不是 StateFlow，旋转屏幕不会重放
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.RecordSaved -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(
                            R.string.snack_record_saved,
                            event.weightKg.format1(),
                        ),
                        actionLabel = context.getString(R.string.action_undo),
                        // 撤销要给足时间，Short 只有 4 秒
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoAdd(event.id)
                    }
                }

                HomeEvent.ProfileSaved -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snack_profile_saved),
                )

                HomeEvent.RecordUpdated -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snack_record_updated),
                )

                is HomeEvent.Exported -> snackbarHostState.showSnackbar(
                    if (event.count == 0) {
                        context.getString(R.string.snack_export_empty)
                    } else {
                        context.getString(R.string.snack_exported, event.count)
                    },
                )

                is HomeEvent.Imported -> snackbarHostState.showSnackbar(
                    if (event.skipped == 0) {
                        context.getString(R.string.snack_imported, event.added)
                    } else {
                        context.getString(R.string.snack_imported_partial, event.added, event.skipped)
                    },
                )

                HomeEvent.DataCleared -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snack_cleared),
                )

                is HomeEvent.DataFailed -> snackbarHostState.showSnackbar(
                    if (event.exporting) {
                        context.getString(R.string.snack_export_failed, event.reason)
                    } else {
                        context.getString(R.string.snack_import_failed, event.reason)
                    },
                )

                is HomeEvent.SyncFinished -> snackbarHostState.showSnackbar(
                    syncMessage(context, event),
                )

                is HomeEvent.SyncUnavailable -> snackbarHostState.showSnackbar(
                    context.getString(
                        when (event.availability) {
                            SyncAvailabilityUi.NOT_INSTALLED -> R.string.snack_sync_not_installed
                            SyncAvailabilityUi.NEEDS_UPDATE -> R.string.snack_sync_needs_update
                            else -> R.string.snack_sync_unsupported
                        }
                    ),
                )

                HomeEvent.SyncPermissionDenied -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snack_sync_denied),
                )

                is HomeEvent.SyncFailed -> snackbarHostState.showSnackbar(
                    context.getString(R.string.snack_sync_failed, event.reason),
                )
            }
        }
    }

    // 设置页按系统返回键应当回到首页，而不是退出 App
    BackHandler(enabled = state.screen == Screen.SETTINGS) { viewModel.closeSettings() }

    // 用 Scaffold 承载悬浮按钮与 Snackbar：它会自动把按钮抬到 Snackbar 之上，
    // 手写 Box 的话两者会在底部叠在一起
    Scaffold(
        // 系统栏避让由 HomeScreen 自己处理（它要按 statusBars / navigationBars 分别留白）
        contentWindowInsets = WindowInsets(0),
        containerColor = WeightDiaryTheme.colors.background,
        snackbarHost = {
            // 「全部记录」弹窗自己渲染 Snackbar（它盖在主窗口之上），此时主窗口这份要让位，
            // 否则同一个 SnackbarHostState 会被两处同时渲染
            if (state.activeSheet != ActiveSheet.ALL_RECORDS) {
                SnackbarHost(snackbarHostState)
            }
        },
        floatingActionButton = {
            // 悬浮按钮是首页的「添加数据」，设置页不该有
            if (state.screen != Screen.HOME) return@Scaffold
            AddRecordFab(
                onClick = { viewModel.openSheet(ActiveSheet.ADD_RECORD) },
                // contentWindowInsets 置零之后 Scaffold 不会自己避让导航栏，
                // 不补这一下按钮会贴着底部手势条
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) {
        // 整屏之间左右滑动，而不是直接替换 —— 有个方向感才知道自己进/退到了哪
        AnimatedContent(
            targetState = state.screen,
            transitionSpec = {
                if (targetState == Screen.SETTINGS) {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it / 4 }
                } else {
                    slideInHorizontally { -it / 4 } togetherWith slideOutHorizontally { it }
                }
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                Screen.HOME -> HomeScreen(
                    state = state,
                    onEditProfile = { viewModel.openSheet(ActiveSheet.EDIT_PROFILE) },
                    onChartTabSelected = viewModel::selectTab,
                    onShiftRange = viewModel::shiftRange,
                    onRecordClick = { viewModel.startEdit(it) },
                    onViewAllRecords = { viewModel.openSheet(ActiveSheet.ALL_RECORDS) },
                    onSettingsClick = viewModel::openSettings,
                    onSyncClick = viewModel::syncNow,
                )

                Screen.SETTINGS -> SettingsScreen(
                    bmiStandard = state.bmiStandard,
                    recordCount = state.recordCount,
                    versionName = BuildConfig.VERSION_NAME,
                    healthConnect = state.healthConnect,
                    experimental = state.experimental,
                    onBack = viewModel::closeSettings,
                    onBmiStandardChange = viewModel::setBmiStandard,
                    onExport = { exportLauncher.launch(defaultBackupFileName()) },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    // 这一行只负责把功能**配好**，不再发起同步 —— 同步的入口是首页那个
                    // 按钮（手动同步开关）与冷启动（自动同步开关）。判定顺序写在
                    // syncRowAction() 里，那边有单测守着
                    onHealthConnectClick = {
                        when (syncRowAction(state.healthConnect)) {
                            // 库里一条记录都没有 → 先引导记一条自己的体重。
                            // 那条记录同时是时间边界和异常过滤的锚点，跳过它就会
                            // 无过滤地吞下一家人的数据（docs/08 §6.2）
                            SyncRowAction.ADD_ANCHOR ->
                                viewModel.openSheet(ActiveSheet.ADD_RECORD)

                            SyncRowAction.REQUEST_PERMISSION ->
                                permissionLauncher.launch(HealthConnectSource.PERMISSIONS)

                            // 没装 / 版本太老：这两行仍然可点，但只解释、不发请求 ——
                            // 应用不在时去请求权限，系统弹窗会以「应用不存在」失败
                            SyncRowAction.EXPLAIN -> viewModel.explainSyncUnavailable()

                            SyncRowAction.NONE, SyncRowAction.UNSUPPORTED -> Unit
                        }
                    },
                    onExperimentalEnabledChange = viewModel::setExperimentalEnabled,
                    onManualSyncChange = viewModel::setManualSyncEnabled,
                    onAutoSyncChange = viewModel::setAutoSyncEnabled,
                    onClearData = viewModel::clearAllData,
                    // 只把发布页交给浏览器 —— 本 App 不联网，所以点下去是「去看有没有新版」，
                    // 不是「替你查」。真没装浏览器（去 Google 化的机器）就给一句提示，
                    // 不要静默失败
                    onViewReleases = {
                        if (!openReleases(context)) {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.snack_no_browser),
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    when (state.activeSheet) {
        ActiveSheet.ADD_RECORD -> {
            val editing = state.editing
            AddRecordSheet(
                // 非空即进入编辑模式：预填原值、标题变「编辑数据」
                initial = editing,
                // 编辑时在保存下方给出显式的删除入口 —— 长按与左滑都没有视觉提示
                onDelete = editing?.let { row -> { viewModel.deleteRecord(row) } },
                onDismiss = viewModel::dismissSheet,
                onSubmit = { weightKg, measuredAt, bodyFatPercent, note ->
                    if (editing != null) {
                        viewModel.updateRecord(
                            id = editing.id,
                            weightKg = weightKg,
                            measuredAt = measuredAt,
                            bodyFatPercent = bodyFatPercent,
                            note = note,
                        )
                    } else {
                        viewModel.addRecord(weightKg, measuredAt, bodyFatPercent, note)
                    }
                },
            )
        }

        ActiveSheet.ALL_RECORDS -> AllRecordsSheet(
            rows = state.allRecords,
            // 编辑时**不换弹窗**，只把内容换成表单 —— 关掉再弹会「收回又弹出」
            editing = state.editing,
            snackbarHostState = snackbarHostState,
            onDismiss = viewModel::dismissSheet,
            onRowClick = { viewModel.startEdit(it, fromAllRecords = true) },
            onRowDelete = viewModel::deleteRecord,
            onEditSave = { weightKg, measuredAt, bodyFatPercent, note ->
                state.editing?.let {
                    viewModel.updateRecord(it.id, weightKg, measuredAt, bodyFatPercent, note)
                }
            },
            onEditCancel = viewModel::cancelEdit,
        )


        ActiveSheet.EDIT_PROFILE -> EditProfileSheet(
            initialHeightCm = state.heightCm,
            initialTargetWeightKg = state.goal.targetWeightKg,
            // 从 BMI 卡点进来（有体重没身高）时说明原因，否则用户不知道为什么 BMI 是 `--`
            hint = if (state.heightCm == null && !state.isEmpty) {
                stringResource(R.string.sheet_onboarding_desc)
            } else {
                null
            },
            onDismiss = viewModel::dismissSheet,
            onSave = { heightCm, targetWeightKg ->
                viewModel.saveProfile(heightCm, targetWeightKg)
            },
        )

        ActiveSheet.NONE -> if (state.showOnboarding) {
            OnboardingSheet(
                onDismiss = viewModel::completeOnboarding,
                onSave = { heightCm ->
                    // 传当前目标原样回去，避免只填身高却把已有目标清掉
                    viewModel.saveProfile(heightCm, state.goal.targetWeightKg)
                },
            )
        }
    }
}

/**
 * 默认的备份文件名：WeightDiary-2026-10-03.csv
 *
 * 刻意用英文 —— 中文名在 Windows 与 Android 之间转手时会踩编码坑
 * （adb 传 UTF-8、PowerShell 按 GBK 解码，文件名会变成乱码）。
 * 文件**内容**仍然是中文表头，方便直接用 Excel 打开。
 */
/**
 * 打开 GitHub 发布页。**联网的是浏览器，不是本 App** —— 这里只发一个 `ACTION_VIEW`
 * 就结束，APK 里依旧没有 `INTERNET`（`tools/verify-no-internet.ps1` 守着这条）。
 *
 * 刻意**不用** `intent.resolveActivity(packageManager)` 预判有没有浏览器：API 30+ 的
 * 包可见性会让它在没有 `<queries>` 声明时返回 `null`，于是「明明装着浏览器却点不动」。
 * 直接 `startActivity` 再兜异常反而是最准的判断。
 *
 * @return 是否真的把 URL 交出去了
 */
private fun openReleases(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.about_releases_url)))
    return runCatching { context.startActivity(intent) }.isSuccess
}

private fun defaultBackupFileName(): String =
    "WeightDiary-" + DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now()) + ".csv"

/**
 * 同步结果的提示文案。
 *
 * 「一条都没进来」有好几种不同的原因，混成一个「已同步 0 条」用户只会以为坏了：
 * - **首次接入**必然有记录被跳过 —— 引导记录的时间就是时间边界，它之前的数据
 *   一律不收（`docs/08` §6.4）。这是有意的，所以要说成 [R.string.snack_sync_start_fresh]。
 *   **但这句只在首次成立**：边界一旦定下就不再变，那些老记录每次同步都会被再跳过一次，
 *   之后的同步再说「从现在开始记录」就是答非所问
 * - 全都**并入**了已有记录 → 要报出并了几条，否则用户以为数据丢了
 * - 真的没有新数据
 */
private fun syncMessage(context: Context, event: HomeEvent.SyncFinished): String = when {
    event.inserted > 0 && event.skipped > 0 ->
        context.getString(R.string.snack_sync_done_skipped, event.inserted, event.skipped)

    event.inserted > 0 && event.claimed > 0 ->
        context.getString(R.string.snack_sync_done_claimed, event.inserted, event.claimed)

    event.inserted > 0 -> context.getString(R.string.snack_sync_done, event.inserted)

    event.claimed > 0 ->
        context.getString(R.string.snack_sync_claimed_only, event.claimed)

    event.skipped > 0 && event.firstSync -> context.getString(R.string.snack_sync_start_fresh)

    else -> context.getString(R.string.snack_sync_nothing)
}