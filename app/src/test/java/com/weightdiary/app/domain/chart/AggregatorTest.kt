package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.model.WeightRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 聚合器测试。
 *
 * 2026-10 起图表**只画体重**，所以原先围绕「切换指标时锁定同一条记录」的那几条用例
 * （体脂取哪条、没设身高 BMI 出不来…）已经没有对应代码，随之删除。
 * 换指标的能力本身也没了，见决策记录。
 */
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
        start: String = "2026-06-01",
        end: String = "2026-06-30",
    ) = Aggregator.aggregate(
        records = records,
        range = rangeOf(start, end),
        granularity = granularity,
        zone = zone,
    )

    // ─────────────── RAW ───────────────

    @Test
    fun `RAW 保留全部原始记录`() {
        val records = listOf(
            rec(1, "2026-06-01T08:00:00Z", 68.5),
            rec(2, "2026-06-01T20:00:00Z", 69.2),
        )
        val pts = aggregate(records, Granularity.RAW)
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
        val pts = aggregate(records, Granularity.RAW)
        assertEquals(1, pts.size)
        assertEquals(68.5, pts[0].value, 1e-9)
    }

    // ─────────────── DAILY ───────────────

    @Test
    fun `DAILY 每天一个点`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 68.5),
            rec(2, "2026-06-01T20:00:00Z", 69.2),
            rec(3, "2026-06-03T07:00:00Z", 68.0),
        )
        val pts = aggregate(records, Granularity.DAILY)
        assertEquals(2, pts.size)
        assertEquals(68.5, pts[0].value, 1e-9)
        assertEquals(68.0, pts[1].value, 1e-9)
    }

    /**
     * 同一天记了多条时取**体重最低**的那条，并且 `sourceRecordId` 要指向它本人
     * —— 气泡与选中态靠这个 id 定位，指错了会选到另一条记录。
     */
    @Test
    fun `DAILY 取当日体重最低的那条记录`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 69.0),
            rec(2, "2026-06-01T20:00:00Z", 68.5),
            rec(3, "2026-06-01T22:00:00Z", 68.8),
        )
        val pts = aggregate(records, Granularity.DAILY)
        assertEquals(1, pts.size)
        assertEquals(68.5, pts[0].value, 1e-9)
        assertEquals(2L, pts[0].sourceRecordId)
    }

    @Test
    fun `DAILY 的横坐标是那条记录的真实测量时刻`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 69.0),
            rec(2, "2026-06-01T20:00:00Z", 68.5),
        )
        val pts = aggregate(records, Granularity.DAILY)
        assertEquals("2026-06-01T20:00:00Z", pts[0].time.toString())
    }

    // ─────────────── MONTHLY ───────────────

    /**
     * 月聚合点的横坐标必须是**月中**，不能是该月最后一条记录的时间。
     * 否则当前月才过几天时（10 月只到 3 号），它的点会紧贴 9 月的点糊成一团。
     */
    @Test
    fun `MONTHLY 的点落在月中而不是最后一条记录的时间`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 68.0),
            rec(2, "2026-06-03T07:00:00Z", 67.0),
        )
        val pts = aggregate(records, Granularity.MONTHLY)
        assertEquals(1, pts.size)
        assertEquals("点应当落在 6 月 15 日中午", "2026-06-15T12:00:00Z", pts[0].time.toString())
    }

    @Test
    fun `MONTHLY 取月平均值`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 70.0),
            rec(2, "2026-06-15T07:00:00Z", 68.0),
            rec(3, "2026-06-30T07:00:00Z", 66.0),
        )
        val pts = aggregate(records, Granularity.MONTHLY)
        assertEquals(1, pts.size)
        assertEquals(68.0, pts[0].value, 1e-9)
    }

    @Test
    fun `MONTHLY 跨月分成多个点`() {
        val records = listOf(
            rec(1, "2026-06-10T07:00:00Z", 68.0),
            rec(2, "2026-06-20T07:00:00Z", 67.0),
            rec(3, "2026-07-05T07:00:00Z", 66.0),
        )
        val pts = aggregate(records, Granularity.MONTHLY, start = "2026-06-01", end = "2026-07-31")
        assertEquals(2, pts.size)
        assertEquals(67.5, pts[0].value, 1e-9)
        assertEquals(66.0, pts[1].value, 1e-9)
    }

    /** 代表记录取该月最后一条 —— 气泡定位靠它 */
    @Test
    fun `MONTHLY 的代表记录是当月最后一条`() {
        val records = listOf(
            rec(1, "2026-06-01T07:00:00Z", 70.0),
            rec(2, "2026-06-15T07:00:00Z", 68.0),
            rec(3, "2026-06-30T07:00:00Z", 66.0),
        )
        val pts = aggregate(records, Granularity.MONTHLY)
        assertEquals(3L, pts[0].sourceRecordId)
    }

    // ─────────────── 通用 ───────────────

    @Test
    fun `结果按时间升序`() {
        val records = listOf(
            rec(1, "2026-06-03T07:00:00Z", 68.0),
            rec(2, "2026-06-01T07:00:00Z", 69.0),
            rec(3, "2026-06-02T07:00:00Z", 68.5),
        )
        val pts = aggregate(records, Granularity.DAILY)
        assertEquals(listOf(69.0, 68.5, 68.0), pts.map { it.value })
    }

    @Test
    fun `空输入产出空列表`() {
        assertTrue(aggregate(emptyList(), Granularity.DAILY).isEmpty())
        assertTrue(aggregate(emptyList(), Granularity.MONTHLY).isEmpty())
        assertTrue(aggregate(emptyList(), Granularity.RAW).isEmpty())
    }

    @Test
    fun `没填体脂不影响体重聚合`() {
        val records = listOf(rec(1, "2026-06-01T07:00:00Z", 68.5, fat = null))
        val pts = aggregate(records, Granularity.DAILY)
        assertEquals(1, pts.size)
        assertEquals(68.5, pts[0].value, 1e-9)
    }
}
