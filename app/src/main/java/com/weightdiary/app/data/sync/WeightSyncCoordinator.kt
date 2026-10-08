package com.weightdiary.app.data.sync

import com.weightdiary.app.data.healthconnect.HealthConnectAvailability
import com.weightdiary.app.data.healthconnect.HealthConnectSource
import com.weightdiary.app.data.prefs.SyncStore
import com.weightdiary.app.data.repository.WeightRepository
import com.weightdiary.app.domain.sync.MeasurementPairing
import com.weightdiary.app.domain.sync.SkipReason
import com.weightdiary.app.domain.sync.SyncPlanner
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** 一次同步的结果。UI 按它决定提示文案与设置页的状态行 */
sealed interface SyncOutcome {

    data class Success(
        /** 真正新增的行数 */
        val inserted: Int,
        /** 认领到已有手动记录上的条数（不算新增） */
        val claimed: Int,
        val skipped: Map<SkipReason, Int>,
        /** 没配上体重的体脂率条数 */
        val unpairedBodyFat: Int,
    ) : SyncOutcome {
        val skippedCount: Int get() = skipped.values.sum()
    }

    data class Unavailable(val availability: HealthConnectAvailability) : SyncOutcome

    data object PermissionDenied : SyncOutcome

    data class Failed(val reason: String) : SyncOutcome
}

/**
 * 把「读 HC → 配对 → 规划 → 落库」串起来。
 *
 * 判断逻辑**一条都不在这里** —— 全在 `domain/sync`（可 JVM 单测）。
 * 这里只负责编排与副作用：读 SDK、开事务、推进水位线。
 */
class WeightSyncCoordinator(
    private val repository: WeightRepository,
    private val syncStore: SyncStore,
    private val source: HealthConnectSource,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    companion object {
        /** 不申请历史权限时，HC 只给最近 30 天（`docs/06` §3 已核实） */
        const val WINDOW_DAYS = 30L

        /** 每次往回收这么多天，兜「品牌 App 延迟写入」 */
        const val OVERLAP_DAYS = 7L
    }

    /** 给 UI 判断该显示什么。查询的是 SDK 状态，不是 Flow，所以由调用方决定何时刷新 */
    fun availability(): HealthConnectAvailability = source.availability()

    /** 体重与体脂率的读权限是否都已授予。HC 允许只授一半，所以要求 `containsAll` */
    suspend fun hasPermissions(): Boolean =
        runCatching { permissionsSatisfied(source.grantedPermissions()) }.getOrDefault(false)

    fun permissionsSatisfied(granted: Set<String>): Boolean =
        granted.containsAll(HealthConnectSource.PERMISSIONS)

    suspend fun sync(now: Instant = Instant.now()): SyncOutcome {
        val availability = source.availability()
        if (availability != HealthConnectAvailability.AVAILABLE) {
            return SyncOutcome.Unavailable(availability)
        }

        val granted = runCatching { source.grantedPermissions() }
            .getOrElse { return SyncOutcome.Failed(it.shortReason()) }
        if (!granted.containsAll(HealthConnectSource.PERMISSIONS)) {
            return SyncOutcome.PermissionDenied
        }

        val from = windowStart(syncStore.lastSyncAt.first(), now)
        val raw = runCatching { source.read(from, now) }
            .getOrElse { return SyncOutcome.Failed(it.shortReason()) }

        val pairing = MeasurementPairing.pair(raw.weights, raw.bodyFats, zone)
        val plan = SyncPlanner.plan(
            existing = repository.existingForSync(),
            ignoredExternalIds = repository.ignoredExternalIds(),
            pulled = pairing.measurements,
            zone = zone,
            now = now,
        )

        val inserted = repository.applySyncPlan(plan)
        // 只有整条流程顺利走完才推进水位线。中途失败就保持原样，下次从头来 ——
        // 提前推进会让失败那次覆盖的时间段再也拉不到。
        syncStore.setLastSyncAt(now)

        return SyncOutcome.Success(
            inserted = inserted,
            claimed = plan.claims.size,
            skipped = plan.skipped,
            unpairedBodyFat = pairing.unpairedBodyFat,
        )
    }

    /**
     * 拉取窗口的起点 = `max(30 天前, 上次同步 − 7 天)`。
     *
     * 重叠是为了兜「称完三天后才同步进 HC」：不重叠的话，那条记录的时间戳早于
     * 水位线，就永远拉不到了。重复拉到的部分由 `externalId` 唯一索引吃掉。
     */
    private fun windowStart(lastSync: Instant?, now: Instant): Instant {
        val thirtyDaysAgo = now.minus(Duration.ofDays(WINDOW_DAYS))
        val overlapStart = lastSync?.minus(Duration.ofDays(OVERLAP_DAYS))
        return if (overlapStart != null && overlapStart.isAfter(thirtyDaysAgo)) {
            overlapStart
        } else {
            thirtyDaysAgo
        }
    }
}

/** 异常信息可能很长（带类名），提示里只留一句能看懂的 */
private fun Throwable.shortReason(): String =
    (message ?: this::class.java.simpleName).take(60)
