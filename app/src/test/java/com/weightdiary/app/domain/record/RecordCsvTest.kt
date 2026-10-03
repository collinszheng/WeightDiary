package com.weightdiary.app.domain.record

import com.weightdiary.app.domain.model.WeightRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class RecordCsvTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun rec(id: Long, at: String, kg: Double, fat: Double? = null, note: String? = null) =
        WeightRecord(
            id = id,
            measuredAt = Instant.parse(at),
            weightKg = kg,
            bodyFatPercent = fat,
            note = note,
            createdAt = Instant.parse(at),
            updatedAt = Instant.parse(at),
        )

    // ─────────────── 导出 ───────────────

    @Test
    fun `导出带 BOM 和中文表头 - 否则 Excel 打开是乱码`() {
        val csv = RecordCsv.encode(listOf(rec(1, "2026-10-03T07:48:00Z", 68.5)), zone)
        assertTrue("开头缺少 BOM", csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("时间"))
        assertTrue(csv.contains("体重(kg)"))
    }

    @Test
    fun `按时间升序导出`() {
        val csv = RecordCsv.encode(
            listOf(
                rec(2, "2026-10-03T07:00:00Z", 68.0),
                rec(1, "2026-10-01T07:00:00Z", 69.0),
            ),
            zone,
        )
        val dates = csv.lineSequence().drop(1).filter { it.isNotBlank() }.map { it.substringBefore(',') }
        assertEquals(listOf("2026-10-01T15:00:00+08:00", "2026-10-03T15:00:00+08:00"), dates.toList())
    }

    /** 体重是算出来的 Double，直接 toString 会写出 71.86500000000001 这种噪声 */
    @Test
    fun `浮点噪声被抹平`() {
        val csv = RecordCsv.encode(
            listOf(
                rec(1, "2026-10-03T00:00:00Z", 71.86500000000001),
                rec(2, "2026-10-03T01:00:00Z", 71.52999999999999),
            ),
            zone,
        )
        assertTrue(csv.contains(",71.87,"))
        assertTrue(csv.contains(",71.53,"))
        assertTrue("不该出现浮点尾巴", !csv.contains("0000000"))
    }

    /** 毫秒对备份文件没有意义，还会把行撑长 */
    @Test
    fun `时间只写到秒`() {
        val csv = RecordCsv.encode(listOf(rec(1, "2026-10-03T00:00:00.439Z", 68.5)), zone)
        val time = csv.lineSequence().first { it.contains("68.5") }.substringBefore(',')
        assertEquals("2026-10-03T08:00:00+08:00", time)
    }

    @Test
    fun `整数体重的末尾零被去掉`() {
        val csv = RecordCsv.encode(listOf(rec(1, "2026-10-03T00:00:00Z", 68.0)), zone)
        assertTrue(csv.contains(",68,"))
    }

    @Test
    fun `没有体脂率时该格留空`() {
        val csv = RecordCsv.encode(listOf(rec(1, "2026-10-03T00:00:00Z", 68.5, fat = null)), zone)
        val line = csv.lineSequence().first { it.contains("68.5") }
        assertEquals(listOf("2026-10-03T08:00:00+08:00", "68.5", "", ""), line.split(","))
    }

    @Test
    fun `含逗号和引号的备注被正确转义`() {
        val csv = RecordCsv.encode(
            listOf(rec(1, "2026-10-03T00:00:00Z", 68.5, note = "晚饭后, 喝了\"很多\"水")),
            zone,
        )
        assertTrue(csv.contains("\"晚饭后, 喝了\"\"很多\"\"水\""))
    }

    // ─────────────── 导入 ───────────────

    @Test
    fun `导出再导入能原样还原`() {
        val source = listOf(
            rec(1, "2026-10-01T07:00:00Z", 69.0, 22.5, "第一天"),
            rec(2, "2026-10-02T07:00:00Z", 68.5, null, "带,逗号"),
            rec(3, "2026-10-03T07:00:00Z", 68.0, 21.8, null),
        )
        val decoded = RecordCsv.decode(RecordCsv.encode(source, zone))

        assertEquals(0, decoded.skipped)
        assertEquals(3, decoded.rows.size)
        assertEquals(source.map { it.measuredAt }, decoded.rows.map { it.measuredAt })
        assertEquals(source.map { it.weightKg }, decoded.rows.map { it.weightKg })
        assertEquals(listOf(22.5, null, 21.8), decoded.rows.map { it.bodyFatPercent })
        // null 与空串在 CSV 里分不开，回来都变成 null —— 这是预期的无损边界
        assertEquals(listOf("第一天", "带,逗号", null), decoded.rows.map { it.note })
    }

    @Test
    fun `没有表头也能导`() {
        val decoded = RecordCsv.decode("2026-10-03T07:00:00+08:00,68.5,21.8,测试")
        assertEquals(1, decoded.rows.size)
        assertEquals(68.5, decoded.rows[0].weightKg, 1e-9)
    }

    @Test
    fun `坏行只跳过它自己而不是整份失败`() {
        val csv = """
            时间,体重(kg),体脂率(%),备注
            2026-10-01T07:00:00+08:00,69.0,,
            这不是时间,68.0,,
            2026-10-03T07:00:00+08:00,不是数字,,
            2026-10-04T07:00:00+08:00,68.0,,
        """.trimIndent()
        val decoded = RecordCsv.decode(csv)
        assertEquals("应当救回 2 行", 2, decoded.rows.size)
        assertEquals("应当跳过 2 行", 2, decoded.skipped)
    }

    @Test
    fun `空文件不崩`() {
        assertEquals(0, RecordCsv.decode("").rows.size)
        assertEquals(0, RecordCsv.decode("\uFEFF").rows.size)
    }

    @Test
    fun `非正数的体重被拒`() {
        val csv = """
            2026-10-01T07:00:00+08:00,0,,
            2026-10-02T07:00:00+08:00,-5,,
            2026-10-03T07:00:00+08:00,68.0,,
        """.trimIndent()
        val decoded = RecordCsv.decode(csv)
        assertEquals(1, decoded.rows.size)
        assertEquals(2, decoded.skipped)
        assertEquals(68.0, decoded.rows[0].weightKg, 1e-9)
    }

    @Test
    fun `带引号与换行感的备注能被解析回原样`() {
        val decoded = RecordCsv.decode("2026-10-03T07:00:00+08:00,68.5,,\"含,逗号\"\"与引号\"")
        assertEquals("含,逗号\"与引号", decoded.rows[0].note)
    }

    @Test
    fun `缺少备注列时备注为 null`() {
        val decoded = RecordCsv.decode("2026-10-03T07:00:00+08:00,68.5")
        assertEquals(1, decoded.rows.size)
        assertNull(decoded.rows[0].note)
        assertNull(decoded.rows[0].bodyFatPercent)
    }
}
