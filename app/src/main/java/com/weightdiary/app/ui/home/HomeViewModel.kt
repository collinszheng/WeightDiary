package com.weightdiary.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weightdiary.app.data.repository.WeightRepository
import com.weightdiary.app.domain.bmi.BmiCalculator
import com.weightdiary.app.domain.bmi.BmiClassifier
import com.weightdiary.app.domain.chart.Aggregator
import com.weightdiary.app.domain.chart.ChartScaffolder
import com.weightdiary.app.domain.chart.ChartTab
import com.weightdiary.app.domain.chart.RangeResolver
import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.domain.model.UserProfile
import com.weightdiary.app.domain.model.WeightRecord
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.domain.record.RecordRows
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
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(private val repository: WeightRepository) : ViewModel() {

    private val selectedMetric = MutableStateFlow(Metric.WEIGHT)
    private val activeSheet = MutableStateFlow(ActiveSheet.NONE)
    private val chartTab = MutableStateFlow(ChartTab.WEEK)

    /** 图表的基准日期。区间由「Tab + 基准日期」算出来，平移只动这个锚点。 */
    private val chartAnchor = MutableStateFlow(LocalDate.now())

    /** 正在编辑的记录 id。非空时「添加数据」弹窗进入编辑模式。 */
    private val editingId = MutableStateFlow<Long?>(null)

    /** 编辑是从「全部记录」弹窗点进去的。保存/取消后要回到那个弹窗，而不是直接回首页。 */
    private val returnToAllRecords = MutableStateFlow(false)

    /** 一次性事件用 Channel 而不是 StateFlow：Snackbar 这类事件不该在旋转屏幕后被重放。 */
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private data class DataSnapshot(
        val records: List<WeightRecord>,
        val profile: UserProfile,
    )

    /** 纯 UI 的局部状态，打包成一个流，好让 combine 保持在 5 个以内 */
    private data class UiLocal(
        val sheet: ActiveSheet,
        val editingId: Long?,
    )

    private val dataSnapshot = combine(
        repository.records,
        repository.profile,
        ::DataSnapshot,
    )

    private val uiLocal = combine(activeSheet, editingId, ::UiLocal)

    val uiState: StateFlow<HomeUiState> = combine(
        dataSnapshot,
        selectedMetric,
        chartTab,
        chartAnchor,
        uiLocal,
    ) { data, metric, tab, anchor, local ->
        buildState(data.records, data.profile, metric, local.sheet, local.editingId, tab, anchor)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun selectMetric(metric: Metric) {
        val snapshot = uiState.value
        // 「有体重，无身高」时 BMI 根本算不出来。这时点 BMI 卡应当去补身高，
        // 而不是切到一个永远空着的图表（设计规范 §6）。一条记录都没有时不拦，切过去也无妨。
        if (metric == Metric.BMI && snapshot.heightCm == null && !snapshot.isEmpty) {
            openSheet(ActiveSheet.EDIT_PROFILE)
            return
        }
        selectedMetric.value = metric
    }

    // ─────────────── 图表 ───────────────

    fun selectTab(tab: ChartTab) {
        chartTab.value = tab
        // 切 Tab 时把锚点拉回今天。否则从「年」退回「日」，会停在一个很久以前的位置，
        // 用户看到的是空图，还得连点几十次右箭头才能回来。
        chartAnchor.value = LocalDate.now()
    }

    fun shiftRange(steps: Int) {
        val chart = uiState.value.chart
        if (steps > 0 && !chart.canShiftForward) return
        if (steps < 0 && !chart.canShiftBackward) return
        chartAnchor.value = RangeResolver.shiftAnchor(chartTab.value, chartAnchor.value, steps)
    }

    // ─────────────── 弹窗 ───────────────

    fun openSheet(sheet: ActiveSheet) {
        editingId.value = null
        returnToAllRecords.value = false
        activeSheet.value = sheet
    }

    /**
     * 点击某条记录 → 复用「添加数据」弹窗，但进入编辑模式。
     *
     * @param fromAllRecords 从「全部记录」弹窗点进来的。保存/取消后回到那个弹窗
     */
    fun startEdit(row: RecordRow, fromAllRecords: Boolean = false) {
        editingId.value = row.id
        returnToAllRecords.value = fromAllRecords
        activeSheet.value = ActiveSheet.ADD_RECORD
    }

    fun dismissSheet() = closeSheet()

    private fun closeSheet() {
        editingId.value = null
        val backToList = returnToAllRecords.value
        returnToAllRecords.value = false
        activeSheet.value = if (backToList) ActiveSheet.ALL_RECORDS else ActiveSheet.NONE
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

    /** 保存编辑。校验由弹窗就地完成，这里直接落库。 */
    fun updateRecord(
        id: Long,
        weightKg: Double,
        measuredAt: Instant,
        bodyFatPercent: Double?,
        note: String?,
    ) {
        viewModelScope.launch {
            repository.updateFields(
                id = id,
                weightKg = weightKg,
                measuredAt = measuredAt,
                bodyFatPercent = bodyFatPercent,
                note = note?.trim()?.takeIf { it.isNotEmpty() },
            )
            closeSheet()
            _events.send(HomeEvent.RecordUpdated)
        }
    }

    // ─────────────── 删除 ───────────────

    /**
     * 删除一条记录。
     *
     * 这是**不可撤销**的 —— 防误删由「左滑露出按钮 + 点按钮二次确认」承担，
     * 因此不再有「已删除」的撤销 Snackbar。
     */
    fun deleteRecord(row: RecordRow) {
        viewModelScope.launch {
            repository.delete(row.id)
            // 从编辑弹窗里删的，删完要把弹窗关掉；从列表里删的则留在列表继续删
            if (activeSheet.value == ActiveSheet.ADD_RECORD) closeSheet()
        }
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
    editingId: Long?,
    tab: ChartTab,
    anchor: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate = LocalDate.now(zone),
): HomeUiState {
    // repository.records 已按 measuredAt 倒序（Dao 的 ORDER BY），所以末条就是最早那条
    val latest = records.firstOrNull()
    val previous = records.getOrNull(1)
    val earliestDate = records.lastOrNull()?.measuredAt?.atZone(zone)?.toLocalDate()

    val height = profile.heightCm?.takeIf { it > 0.0 }
    val bmi = if (latest != null && height != null) {
        BmiCalculator.calculate(latest.weightKg, height)
    } else {
        null
    }

    // 列表行与图表都要遍历全部记录。1000 条量级下这点开销可以忽略，
    // 换来的是列表与图表天然同步 —— 不用维护第二份状态，也就不会不同步。
    val rows = RecordRows.build(records)

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
        chart = buildChart(records, profile, metric, tab, anchor, earliestDate, height, zone, today),
        history = rows.take(HOME_HISTORY_LIMIT),
        allRecords = rows,
        editing = editingId?.let { id -> rows.firstOrNull { it.id == id } },
    )
}

private fun buildChart(
    records: List<WeightRecord>,
    profile: UserProfile,
    metric: Metric,
    tab: ChartTab,
    anchor: LocalDate,
    earliestDate: LocalDate?,
    heightCm: Double?,
    zone: ZoneId,
    today: LocalDate,
): ChartUi {
    val range = RangeResolver.resolve(tab, anchor, earliestDate, zone)
    val granularity = RangeResolver.granularityOf(tab, range)
    val points = Aggregator.aggregate(records, range, granularity, metric, heightCm, zone)
    val values = points.map { it.value }

    // 目标线只对体重有意义 —— BMI / 体脂率没有「目标值」这个概念
    val rawTarget = profile.targetWeightKg
        ?.takeIf { it > 0.0 && metric == Metric.WEIGHT }

    // 决策 B8：目标离数据太远就不撑开 Y 轴，否则折线会被压成一条平线，趋势全看不出来
    val includeTarget = rawTarget != null && ChartScaffolder.shouldIncludeTarget(values, rawTarget)
    val goalLine = rawTarget?.takeIf { includeTarget }
    val goalOffscreen = when {
        rawTarget == null || includeTarget -> GoalOffscreen.NONE
        values.isEmpty() -> GoalOffscreen.NONE
        rawTarget < values.min() -> GoalOffscreen.BELOW
        else -> GoalOffscreen.ABOVE
    }

    return ChartUi(
        tab = tab,
        granularity = granularity,
        start = range.start,
        end = range.end,
        points = points,
        yAxis = if (points.isEmpty()) null else ChartScaffolder.buildYAxis(values, goalLine),
        xLabels = ChartScaffolder.xLabels(tab, range, zone),
        goalLine = goalLine,
        goalOffscreen = goalOffscreen,
        canShiftForward = RangeResolver.canShiftForward(tab, anchor, today, earliestDate, zone),
        canShiftBackward = RangeResolver.canShiftBackward(tab, anchor, earliestDate, zone),
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
