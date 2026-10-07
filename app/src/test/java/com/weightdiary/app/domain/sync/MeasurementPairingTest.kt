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
        assertEquals(22.5, result.measurements[0].bodyFatPercent!!, 1e-9)
        assertNull(result.measurements[1].bodyFatPercent)
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
