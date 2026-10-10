package com.weightdiary.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.weightdiary.app.data.healthconnect.HealthConnectAvailability
import com.weightdiary.app.data.backup.RecordBackup
import com.weightdiary.app.data.repository.WeightRepository
import com.weightdiary.app.data.sync.SyncOutcome
import com.weightdiary.app.data.sync.WeightSyncCoordinator
import com.weightdiary.app.domain.bmi.BmiCalculator
import com.weightdiary.app.domain.bmi.BmiClassifier
import com.weightdiary.app.domain.chart.Aggregator
import com.weightdiary.app.domain.chart.ChartScaffolder
import com.weightdiary.app.domain.chart.ChartTab
import com.weightdiary.app.domain.chart.RangeResolver
import com.weightdiary.app.domain.chart.ReferenceLines
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.model.RecordSource
import com.weightdiary.app.domain.model.UserProfile
import com.weightdiary.app.domain.model.WeightRecord
import com.weightdiary.app.domain.record.RecordRow
import com.weightdiary.app.domain.record.RecordRows
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(
    private val repository: WeightRepository,
    private val backup: RecordBackup,
    private val syncCoordinator: WeightSyncCoordinator,
) : ViewModel() {

    private val activeSheet = MutableStateFlow(ActiveSheet.NONE)
    private val chartTab = MutableStateFlow(ChartTab.WEEK)

    /** 图表的基准日期。区间由「Tab + 基准日期」算出来，平移只动这个锚点。 */
    private val chartAnchor = MutableStateFlow(LocalDate.now())

    /** 正在编辑的记录 id。非空时「添加数据」弹窗进入编辑模式。 */
    private val editingId = MutableStateFlow<Long?>(null)

    /** 整屏导航。设置是整屏，不是弹窗 */
    private val screen = MutableStateFlow(Screen.HOME)

    /**
     * 同步入口的状态。SDK 的可用性查询不是 Flow（要问系统装没装），
     * 所以在 init 和每次同步后各刷一次，不轮询。
     */
    private val syncAvailability = MutableStateFlow(SyncAvailabilityUi.UNSUPPORTED)
    private val syncGranted = MutableStateFlow(false)
    private val syncing = MutableStateFlow(false)

    /** 一次性事件用 Channel 而不是 StateFlow：Snackbar 这类事件不该在旋转屏幕后被重放。 */
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    private data class DataSnapshot(
        val records: List<WeightRecord>,
        val profile: UserProfile,
        val lastSyncAt: Instant?,
    )

    /** 纯 UI 的局部状态，打包成一个流，好让 combine 保持在 5 个以内 */
    private data class UiLocal(
        val sheet: ActiveSheet,
        val editingId: Long?,
        val screen: Screen,
        val sync: SyncLocal,
    )

    private val dataSnapshot = combine(
        repository.records,
        repository.profile,
        repository.lastSyncAt,
        ::DataSnapshot,
    )

    private val syncLocal = combine(
        syncAvailability,
        syncGranted,
        syncing,
        ::SyncLocal,
    )

    private val uiLocal = combine(activeSheet, editingId, screen, syncLocal, ::UiLocal)

    /**
     * 实验功能的三个开关先自己合成一个流。
     *
     * 不是为了好看：`combine` 最多接 5 个流，这里已经有 dataSnapshot / chartTab /
     * chartAnchor / uiLocal 四个，直接铺开会超。
     */
    private val experimentalUi: Flow<ExperimentalUi> = combine(
        repository.experimentalEnabled,
        repository.manualSyncEnabled,
        repository.autoSyncEnabled,
        ::ExperimentalUi,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        dataSnapshot,
        chartTab,
        chartAnchor,
        uiLocal,
        experimentalUi,
    ) { data, tab, anchor, local, experimental ->
        buildState(
            records = data.records,
            profile = data.profile,
            sheet = local.sheet,
            editingId = local.editingId,
            screen = local.screen,
            tab = tab,
            anchor = anchor,
            sync = local.sync,
            lastSyncAt = data.lastSyncAt,
            experimental = experimental,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            refreshSyncStatus()
            autoSyncOnLaunch()
        }
    }

    /**
     * 冷启动自动同步**一次**（用户明确要求：只在打开软件时同步，运行期间来的新数据
     * 不自动同步，要么手动点、要么下次启动再说）。
     *
     * 放在 ViewModel 的 init 里而不是 Activity 的 LaunchedEffect：init 跟着 ViewModel
     * 实例走，旋转屏幕、切到设置再回来都不会重放；LaunchedEffect 会重放。
     *
     * **刻意不读 `uiState` 判断授权** —— DataStore 和系统查询都是异步的，`uiState`
     * 首帧里 granted 还是默认的 false，照着它判断自动同步永远不会发生。所以先 await
     * [refreshSyncStatus]（上面那行），再读这两个 MutableStateFlow 的**当前值**。
     *
     * 前提不满足时**一律静默**：启动第一屏弹系统授权框、或弹「先记一条体重」的表单
     * 都很烦人。不报错、不提示，用户想同步时手动点一下。
     */
    private suspend fun autoSyncOnLaunch() {
        val autoActive = repository.experimentalEnabled.first() &&
            repository.autoSyncEnabled.first()
        // 库里一条记录都没有时，时间边界与异常过滤都没有锚点，不能同步（docs/08 §6.2）
        val hasAnchor = repository.records.first().isNotEmpty()
        val shouldSync = canAutoSyncOnLaunch(
            autoActive = autoActive,
            availability = syncAvailability.value,
            granted = syncGranted.value,
            hasAnchor = hasAnchor,
        )
        if (shouldSync) syncNow()
    }

    // ─────────────── 体脂秤同步 ───────────────

    private suspend fun refreshSyncStatus() {
        val availability = syncCoordinator.availability().toUi()
        syncAvailability.value = availability
        syncGranted.value = availability == SyncAvailabilityUi.AVAILABLE &&
            syncCoordinator.hasPermissions()
    }

    /** 用户在系统弹窗里授权（或拒绝）之后回调。授权成了就立刻同步一次 */
    fun onPermissionsResult(granted: Set<String>) {
        val ok = syncCoordinator.permissionsSatisfied(granted)
        syncGranted.value = ok
        if (ok) {
            syncNow()
        } else {
            // 拒绝（或只授了一半）也必须给一句反馈。否则表现就是
            // 「点了那一行，什么都没发生」—— 和 rationale Activity 那个坑长得一模一样
            viewModelScope.launch { _events.send(HomeEvent.SyncPermissionDenied) }
        }
    }

    fun syncNow() {
        if (syncing.value) return
        viewModelScope.launch {
            syncing.value = true
            // 首次成功同步之前水位线是空的。这个判断要在同步**之前**做：
            // 首次接入必然把早于时间边界的记录全跳过，那时该说「从现在开始记录」，
            // 而之后同样的 skipped 只意味着「没有新数据」，再说那句就是答非所问
            val firstSync = repository.lastSyncAt.first() == null

            val startedAt = System.nanoTime()
            val outcome = syncCoordinator.sync()
            val spentMs = (System.nanoTime() - startedAt) / 1_000_000
            // 补足到整数圈：同步常常只要几十毫秒，短于一圈就会「闪一下」看不见；
            // 而补到半圈收尾又会让图标在转的中途被拽回原位。见 syncFeedbackMs
            val targetMs = syncFeedbackMs(spentMs)
            if (spentMs < targetMs) delay(targetMs - spentMs)
            syncing.value = false

            when (outcome) {
                is SyncOutcome.Success -> {
                    syncGranted.value = true
                    _events.send(
                        HomeEvent.SyncFinished(
                            inserted = outcome.inserted,
                            claimed = outcome.claimed,
                            skipped = outcome.skippedCount,
                            firstSync = firstSync,
                        )
                    )
                }

                is SyncOutcome.Unavailable -> {
                    val ui = outcome.availability.toUi()
                    syncAvailability.value = ui
                    _events.send(HomeEvent.SyncUnavailable(ui))
                }

                SyncOutcome.PermissionDenied -> {
                    syncGranted.value = false
                    _events.send(HomeEvent.SyncPermissionDenied)
                }

                is SyncOutcome.Failed -> _events.send(HomeEvent.SyncFailed(outcome.reason))
            }
        }
    }

    /**
     * 体脂秤那一行在「本机没装 / 版本太老」时被点：**只说清是哪一种情况**，不发请求。
     *
     * 这两种情况下都不该去请求权限：应用不在，系统弹窗会以「应用不存在」这类
     * 看不懂的方式失败，用户只会以为 App 坏了。
     */
    fun explainSyncUnavailable() {
        val availability = syncAvailability.value
        if (availability == SyncAvailabilityUi.AVAILABLE) return
        viewModelScope.launch { _events.send(HomeEvent.SyncUnavailable(availability)) }
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
        activeSheet.value = sheet
    }

    /**
     * 点击某条记录 → 进入编辑模式。
     *
     * @param fromAllRecords 从「全部记录」弹窗点进来的。此时**保持 ALL_RECORDS 不变**，
     *   只把弹窗内容切成编辑表单 —— 关掉再弹一个会让用户看到「收回 → 弹出」，很乱
     */
    fun startEdit(row: RecordRow, fromAllRecords: Boolean = false) {
        editingId.value = row.id
        activeSheet.value = if (fromAllRecords) ActiveSheet.ALL_RECORDS else ActiveSheet.ADD_RECORD
    }

    fun dismissSheet() {
        editingId.value = null
        activeSheet.value = ActiveSheet.NONE
    }

    /** 编辑表单里的「‹ 全部记录」：从列表点进来的退回列表，从首页点进来的直接关掉 */
    fun cancelEdit() = finishEditing()

    /** 编辑结束（保存、删除、返回都走这里）：退回列表或关掉弹窗 */
    private fun finishEditing() {
        editingId.value = null
        if (activeSheet.value != ActiveSheet.ALL_RECORDS) {
            activeSheet.value = ActiveSheet.NONE
        }
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
            finishEditing()
            _events.send(HomeEvent.RecordUpdated)
        }
    }

    // ─────────────── 设置 ───────────────

    fun openSettings() {
        activeSheet.value = ActiveSheet.NONE
        screen.value = Screen.SETTINGS
        // 用户可能刚从系统设置里改了授权，回来看一眼状态
        viewModelScope.launch { refreshSyncStatus() }
    }

    /** 进设置主页里的某一页。传整屏枚举而不是写四个函数：接线处一眼看得出去了哪一页 */
    fun openSettingsPage(page: Screen) {
        screen.value = page
    }

    /** 返回上一级。规则在 [backTarget] 里，这里只管照着走 */
    fun goBack() {
        screen.value = backTarget(screen.value)
    }

    fun setBmiStandard(standard: BmiStandard) {
        viewModelScope.launch { repository.setBmiStandard(standard) }
    }

    // ─────────────── 实验功能开关 ───────────────

    fun setExperimentalEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setExperimentalEnabled(enabled) }
    }

    fun setManualSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setManualSyncEnabled(enabled) }
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoSyncEnabled(enabled) }
    }

    fun exportData(uri: android.net.Uri) {
        viewModelScope.launch {
            val records = repository.snapshot()
            runCatching { backup.write(uri, records) }
                .onSuccess { _events.send(HomeEvent.Exported(it)) }
                .onFailure { _events.send(HomeEvent.DataFailed(exporting = true, reason = it.shortReason())) }
        }
    }

    fun importData(uri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val decoded = backup.read(uri)
                val added = repository.importRows(decoded.rows)
                added to decoded.skipped
            }
                .onSuccess { (added, skipped) -> _events.send(HomeEvent.Imported(added, skipped)) }
                .onFailure { _events.send(HomeEvent.DataFailed(exporting = false, reason = it.shortReason())) }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            // 清空连带把同步水位线也清了，状态行要跟着回到「从未同步」
            _events.send(HomeEvent.DataCleared)
        }
    }

    // ─────────────── 删除 ───────────────

    /**
     * 删除一条记录。
     *
     * 这是**不可撤销**的 —— 防误删由「左滑露出按钮 + 点按钮二次确认」承担，
     * 因此不再有「已删除」的撤销 Snackbar。
     *
     * 同步来的记录被删时，仓库会同时写下墓碑，否则下次同步它会复活。
     */
    fun deleteRecord(row: RecordRow) {
        viewModelScope.launch {
            repository.delete(row.id)
            // 从编辑表单里删的，删完退回列表；从列表里左滑删的则什么都不用做
            finishEditing()
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
        fun factory(
            repository: WeightRepository,
            backup: RecordBackup,
            syncCoordinator: WeightSyncCoordinator,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(repository, backup, syncCoordinator) as T
        }
    }
}

