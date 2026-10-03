package com.weightdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.weightdiary.app.ui.home.HomeScreen
import com.weightdiary.app.ui.home.HomeViewModel
import com.weightdiary.app.ui.theme.WeightDiaryTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // targetSdk 35+ 在 Android 15 上强制边到边。不调这个的话窗口虽然铺满全屏，
        // 但系统栏图标会用默认（浅色）绘制，在白底上等于不可见 —— 状态栏像是消失了。
        // 内容侧的 insets 避让在 HomeScreen 里处理（背景仍延伸到状态栏下，符合决策 C9）。
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

                HomeScreen(
                    state = state,
                    onMetricClick = homeViewModel::selectMetric,
                    // M2 接入「添加数据」底部弹窗
                    onAddRecord = {},
                    // M2 接入「编辑个人资料」底部弹窗
                    onEditProfile = {},
                )
            }
        }
    }
}
