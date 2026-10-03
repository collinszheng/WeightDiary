package com.weightdiary.app.domain.record

import com.weightdiary.app.domain.model.WeightRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecordRowsTest {

    private fun rec(id: Long, at: String, kg: Double) = WeightRecord(
        id = id,
        measuredAt = Instant.parse(at),
        weightKg = kg,
        bodyFatPercent = null,
        note = null,
        createdAt = Instant.parse(at),
        updatedAt = Instant.parse(at),
    )

    @Test
    fun `空列表产出空行`() {
        assertEquals(emptyList<RecordRow>(), RecordRows.build(emptyList()))
    }

    @Test
    fun `单条记录没有变化量`() {
        val rows = RecordRows.build(listOf(rec(1, "2026-06-30T08:00:00Z", 68.5)))
        assertEquals(1, rows.size)
        assertNull(rows[0].deltaKg)
    }

    /**
     * 关键口径：入参是**倒序**的（新的在前），所以第 i 行的「上一条」是 i+1。
     * 这里最容易写成 i−1，那样每行的变化量会整体反号。
     */
    @Test
    fun `变化量是与时间更早那条的差`() {
        // 时间倒序：6/30 最新，6/29 其次，6/28 最早
        val rows = RecordRows.build(
            listOf(
                rec(3, "2026-06-30T08:00:00Z", 68.5),
                rec(2, "2026-06-29T08:00:00Z", 69.0),
                rec(1, "2026-06-28T08:00:00Z", 68.2),
            ),
        )

        assertEquals(3, rows.size)
        // 68.5 − 69.0 = −0.5（相比前一天降了）
        assertEquals(-0.5, rows[0].deltaKg!!, 1e-9)
        // 69.0 − 68.2 = +0.8（相比前一天涨了）
        assertEquals(0.8, rows[1].deltaKg!!, 1e-9)
        // 最早那条没有可比对象
        assertNull(rows[2].deltaKg)
    }

    @Test
    fun `保持入参顺序 - 不重新排序`() {
        val rows = RecordRows.build(
            listOf(
                rec(3, "2026-06-30T08:00:00Z", 68.5),
                rec(2, "2026-06-29T08:00:00Z", 69.0),
            ),
        )
        assertEquals(listOf(3L, 2L), rows.map { it.id })
    }

    @Test
    fun `字段原样带过去`() {
        val source = WeightRecord(
            id = 7,
            measuredAt = Instant.parse("2026-06-30T08:00:00Z"),
            weightKg = 68.5,
            bodyFatPercent = 21.8,
            note = "晚饭后",
            createdAt = Instant.parse("2026-06-30T08:00:00Z"),
            updatedAt = Instant.parse("2026-06-30T08:00:00Z"),
        )
        val row = RecordRows.build(listOf(source))[0]
        assertEquals(7L, row.id)
        assertEquals(68.5, row.weightKg, 1e-9)
        assertEquals(21.8, row.bodyFatPercent!!, 1e-9)
        assertEquals("晚饭后", row.note)
        assertEquals(source.measuredAt, row.measuredAt)
    }

    @Test
    fun `体重相同的两条变化量为 0 而不是 null`() {
        val rows = RecordRows.build(
            listOf(
                rec(2, "2026-06-30T08:00:00Z", 68.5),
                rec(1, "2026-06-29T08:00:00Z", 68.5),
            ),
        )
        assertEquals(0.0, rows[0].deltaKg!!, 1e-9)
    }

    @Test
    fun `同一天多条也按相邻顺序算`() {
        val rows = RecordRows.build(
            listOf(
                rec(3, "2026-06-30T20:00:00Z", 68.0),
                rec(2, "2026-06-30T08:00:00Z", 68.6),
                rec(1, "2026-06-29T08:00:00Z", 69.0),
            ),
        )
        assertEquals(-0.6, rows[0].deltaKg!!, 1e-9)
        assertEquals(-0.4, rows[1].deltaKg!!, 1e-9)
        assertNull(rows[2].deltaKg)
    }
}
