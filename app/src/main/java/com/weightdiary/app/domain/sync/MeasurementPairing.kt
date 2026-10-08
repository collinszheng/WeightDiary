package com.weightdiary.app.domain.sync

import java.time.Duration
import java.time.ZoneId

/**
 * 把 Health Connect 的**体重**与**体脂率**配成一次称重。
 *
 * 这两者在 HC 里是**两条独立记录、各有自己的 `metadata.id`**（`docs/08` §4.1 第 2 层），
 * 所以要按「同一本地日 + 时间最近」配对。配不上的体脂率记录只能丢掉并计数 ——
 * 一条没有体重的体脂率进不了本 App 的数据模型（体重是必填的）。
 *
 * 纯函数、无 Android 依赖。
 */
object MeasurementPairing {

    data class Result(
        val measurements: List<PulledMeasurement>,
        /** 没配上体重的体脂率条数。它不该被静默吞掉，要报出来 */
        val unpairedBodyFat: Int,
    )

    fun pair(
        weights: List<RawWeight>,
        bodyFats: List<RawBodyFat>,
        zone: ZoneId,
    ): Result {
        // 配对是**全局按时间差升序**的贪心，而不是「按体重的时间顺序、各取离自己最近的」。
        //
        // 后者是先到先得：先被处理的（更早的）体重会抢走其实离**后面**某条更近的体脂。
        // 真机上踩到过 —— 体脂秤把体重与体脂率写在**同一时刻**（这是常态），
        // 结果体脂被 2 分钟前的那条体重抢走，用户看到体脂挂在了错的那一行。
        // 见 docs/09-真机实测记录.md §6.15
        val weightDays = weights.map { it.measuredAt.atZone(zone).toLocalDate() }
        val fatDays = bodyFats.map { it.measuredAt.atZone(zone).toLocalDate() }

        // 同一天的所有 (体重, 体脂) 组合，按时间差升序 —— 最近的一对先认领
        val candidates = buildList {
            for (wi in weights.indices) {
                for (fi in bodyFats.indices) {
                    if (weightDays[wi] != fatDays[fi]) continue
                    add(
                        Triple(
                            wi,
                            fi,
                            Duration.between(bodyFats[fi].measuredAt, weights[wi].measuredAt).abs(),
                        ),
                    )
                }
            }
        }.sortedBy { it.third }

        val fatOfWeight = HashMap<Int, RawBodyFat>()
        val usedFat = HashSet<Int>()
        for ((wi, fi, _) in candidates) {
            // 一条体脂只能被用一次，否则会把一次体成分摊到两次称重上
            if (fatOfWeight.containsKey(wi) || fi in usedFat) continue
            fatOfWeight[wi] = bodyFats[fi]
            usedFat += fi
        }

        val measurements = weights.indices
            .sortedBy { weights[it].measuredAt }
            .map { wi ->
                val weight = weights[wi]
                PulledMeasurement(
                    externalId = weight.externalId,
                    measuredAt = weight.measuredAt,
                    weightKg = weight.weightKg,
                    bodyFatPercent = fatOfWeight[wi]?.percent,
                )
            }

        return Result(measurements, bodyFats.size - usedFat.size)
    }
}
