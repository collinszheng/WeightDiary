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
        // 同一天的体脂率按时间排好队，挨个被认领，每条最多用一次
        val byDay = bodyFats
            .groupBy { it.measuredAt.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.sortedBy { it.measuredAt }.toMutableList() }

        var unpaired = bodyFats.size

        val measurements = weights.sortedBy { it.measuredAt }.map { weight ->
            // 用 `?: mutableListOf()` 而不是 `orEmpty()`：后者会把 MutableList 退化成 List，
            // 后面就没法把已认领的那条移出队列了
            val candidates = byDay[weight.measuredAt.atZone(zone).toLocalDate()] ?: mutableListOf()
            val nearest = candidates.minByOrNull {
                Duration.between(it.measuredAt, weight.measuredAt).abs()
            }
            if (nearest != null) {
                candidates.remove(nearest)
                unpaired--
            }
            PulledMeasurement(
                externalId = weight.externalId,
                measuredAt = weight.measuredAt,
                weightKg = weight.weightKg,
                bodyFatPercent = nearest?.percent,
            )
        }

        return Result(measurements, unpaired)
    }
}
