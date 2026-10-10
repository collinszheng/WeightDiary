package com.weightdiary.app.ui.home

import com.weightdiary.app.domain.chart.ChartPoint
import com.weightdiary.app.domain.chart.ChartTab
import com.weightdiary.app.domain.chart.Granularity
import com.weightdiary.app.domain.chart.ReferenceLine
import com.weightdiary.app.domain.chart.XAxisLabels
import com.weightdiary.app.domain.chart.XLabelKind
import com.weightdiary.app.domain.chart.YAxis
import com.weightdiary.app.domain.model.BmiLevel
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.record.RecordRow
import java.time.Instant

/** 当前打开的底部弹窗。放在 UiState 里而不是 Compose 局部状态，旋转屏幕后不会丢。 */
enum class ActiveSheet {
    NONE,
    ADD_RECORD,
    EDIT_PROFILE,
    ALL_RECORDS,
}

/**
 * 首页 UI 状态。
 *
 * 刻意**只放原始数据，不放显示文案** —— ViewModel 没有 Context，
 * 文案一律由 Composable 用 `stringResource` 组装（决策 C5：字符串走 strings.xml）。
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    /** 无任何记录时为 true，用于空状态 */
    val isEmpty: Boolean = true,
    val recordCount: Int = 0,

    // ── 目标卡要用的值（「最新一条」，与图表的「当日最低值」有意不同，见决策记录 冲突 2）──
    val weightKg: Double? = null,
    val bmi: Double? = null,

    /**
     * **最近一次填写的**体脂率，不是最近一条记录的那条。
     *
     * 体脂的测量频率远低于体重（可能几周才填一次）。跟着最新记录走的话，
     * 只要最新那条没填体脂，这个位置就空了 —— 而用户明明上个月填过。
     */
    val latestBodyFatPercent: Double? = null,
    val heightCm: Double? = null,
    val latestMeasuredAt: Instant? = null,
    /** 与上一条记录的差值。正数为增、负数为减；只有一条记录时为 null */
    val deltaKg: Double? = null,

    val goal: GoalUi = GoalUi(),
    /** 无身高时为 null，UI 显示 `--` */
    val level: BmiLevel? = null,

    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    /** 首次启动的身高引导。填写或跳过之后都不再出现 */
    val showOnboarding: Boolean = false,

    val chart: ChartUi = ChartUi(),

    /** 首页历史列表。只放最近几条，全部记录在 [allRecords] */
    val history: List<RecordRow> = emptyList(),
    /** 「全部记录」弹窗用的全量列表。一次性算好，不引入 Paging（设计规范 §5.2） */
    val allRecords: List<RecordRow> = emptyList(),
    /** 正在编辑的记录。非空时 [ActiveSheet.ADD_RECORD] 进入编辑模式 */
    val editing: RecordRow? = null,

    /** 「超重」的门槛由它决定：中国 24.0 / WHO 25.0 */
    val bmiStandard: BmiStandard = BmiStandard.CHINA,

    /** 当前整屏。设置是**整屏**而不是弹窗 —— 入口在右上角，从下往上弹不呼应 */
    val screen: Screen = Screen.HOME,

    /** 设置页「实验功能」里体脂秤那一行的状态（`docs/08` §6） */
    val healthConnect: HealthConnectUi = HealthConnectUi(),

    /** 设置页「实验功能」的总开关与两种同步方式 */
    val experimental: ExperimentalUi = ExperimentalUi(),
) {
    /** 首页历史区默认展示条数（设计规范 §4.5：默认展示最近 2–3 条） */
    val historyLimit: Int get() = HOME_HISTORY_LIMIT

    val hasMoreRecords: Boolean get() = allRecords.size > history.size
}

/** 整屏页面。弹窗仍由 [ActiveSheet] 表达 */
enum class Screen {
    HOME,
    SETTINGS,
}

const val HOME_HISTORY_LIMIT = 3

/**
 * 图表区的状态。
 *
 * 时间用 `Instant` 而不是格式化好的字符串 —— 文案由 Composable 查 strings.xml 组（决策 C5）。
 */
data class ChartUi(
    val tab: ChartTab = ChartTab.WEEK,
    val granularity: Granularity = Granularity.DAILY,
    /** 可视区间。null 表示还没算出来（首帧） */
    val start: Instant? = null,
    val end: Instant? = null,
    val points: List<ChartPoint> = emptyList(),
    val yAxis: YAxis? = null,
    /** X 轴标签。位置与语义类别都由按 Tab 分类的规则给出，不是统一的「5 个等分」 */
    val xLabels: XAxisLabels = XAxisLabels(emptyList(), XLabelKind.DATE),

    /** 目标线落在窗口内时画虚线；落在窗口外时为 null，改由 [goalOffscreen] 提示 */
    val goalLine: Double? = null,

    /** 目标体重落在窗口外时的角标提示 */
    val goalOffscreen: GoalOffscreen? = null,

    /** BMI 分级阈值线。**只含落进当前窗口的那些**，视野外的一律不画 */
    val referenceLines: List<ReferenceLine> = emptyList(),

    val canShiftForward: Boolean = false,
    val canShiftBackward: Boolean = false,
) {
    val hasData: Boolean get() = points.isNotEmpty()
}

