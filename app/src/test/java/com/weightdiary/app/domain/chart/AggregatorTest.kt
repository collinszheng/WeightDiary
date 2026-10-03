package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.model.Metric
import com.weightdiary.app.domain.model.WeightRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class AggregatorTest {

    private val zone = ZoneId.of("UTC")

    private fun rec(id: Long, at: String, kg: Double, fat: Double? = null) = WeightRecord(
        id = id,
        measuredAt = Instant.parse(at),
        weightKg = kg,
        bodyFatPercent = fat,
        note = null,
        createdAt = Instant.parse(at),
        updatedAt = Instant.parse(at),
    )

    private fun rangeOf(startDay: String, endDay: String) = ChartRange(
        RangeResolver.startOfDay(LocalDate.parse(startDay), zone),
        RangeResolver.endOfDay(LocalDate.parse(endDay), zone),
    )

    private fun aggregate(
        records: List<WeightRecord>,
        granularity: Granularity,
        metric: Metric,
        heightCm: Double? = 175.0,
    ) = Aggregator.aggregate(
        records = records,
        range = rangeOf("2026-06-01", "2026-06-30"),
        granularity = granularity,
        metric = metric,
        heightCm = heightCm,
        zone = zone,
    )

    // ─────────────── RAW ───────────────

    @Test
    fun `RAW 保留全部原始记录`() {
        val records = listOf(
            rec(1, "2026-06-01T08:00:00Z", 68.5),
            rec(2, "2026-06-01T20:00:00Z", 69.2),
        )
        val pts = aggregate(records, Granularity.RAW, Metric.WEIGHT)
        assertEquals(2, pts.size)
        assertEquals(68.5, pts[0].value, 1e-9)
        assertEquals(69.2, pts[1].value, 1e-9)
    }

    @Test
    fun `范围外的记录被排除`() {
        val records = listOf(
            rec(1, "2026-05-31T08:00:00Z", 68.0),
            rec(2, "2026-06-15T08:00:00Z", 68.5),
            rec(3, "2026-07-01T08:00:00Z", 67.0),
        )
        val pts = aggregate(records, Granularity.RAW, Metric.WEIGHT)
        assertEquals(1, pts.size)
        assertEquals(68.5, pts[0].value, 1e-9)
    }

    // ─────────────── DAILY：必须锁定同一条记录 ───────────────

    /**
     * 这是本阶段最重要的不变量。
     *
     * 同一天两条记录：A(68.5kg, 体脂 25.0) 和 B(69.0kg, 体脂 21.0)。
     * 「当日最低值」应当锁定 A —— 所以体脂率要取 A 的 25.0，而**不是**两条里最低的 21.0。
     * 若对每个指标各自取最小值，图上会出现「最低体重」和「最低体脂」来自两次不同称重的错位。
     */
    @Test
    fun `DAILY 锁定同一条记录 - 体脂率跟体重走而不是各取最小`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 68.5, fat = 25.0),
            rec(2, "2026-06-01T20:00:00Z", 69.0, fat = 21.0),
        )

        val weight = aggregate(records, Granularity.DAILY, Metric.WEIGHT)
        assertEquals(1, weight.size)
        assertEquals(68.5, weight[0].value, 1e-9)
        assertEquals(1L, weight[0].sourceRecordId)

        val fat = aggregate(records, Granularity.DAILY, Metric.BODY_FAT)
        assertEquals(1, fat.size)
        assertEquals("体脂率应取自当日最低体重那条记录", 25.0, fat[0].value, 1e-9)
        assertEquals(1L, fat[0].sourceRecordId)
    }

    @Test
    fun `DAILY 每天一个点`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 68.5),
            rec(2, "2026-06-01T20:00:00Z", 69.2),
            rec(3, "2026-06-03T07:00:00Z", 68.0),
        )
        val pts = aggregate(records, Granularity.DAILY, Metric.WEIGHT)
        assertEquals(2, pts.size)
        assertEquals(68.5, pts[0].value, 1e-9)
        assertEquals(68.0, pts[1].value, 1e-9)
    }

    @Test
    fun `DAILY 遇到缺该指标的记录会顺延到下一条`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 68.5, fat = null), // 当日最低但没填体脂
            rec(2, "2026-06-01T20:00:00Z", 69.0, fat = 21.0),
        )
        val fat = aggregate(records, Granularity.DAILY, Metric.BODY_FAT)
        assertEquals(1, fat.size)
        assertEquals(21.0, fat[0].value, 1e-9)
        assertEquals(2L, fat[0].sourceRecordId)
    }

    @Test
    fun `没填体脂的记录不会变成 0`() {
        val records = listOf(rec(1, "2026-06-01T07:00:00Z", 68.5, fat = null))
        assertTrue(aggregate(records, Granularity.DAILY, Metric.BODY_FAT).isEmpty())
    }

    @Test
    fun `没设身高时 BMI 出不来 - 不崩也不给 0`() {
        val records = listOf(rec(1, "2026-06-01T07:00:00Z", 68.5))
        assertTrue(aggregate(records, Granularity.DAILY, Metric.BMI, heightCm = null).isEmpty())
    }

    @Test
    fun `BMI 由体重与身高实时算出`() {
        val records = listOf(rec(1, "2026-06-01T07:00:00Z", 68.5))
        val pts = aggregate(records, Granularity.DAILY, Metric.BMI, heightCm = 175.0)
        assertEquals(22.4, pts[0].value, 1e-9)
    }

    // ─────────────── MONTHLY ───────────────

    @Test
    fun `MONTHLY 取月平均值`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 70.0),
            rec(2, "2026-06-15T07:00:00Z", 68.0),
            rec(3, "2026-06-30T07:00:00Z", 66.0),
        )
        val pts = aggregate(records, Granularity.MONTHLY, Metric.WEIGHT)
        assertEquals(1, pts.size)
        assertEquals(68.0, pts[0].value, 1e-9)
    }

    @Test
    fun `MONTHLY 跨月分成多个点`() {
        // 区间要覆盖到 7 月，否则 7 月那条会被范围过滤掉
        val records = listOf(
            rec(1, "2026-06-10T07:00:00Z", 68.0),
            rec(2, "2026-06-20T07:00:00Z", 67.0),
            rec(3, "2026-07-05T07:00:00Z", 66.0),
        )
        val pts = Aggregator.aggregate(
            records = records,
            range = rangeOf("2026-06-01", "2026-07-31"),
            granularity = Granularity.MONTHLY,
            metric = Metric.WEIGHT,
            heightCm = 175.0,
            zone = zone,
        )
        assertEquals(2, pts.size)
        assertEquals(67.5, pts[0].value, 1e-9)
        assertEquals(66.0, pts[1].value, 1e-9)
    }

    @Test
    fun `结果按时间升序`() {
        val records = listOf(
            rec(1, "2026-06-03T07:00:00Z", 68.0),
            rec(2, "2026-06-01T07:00:00Z", 69.0),
            rec(3, "2026-06-02T07:00:00Z", 68.5),
        )
        val pts = aggregate(records, Granularity.DAILY, Metric.WEIGHT)
        assertEquals(listOf(69.0, 68.5, 68.0), pts.map { it.value })
    }

    @Test
    fun `空输入产出空列表`() {
        assertTrue(aggregate(emptyList(), Granularity.DAILY, Metric.WEIGHT).isEmpty())
        assertTrue(aggregate(emptyList(), Granularity.MONTHLY, Metric.WEIGHT).isEmpty())
        assertTrue(aggregate(emptyList(), Granularity.RAW, Metric.WEIGHT).isEmpty())
    }
}
