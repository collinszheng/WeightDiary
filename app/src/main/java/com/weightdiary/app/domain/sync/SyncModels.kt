package com.weightdiary.app.domain.sync

import com.weightdiary.app.domain.model.RecordSource
import java.time.Instant

/** Health Connect 拉回来的一条**体重**记录（只留同步用得上的字段） */
data class RawWeight(
    val externalId: String,
    val measuredAt: Instant,
    val weightKg: Double,
)

/** Health Connect 拉回来的一条**体脂率**记录 */
data class RawBodyFat(
    val externalId: String,
    val measuredAt: Instant,
    val percent: Double,
)

/**
 * 配对后的「一次称重」。
 *
 * [externalId] 取**体重**那条记录的 `metadata.id`；体脂率那条的 id 不存 ——
 * 它只是被搭了个便车，本 App 的数据模型里一次称重就是一行。
 */
data class PulledMeasurement(
    val externalId: String,
    val measuredAt: Instant,
    val weightKg: Double,
    val bodyFatPercent: Double?,
)

/** 库里已有记录的「同步视图」—— 只含去重与过滤用得上的字段 */
data class ExistingRecord(
    val id: Long,
    val measuredAt: Instant,
    val weightKg: Double,
    val externalId: String?,
    val source: RecordSource,
)

/** 为什么跳过。用于设置页「跳过 N 条」的报数，不是给人看的错误码 */
enum class SkipReason {
    /** 早于时间边界 T（最后一条手动记录）—— `docs/08` §4.3 */
    BOUNDARY,

    /** 用户删过这条，墓碑里记着 */
    TOMBSTONED,

    /** 已经在库里：externalId 撞了，或「同一本地日 + 体重相近」 */
    DUPLICATE,

    /** 超出 `RecordValidator` 的合法区间 */
    OUT_OF_RANGE,

    /** 时间落在未来 */
    FUTURE,

    /** 与自己的历史差异过大（共用体脂秤上的别人）—— `docs/08` §4.4 */
    OUTLIER,
}

/** 「邻近认领」：把外部 id 挂到一条已有的手动记录上，不新增行 */
data class Claim(val id: Long, val externalId: String)

data class SyncPlan(
    val inserts: List<PulledMeasurement>,
    val claims: List<Claim>,
    val skipped: Map<SkipReason, Int>,
) {
    val skippedCount: Int get() = skipped.values.sum()

    companion object {
        val EMPTY = SyncPlan(emptyList(), emptyList(), emptyMap())
    }
}
