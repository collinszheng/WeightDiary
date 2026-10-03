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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class HomeViewModel(private val repository: WeightRepository) : ViewModel() {

    private val selectedMetric = MutableStateFlow(Metric.WEIGHT)
    private val activeSheet = MutableStateFlow(ActiveSheet.NONE)

    /**
     * 一次性事件用 Channel 而不是 StateFlow：Snackbar 这类事件不该在旋转屏幕后被重放。
     */
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        repository.records,
        repository.profile,
        selectedMetric,
        activeSheet,
    ) { records, profile, metric, sheet ->
        buildState(records, profile, metric, sheet)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun selectMetric(metric: Metric) {
        selectedMetric.value = metric
    }

    // ─────────────── 弹窗 ───────────────

    fun openSheet(sheet: ActiveSheet) {
        activeSheet.value = sheet
    }

    fun dismissSheet() {
        activeSheet.value = ActiveSheet.NONE
    }

    // ─────────────── 录入 ───────────────

    /**
     * 写入一条记录。调用前 UI 已经用 [com.weightdiary.app.domain.validation.RecordValidator]
     * 校验过，这里不做二次校验 —— 校验反馈应当是同步的、就地显示在输入框下方。
     */
    fun addRecord(
        weightKg: Double,
        measuredAt: Instant,
        bodyFatPercent: Double?,
        note: String?,
    ) {
        viewModelScope.launch {
            val id = repository.add(
                weightKg = weightKg,
                measuredAt = measuredAt,
                bodyFatPercent = bodyFatPercent,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            )
            activeSheet.value = ActiveSheet.NONE
            _events.send(HomeEvent.RecordSaved(id, weightKg))
        }
    }

    /** Snackbar 上的「撤销」 */
    fun undoAdd(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    // ─────────────── 个人资料 ───────────────

    fun saveProfile(heightCm: Double?, targetWeightKg: Double?) {
        viewModelScope.launch {
            val snapshot = uiState.value
            val targetChanged = targetWeightKg != snapshot.goal.targetWeightKg

            repository.setHeight(heightCm)
            repository.setTargetWeight(
                targetWeightKg = targetWeightKg,
                setAtWeightKg = when {
                    targetWeightKg == null -> null
                    // 只有目标本身变了才重置进度起点；
                    // 单纯改身高时若跟着重置，用户的历史进度会被抹掉
                    targetChanged -> snapshot.goal.currentWeightKg
                    else -> snapshot.goal.startWeightKg
                },
            )
            repository.setOnboardingCompleted(true)
            activeSheet.value = ActiveSheet.NONE
            _events.send(HomeEvent.ProfileSaved)
        }
    }

    /** 首次引导的「跳过」 */
    fun completeOnboarding() {
        viewModelScope.launch { repository.setOnboardingCompleted(true) }
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
    sheet: ActiveSheet,
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
            startWeightKg = profile.targetSetAtWeightKg,
            progress = goalProgress(profile, latest),
        ),
        level = bmi?.let { BmiClassifier.classify(it, profile.bmiStandard) },
        activeSheet = sheet,
        showOnboarding = !profile.onboardingCompleted,
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