/** data 层的可用性 → UI 层的镜像。Composable 不认识 SDK 的类型 */
private fun HealthConnectAvailability.toUi(): SyncAvailabilityUi = when (this) {
    HealthConnectAvailability.AVAILABLE -> SyncAvailabilityUi.AVAILABLE
    HealthConnectAvailability.NEEDS_UPDATE -> SyncAvailabilityUi.NEEDS_UPDATE
    HealthConnectAvailability.NOT_INSTALLED -> SyncAvailabilityUi.NOT_INSTALLED
    HealthConnectAvailability.UNSUPPORTED -> SyncAvailabilityUi.UNSUPPORTED
}

/**
 * UI 层的同步局部状态。
 *
 * 放在文件级而不是 ViewModel 内部：`buildState` 是顶层私有函数，够不着嵌套类。
 */
private data class SyncLocal(
    val availability: SyncAvailabilityUi,
    val granted: Boolean,
    val syncing: Boolean,
)

private fun buildState(
    records: List<WeightRecord>,
    profile: UserProfile,
    sheet: ActiveSheet,
    editingId: Long?,
    screen: Screen,
    tab: ChartTab,
    anchor: LocalDate,
    sync: SyncLocal,
    lastSyncAt: Instant?,
    experimental: ExperimentalUi,
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

    // 体脂单独找「最近一次填写的」。记录已按 measuredAt 倒序，所以 firstOrNull 就是最近那次
    val latestBodyFat = records.firstOrNull { it.bodyFatPercent != null }?.bodyFatPercent

    return HomeUiState(
        isLoading = false,
        isEmpty = records.isEmpty(),
        recordCount = records.size,
        weightKg = latest?.weightKg,
        bmi = bmi,
        latestBodyFatPercent = latestBodyFat,
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
        bmiStandard = profile.bmiStandard,
        screen = screen,
        chart = buildChart(records, profile, tab, anchor, earliestDate, height, zone, today),
        history = rows.take(HOME_HISTORY_LIMIT),
        allRecords = rows,
        editing = editingId?.let { id -> rows.firstOrNull { it.id == id } },
        healthConnect = HealthConnectUi(
            availability = sync.availability,
            granted = sync.granted,
            syncing = sync.syncing,
            lastSyncAt = lastSyncAt,
            syncedCount = records.count { it.source == RecordSource.HEALTH_CONNECT },
            needsAnchor = records.isEmpty(),
        ),
        experimental = experimental,
    )
}

