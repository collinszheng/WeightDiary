package com.weightdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weightdiary.app.ui.common.format1
import com.weightdiary.app.ui.home.ActiveSheet
import com.weightdiary.app.ui.home.HomeEvent
import com.weightdiary.app.ui.home.HomeScreen
import com.weightdiary.app.ui.home.HomeUiState
import com.weightdiary.app.ui.home.HomeViewModel
import com.weightdiary.app.ui.sheet.AddRecordSheet
import com.weightdiary.app.ui.sheet.AllRecordsSheet
import com.weightdiary.app.ui.sheet.EditProfileSheet
import com.weightdiary.app.ui.sheet.OnboardingSheet
import com.weightdiary.app.ui.theme.WeightDiaryTheme

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
                    factory = HomeViewModel.factory(container.weightRepository),
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

                is HomeEvent.RecordDeleted -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snack_record_deleted),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Long,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undoDelete(event.record)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeScreen(
            state = state,
            onMetricClick = viewModel::selectMetric,
            onAddRecord = { viewModel.openSheet(ActiveSheet.ADD_RECORD) },
            onEditProfile = { viewModel.openSheet(ActiveSheet.EDIT_PROFILE) },
            onChartTabSelected = viewModel::selectTab,
            onShiftRange = viewModel::shiftRange,
            onRecordClick = { viewModel.startEdit(it) },
            onRecordLongClick = viewModel::deleteRecord,
            onViewAllRecords = { viewModel.openSheet(ActiveSheet.ALL_RECORDS) },
        )

        // 「全部记录」弹窗自己渲染 Snackbar（它盖在主窗口之上），此时主窗口这份要让位，
        // 否则同一个 SnackbarHostState 会被两处同时渲染
        if (state.activeSheet != ActiveSheet.ALL_RECORDS) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }

    when (state.activeSheet) {
        ActiveSheet.ADD_RECORD -> AddRecordSheet(
            // 非空即进入编辑模式：预填原值、标题变「编辑数据」
            initial = state.editing,
            onDismiss = viewModel::dismissSheet,
            onSubmit = { weightKg, measuredAt, bodyFatPercent, note ->
                val editing = state.editing
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

        ActiveSheet.ALL_RECORDS -> AllRecordsSheet(
            rows = state.allRecords,
            snackbarHostState = snackbarHostState,
            onDismiss = viewModel::dismissSheet,
            onRowClick = { viewModel.startEdit(it, fromAllRecords = true) },
            onRowLongClick = viewModel::deleteRecord,
        )

        ActiveSheet.EDIT_PROFILE -> EditProfileSheet(
            initialHeightCm = state.heightCm,
            initialTargetWeightKg = state.goal.targetWeightKg,
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
