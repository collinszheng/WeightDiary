package com.weightdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.home.ActiveSheet
import com.weightdiary.app.ui.home.HomeEvent
import com.weightdiary.app.ui.home.HomeScreen
import com.weightdiary.app.ui.home.HomeUiState
import com.weightdiary.app.domain.record.RecordCsv
import com.weightdiary.app.ui.home.HomeViewModel
import com.weightdiary.app.ui.home.components.AddRecordFab
import com.weightdiary.app.ui.sheet.AddRecordSheet
import com.weightdiary.app.ui.sheet.AllRecordsSheet
import com.weightdiary.app.ui.sheet.EditProfileSheet
import com.weightdiary.app.ui.sheet.OnboardingSheet
import com.weightdiary.app.ui.sheet.SettingsSheet
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
            }
        }
    }

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
            AddRecordFab(
                onClick = { viewModel.openSheet(ActiveSheet.ADD_RECORD) },
                // contentWindowInsets 置零之后 Scaffold 不会自己避让导航栏，
                // 不补这一下按钮会贴着底部手势条
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) {
        HomeScreen(
            state = state,
            onMetricClick = viewModel::selectMetric,
            onEditProfile = { viewModel.openSheet(ActiveSheet.EDIT_PROFILE) },
            onChartTabSelected = viewModel::selectTab,
            onShiftRange = viewModel::shiftRange,
            onRecordClick = { viewModel.startEdit(it) },
            onViewAllRecords = { viewModel.openSheet(ActiveSheet.ALL_RECORDS) },
            onSettingsClick = { viewModel.openSheet(ActiveSheet.SETTINGS) },
        )
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

        ActiveSheet.SETTINGS -> SettingsSheet(
            bmiStandard = state.bmiStandard,
            recordCount = state.recordCount,
            versionName = BuildConfig.VERSION_NAME,
            onBmiStandardChange = viewModel::setBmiStandard,
            onExport = { exportLauncher.launch(defaultBackupFileName()) },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onClearData = viewModel::clearAllData,
            onDismiss = viewModel::dismissSheet,
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

/** 默认的备份文件名：体重日记-2026-10-03.csv */
private fun defaultBackupFileName(): String =
    "体重日记-" + DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now()) + ".csv"