private fun buildChart(
    records: List<WeightRecord>,
    profile: UserProfile,
    tab: ChartTab,
    anchor: LocalDate,
    earliestDate: LocalDate?,
    heightCm: Double?,
    zone: ZoneId,
    today: LocalDate,
): ChartUi {
    val range = RangeResolver.resolve(tab, anchor, earliestDate, zone)
    val granularity = RangeResolver.granularityOf(tab, range)
    val points = Aggregator.aggregate(records, range, granularity, zone)
    val values = points.map { it.value }

    val base = ChartUi(
        tab = tab,
        granularity = granularity,
        start = range.start,
        end = range.end,
        points = points,
        xLabels = ChartScaffolder.xLabels(tab, range, zone),
        canShiftForward = RangeResolver.canShiftForward(tab, anchor, today, earliestDate, zone),
        canShiftBackward = RangeResolver.canShiftBackward(tab, anchor, earliestDate, zone),
    )

    if (points.isEmpty()) return base

    val target = profile.targetWeightKg?.takeIf { it > 0.0 }

    // ── 目标线：**无条件纳入坐标轴** ──
    //
    // 产品要求目标线必须看得见，`ChartScaffolder` 也是按这个实现的（那边有单测
    // 「目标线无条件纳入范围 - 哪怕离数据很远」守着）。代价是目标离数据很远时
    // 折线会被压扁 —— 这是刻意用「看得见目标」换「看趋势」。
    //
    // ⚠️ 这里曾经还有个 targetFits 例外（「纳入目标会不会把主步长顶大」才画），
    // 那是决策 B8「30% 撑开限制」的遗留、早已作废。它不但把废止的例外偷偷加了回来，
    // 还让角标在自相矛盾的状态下弹出：真机上目标 65 落在窗口 [64, 70] 内，
    // 线却因 targetFits=false 不画，角标又按 `65 < 64` 判成「在上方」，
    // 于是显示「▲ 还需 1.0 kg」—— 而用户实际要**减** 1.0 kg。
    // 见 docs/09-真机实测记录.md §6.6
    val axis = ChartScaffolder.buildYAxis(values, target)

    // 画线还是出角标，以目标在不在**最终窗口**里为准，与 BMI 阈值线共用同一个判据。
    // 目标无条件纳入后，正常情况必然可见；只有极端跨度触发步长兜底（窗口撑不下）时
    // 才轮得到角标，那时它才真的在报「线在图外」。
    val targetVisible = target != null && axis.showsReferenceLine(target)

    return base.copy(
        yAxis = axis,
        goalLine = if (targetVisible) target else null,
        goalOffscreen = if (target != null && !targetVisible) {
            GoalOffscreen(
                targetKg = target,
                // 拿最后一个绘图点比，而不是最新那条记录 —— 图上看到的是前者
                gapKg = points.last().value - target,
                // 走到这里目标必在窗口之外，所以 <= lower 就是「在下方」
                below = target <= axis.lower,
            )
        } else {
            null
        },
        referenceLines = ReferenceLines.visibleIn(axis, heightCm, profile.bmiStandard),
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

/** 异常信息可能很长（带类名），Snackbar 里只留一句能看懂的 */
private fun Throwable.shortReason(): String =
    (message ?: this::class.java.simpleName).take(60)
