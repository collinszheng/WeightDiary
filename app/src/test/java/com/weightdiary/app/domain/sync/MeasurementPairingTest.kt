package com.weightdiary.app.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class MeasurementPairingTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(text: String): Instant = OffsetDateTime.parse(text).toInstant()

    private fun weight(text: String, kg: Double, id: String) = RawWeight(id, at(text), kg)

    private fun fat(text: String, percent: Double, id: String) = RawBodyFat(id, at(text), percent)

    @Test
    fun `同一天的体脂率配给时间最近的那次称重`() {
        val result = MeasurementPairing.pair(
            weights = listOf(weight("2026-10-03T07:45:00+08:00", 68.5, "w1")),
            bodyFats = listOf(
                fat("2026-10-03T07:48:00+08:00", 22.5, "f1"),
                fat("2026-10-03T20:00:00+08:00", 23.0, "f2"),
            ),
            zone = zone,
        )

        assertEquals(1, result.measurements.size)
        assertEquals(22.5, result.measurements[0].bodyFatPercent!!, 1e-9)
        assertEquals("另一条没配上，要报出来", 1, result.unpairedBodyFat)
    }

    @Test
    fun `一条体脂率只能被用一次 - 否则会把一次体成分摊到两次称重上`() {
        val result = MeasurementPairing.pair(
            weights = listOf(
                weight("2026-10-03T07:45:00+08:00", 68.5, "w1"),
                weight("2026-10-03T07:50:00+08:00", 68.6, "w2"),
            ),
            bodyFats = listOf(fat("2026-10-03T07:48:00+08:00", 22.5, "f1")),
            zone = zone,
        )

        assertEquals(2, result.measurements.size)
        // 07:48 离 w2(07:50) 2 分钟、离 w1(07:45) 3 分钟 —— 全局最近的那条拿。
        // （旧实现按体重时间升序先到先得，会把它给 w1；那是错的，见下面那条真机回归）
        assertNull(result.measurements[0].bodyFatPercent)
        assertEquals(22.5, result.measurements[1].bodyFatPercent!!, 1e-9)
        assertEquals(0, result.unpairedBodyFat)
    }

    /**
     * 真机回归（小米 11 / HyperOS，2025-09-08），详见 [docs/07-真机测试清单.md §6.15]。
     *
     * 体脂秤把体重与体脂率写在**同一时刻**（这是常态）。旧实现按体重的时间升序、
     * 各自抢「离自己最近的」，于是先被处理的 68.0（02:55）抢走了本该属于
     * 67.8（02:57，与体脂**同刻**）的体脂 —— 用户看到体脂挂在了错的那一行。
     */
    @Test
    fun `真机回归 - 体脂落在同一时刻的体重上而不是更早的那条`() {
        val result = MeasurementPairing.pair(
            weights = listOf(
                weight("2026-09-08T02:55:00+08:00", 68.0, "w-0255"),
                weight("2026-09-08T02:57:00+08:00", 67.8, "w-0257"),
            ),
            bodyFats = listOf(fat("2026-09-08T02:57:00+08:00", 20.5, "f-0257")),
            zone = zone,
        )

        val byId = result.measurements.associateBy { it.externalId }
        assertEquals(
            "体脂必须落在同一时刻的那条 67.8 上",
            20.5,
            byId.getValue("w-0257").bodyFatPercent!!,
            1e-9,
        )
        assertNull("更早的 68.0 不该拿到它", byId.getValue("w-0255").bodyFatPercent)
        assertEquals(0, result.unpairedBodyFat)
    }

    @Test
    fun `跨天的体脂率不配 - 配对只认同一本地日`() {
        val result = MeasurementPairing.pair(
            weights = listOf(weight("2026-10-03T07:45:00+08:00", 68.5, "w1")),
            bodyFats = listOf(fat("2026-10-04T07:45:00+08:00", 22.5, "f1")),
            zone = zone,
        )

        assertNull(result.measurements[0].bodyFatPercent)
        assertEquals(1, result.unpairedBodyFat)
    }

    @Test
    fun `没有体脂率记录时体重照样成行 - 体脂率是选填的`() {
        val result = MeasurementPairing.pair(
            weights = listOf(weight("2026-10-03T07:45:00+08:00", 68.5, "w1")),
            bodyFats = emptyList(),
            zone = zone,
        )

        assertEquals(1, result.measurements.size)
        assertNull(result.measurements[0].bodyFatPercent)
        assertEquals(0, result.unpairedBodyFat)
    }

    @Test
    fun `输出按时间升序 - SyncPlanner 的顺序依赖它`() {
        val result = MeasurementPairing.pair(
            weights = listOf(
                weight("2026-10-03T20:00:00+08:00", 68.9, "w2"),
                weight("2026-10-03T07:45:00+08:00", 68.5, "w1"),
            ),
            bodyFats = emptyList(),
            zone = zone,
        )

        assertEquals(listOf("w1", "w2"), result.measurements.map { it.externalId })
    }

    @Test
    fun `没有体重记录时什么也不产出`() {
        val result = MeasurementPairing.pair(
            weights = emptyList(),
            bodyFats = listOf(fat("2026-10-03T07:48:00+08:00", 22.5, "f1")),
            zone = zone,
        )

        assertEquals(0, result.measurements.size)
        assertEquals("没有体重就没法成行，但这条要计数", 1, result.unpairedBodyFat)
    }
}
