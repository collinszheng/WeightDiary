package com.weightdiary.app.domain.sync

import com.weightdiary.app.domain.model.RecordSource
import com.weightdiary.app.domain.validation.RecordValidator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

/**
 * 同步的决策核心：**哪些该进库、哪些该跳过**。
 *
 * 纯函数、无 Android 依赖，可直接 JVM 单测 —— 这是本 feature 里唯一值得单测的部分，
 * 因为它是所有「数据被弄脏」风险的所在。HC 的调用、Room 的写入都不在这里。
 *
 * 规则与理由见 `docs/08` §4。执行顺序（先便宜后贵，且边界是硬的）：
 *
 * 1. 时间边界（一刀切：早于最后一条手动记录的一律丢）
 * 2. 墓碑（用户删过的）
 * 3. 幂等（externalId 已在库里）
 * 4. 时间合法性 / 范围合法性
 * 5. 邻近认领（同一本地日 + 体重相近 → 挂 id，不新增行）
 * 6. 异常过滤（与自己的历史差异过大 → 丢）
 */
object SyncPlanner {

    /**
     * 「同一次称重」的体重容差。
     *
     * 手动录入只让填 1 位小数，所以光是舍入误差就能到 0.05kg —— 0.1kg 刚好盖住它，
     * 又不会把两次真实称重（通常差 0.3kg 以上）并成一次。
     */
    const val SAME_WEIGHT_KG = 0.1

    /** 异常过滤的绝对下限（kg）：同一个人一天内的水分波动极少超过它 */
    const val OUTLIER_ABS_KG = 3.0

    /** 异常过滤的比例余量，给大体重者留的余地 */
    const val OUTLIER_RATIO = 0.05

    /**
     * 未来时间的容忍窗口。
     *
     * 比输入校验那个 +60 秒宽得多 —— 那个服务的是「选了当前时刻却报未来」的假失败，
     * 这个要兜的是体脂秤/手机的时钟偏移，语义不同，别复用。
     */
    val FUTURE_TOLERANCE: Duration = Duration.ofMinutes(10)

    fun plan(
        existing: List<ExistingRecord>,
        ignoredExternalIds: Set<String>,
        pulled: List<PulledMeasurement>,
        zone: ZoneId,
        now: Instant,
    ): SyncPlan {
        if (pulled.isEmpty()) return SyncPlan.EMPTY

        val skipped = LinkedHashMap<SkipReason, Int>()
        fun skip(reason: SkipReason) {
            skipped[reason] = (skipped[reason] ?: 0) + 1
        }

        // 时间边界 T = 库里最后一条**手动**记录。
        // 库里没有手动记录时 T 不存在 —— 那就不设边界（这也是首次接入必须先手动记一条的原因）。
        val boundary: Instant? = existing
            .filter { it.source == RecordSource.MANUAL }
            .maxOfOrNull { it.measuredAt }

        val knownIds = HashSet<String>()
        existing.forEach { record -> record.externalId?.let(knownIds::add) }

        val existingByDay: Map<LocalDate, List<ExistingRecord>> =
            existing.groupBy { it.measuredAt.atZone(zone).toLocalDate() }

        val claimedIds = HashSet<Long>()

        // 锚点 = 库里最新的那条体重（含同步来的）。
        // 刻意**不**优先取手动记录：三个月前的手动记录会把锚点钉在旧体重上，
        // 反而把现在合法的体重判成异常。而「锚点被脏数据污染」这个担心是不成立的 ——
        // 脏数据本来就被下面这道过滤挡在门外。
        var anchor: Double? = existing.maxByOrNull { it.measuredAt }?.weightKg

        val inserts = ArrayList<PulledMeasurement>()
        val claims = ArrayList<Claim>()

        pulled.sortedBy { it.measuredAt }.forEach { measurement ->
            // ① 时间边界
            if (boundary != null && !measurement.measuredAt.isAfter(boundary)) {
                skip(SkipReason.BOUNDARY)
                return@forEach
            }
            // ② 墓碑
            if (measurement.externalId in ignoredExternalIds) {
                skip(SkipReason.TOMBSTONED)
                return@forEach
            }
            // ③ 幂等
            if (measurement.externalId in knownIds) {
                skip(SkipReason.DUPLICATE)
                return@forEach
            }
            // ④ 时间 / 范围合法性
            if (measurement.measuredAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
                skip(SkipReason.FUTURE)
                return@forEach
            }
            if (RecordValidator.validateWeight(measurement.weightKg) != null) {
                skip(SkipReason.OUT_OF_RANGE)
                return@forEach
            }
            if (RecordValidator.validateBodyFat(measurement.bodyFatPercent) != null) {
                skip(SkipReason.OUT_OF_RANGE)
                return@forEach
            }

            val day = measurement.measuredAt.atZone(zone).toLocalDate()
            val twins = existingByDay[day].orEmpty()
                .filter { abs(it.weightKg - measurement.weightKg) <= SAME_WEIGHT_KG }

            // ⑤ 邻近认领：库里那条（没有外部 id 的）就是这次称重，挂上 id，不新增行
            val claimable = twins.firstOrNull { it.externalId == null && it.id !in claimedIds }
            if (claimable != null) {
                claims += Claim(claimable.id, measurement.externalId)
                claimedIds += claimable.id
                knownIds += measurement.externalId
                return@forEach
            }
            // 同一天同一个体重，但那条已经挂过别的外部 id（多个品牌 App 各写一条 HC 记录）
            if (twins.isNotEmpty()) {
                skip(SkipReason.DUPLICATE)
                knownIds += measurement.externalId
                return@forEach
            }
            // 本批内也判一次，挡的是同一批里被写了两遍的称重
            val batchTwin = inserts.any {
                it.measuredAt.atZone(zone).toLocalDate() == day &&
                    abs(it.weightKg - measurement.weightKg) <= SAME_WEIGHT_KG
            }
            if (batchTwin) {
                skip(SkipReason.DUPLICATE)
                knownIds += measurement.externalId
                return@forEach
            }

            // ⑥ 异常过滤：宁可漏，不可错。
            //
            // 锚点为 null 意味着「库里一条记录都没有」—— 那就不设锚点，本批全收。
            // 不能拿本批的第一条当基准：共用秤上第一条可能是家里另一个人的，
            // 拿它当基准会把**真正的主人**整批判成异常（见 docs/08 §4.4）。
            val base = anchor
            if (base != null &&
                abs(measurement.weightKg - base) > max(OUTLIER_ABS_KG, OUTLIER_RATIO * base)
            ) {
                skip(SkipReason.OUTLIER)
                return@forEach
            }

            inserts += measurement
            knownIds += measurement.externalId
            // 锚点跟着往前走，所以正常缓慢减重不会被误杀（它是 fold，不是一次性比较）。
            // 只在本来就有基准时才推进 —— 否则等于凭空造了一个基准出来。
            if (base != null) anchor = measurement.weightKg
        }

        return SyncPlan(
            inserts = inserts.toList(),
            claims = claims.toList(),
            skipped = skipped,
        )
    }
}
