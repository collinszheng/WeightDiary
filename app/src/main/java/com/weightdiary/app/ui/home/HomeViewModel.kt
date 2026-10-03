package com.weightdiary.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weightdiary.app.data.repository.WeightRepository
import com.weightdiary.app.domain.bmi.BmiCalculator
import com.weightdiary.app.domain.bmi.BmiClassifier
import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.domain.model.UserProfile
import com.weightdiary.app.domain.model.WeightRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: WeightRepository) : ViewModel() {

    private val selectedMetric = MutableStateFlow(Metric.WEIGHT)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.records,
        repository.profile,
        selectedMetric,
    ) { records, profile, metric ->
        buildState(records, profile, metric)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun selectMetric(metric: Metric) {
        selectedMetric.value = metric
    }

    /** 目前只有调试种子会用，M2 接入「编辑个人资料」弹窗后由 UI 调用 */
    fun setHeight(heightCm: Double) {
        viewModelScope.launch { repository.setHeight(heightCm) }
    }

    companion object {
        fun factory(repository: WeightRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(repository) as T
            }
    }
}

private fun buildState(
    records: List<WeightRecord>,
    profile: UserProfile,
    metric: Metric,
): HomeUiState {
    // repository.records 已按 measuredAt 倒序（Dao 的 ORDER BY）
    val latest = records.firstOrNull()
    val previous = records.getOrNull(1)

    val height = profile.heightCm?.takeIf { it > 0.0 }
    val bmi = if (latest != null && height != null) {
        BmiCalculator.calculate(latest.weightKg, height)
    } else {
        null
    }

    return HomeUiState(
        isLoading = false,
        isEmpty = records.isEmpty(),
        recordCount = records.size,
        selectedMetric = metric,
        weightKg = latest?.weightKg,
        bmi = bmi,
        bodyFatPercent = latest?.bodyFatPercent,
        heightCm = height,
        latestMeasuredAt = latest?.measuredAt,
        deltaKg = if (latest != null && previous != null) {
            latest.weightKg - previous.weightKg
        } else {
            null
        },
        goal = GoalUi(
            currentWeightKg = latest?.weightKg,
            targetWeightKg = profile.targetWeightKg?.takeIf { it > 0.0 },
            progress = goalProgress(profile, latest),
        ),
        level = bmi?.let { BmiClassifier.classify(it, profile.bmiStandard) },
    )
}

/**
 * 目标进度 = (起点体重 − 当前体重) / (起点体重 − 目标体重)
 *
 * 起点取**设置目标时的体重**（决策 A6）。缺起点、缺目标或跨度为 0 时返回 null，
 * 此时 UI 不画进度条。
 */
private fun goalProgress(profile: UserProfile, latest: WeightRecord?): Float? {
    val start = profile.targetSetAtWeightKg ?: return null
    val target = profile.targetWeightKg ?: return null
    val current = latest?.weightKg ?: return null
    val span = start - target
    if (span <= 0.0) return null
    return ((start - current) / span).coerceIn(0.0, 1.0).toFloat()
}