/**
 * 目标体重落在 Y 轴窗口外时的提示。
 *
 * **刻意不把目标硬塞进坐标轴**。窗口宽度永远是 `3 × 主步长`，把一个远在 6kg 外的目标
 * 塞进来，步长会从 1 被顶到 3，窗口从 3 个单位涨到 9 个 —— 折线振幅从 47% 掉到 16%。
 * 换成一个角标，信息反而更全（直接告诉你还差多少）。
 */
data class GoalOffscreen(
    val targetKg: Double,
    /** 最新一个绘图点与目标的差距。正数表示还要减，负数表示还要增 */
    val gapKg: Double,
    /** 目标在窗口**下方**（true）还是上方。决定角标贴下沿还是上沿 */
    val below: Boolean,
)


data class GoalUi(
    /** 最新体重，作为目标卡片的"当前体重" */
    val currentWeightKg: Double? = null,
    val targetWeightKg: Double? = null,
    /** 设置目标时的体重，进度条的起点。改身高时不能被覆盖 */
    val startWeightKg: Double? = null,
    /** 目标进度 0f..1f。未设目标或无起点体重时为 null，此时不显示进度条 */
    val progress: Float? = null,
) {
    val hasTarget: Boolean get() = targetWeightKg != null
}

/** 一次性事件（Snackbar 之类），用 Channel 发，避免旋转屏幕后重放。 */
sealed interface HomeEvent {
    data class RecordSaved(val id: Long, val weightKg: Double) : HomeEvent
    data object ProfileSaved : HomeEvent
    data object RecordUpdated : HomeEvent

    /** 导出的条数。0 表示没有记录可导 */
    data class Exported(val count: Int) : HomeEvent
    data class Imported(val added: Int, val skipped: Int) : HomeEvent
    data object DataCleared : HomeEvent
    /** 读写出错（用户没选文件、文件不可读等） */
    data class DataFailed(val exporting: Boolean, val reason: String) : HomeEvent

    // ── 体脂秤同步 ──

    /**
     * 同步完成。[inserted] 是真正新增的，[claimed] 是认领到已有手动记录上的
     * （不新增行，所以单独报，不然用户会以为丢数据了），[skipped] 是被跳过的。
     *
     * [firstSync] 是「这次之前从没成功同步过」。首次接入必然把早于时间边界的记录
     * 全跳过，那时该说「从现在开始记录新的称重」；之后同样的 [skipped] 只意味着
     * 「没有新数据」，不能再说那句（见 `MainActivity.syncMessage`）。
     */
    data class SyncFinished(
        val inserted: Int,
        val claimed: Int,
        val skipped: Int,
        val firstSync: Boolean,
    ) : HomeEvent

    data class SyncUnavailable(val availability: SyncAvailabilityUi) : HomeEvent
    data object SyncPermissionDenied : HomeEvent
    data class SyncFailed(val reason: String) : HomeEvent
}

/**
 * 同步入口的可用状态。
 *
 * 这是 **UI 层的镜像**，不是 data 层的 `HealthConnectAvailability` ——
 * Composable 不该认识 SDK 的类型（分层约定，见 AGENTS「架构约定」）。
 * 由 ViewModel 做映射。
 */
enum class SyncAvailabilityUi {
    /** API < 28：那一行**整行不显示**，否则用户会看到一个永远失败的按钮 */
    UNSUPPORTED,

    /** Android 9–13 且没装「健康数据共享」 */
    NOT_INSTALLED,

    /** 装了但版本太老 */
    NEEDS_UPDATE,

    AVAILABLE,
}

/**
 * 设置页里体脂秤那一行的全部状态。
 *
 * 只放原始数据，文案由 Composable 组装（决策 C5）。
 */
data class HealthConnectUi(
    val availability: SyncAvailabilityUi = SyncAvailabilityUi.UNSUPPORTED,
    /** 体重与体脂率的读权限是否**都**已授予（HC 允许只授一半） */
    val granted: Boolean = false,
    val syncing: Boolean = false,
    val lastSyncAt: Instant? = null,
    /** 库里来自 Health Connect 的条数 */
    val syncedCount: Int = 0,
    /**
     * 库里一条记录都没有 —— 首次接入必须先让用户手动记一条自己的体重。
     *
     * 那条记录同时是**时间边界的起点**和**异常过滤的锚点**：
     * 没有它，第一次同步会把一家人的数据无差别吞进来（`docs/08` §6.2）。
     */
    val needsAnchor: Boolean = false,
)

/**
 * 「实验功能」这一节的状态：一个总开关 + 两种同步方式。
 *
 * 分三层而不是一个三选一：总开关管**这一节露不露面**，两个同步方式各管**同步什么时候发生**，
 * 两者可以同时打开（自动同步开着时首页按钮也还在，用户随时能手动补一次）。
 */
data class ExperimentalUi(
    /** 总开关。默认关；关着时设置页那一节只剩开关本身，同步相关的行全部不出现 */
    val enabled: Boolean = false,

    /** 手动同步：打开后首页顶栏出现同步按钮，点了才同步 */
    val manualSync: Boolean = false,

    /** 自动同步：每次冷启动自动同步一次（默认关） */
    val autoSync: Boolean = false,
) {
    /**
     * 两个同步方式是否**真的生效**。
     *
     * 总开关关掉时，下面两个开关的值**保留**（用户重新打开还是原样），但一律不生效 ——
     * 否则会出现「总开关关了，首页那个同步按钮还杵在那」的矛盾状态。
     */
    val manualActive: Boolean get() = enabled && manualSync
    val autoActive: Boolean get() = enabled && autoSync
}
