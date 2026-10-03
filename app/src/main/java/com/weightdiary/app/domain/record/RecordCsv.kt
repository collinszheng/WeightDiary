package com.weightdiary.app.domain.record

import com.weightdiary.app.domain.model.WeightRecord
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 记录与 CSV 之间的互转。**纯 Kotlin，不碰文件系统** ——
 * 读写文件是 Android 层的事（[com.weightdiary.app.data.backup.RecordBackup]）。
 *
 * 表头用中文，方便用户直接拿 Excel / Numbers 打开看。
 * 时间用带时区偏移的 ISO-8601（`2026-10-03T07:48:00+08:00`）：
 * 既可读、又不会在导入时产生时区歧义。
 */
object RecordCsv {

    const val MIME_TYPE = "text/csv"

    private val HEADER = listOf("时间", "体重(kg)", "体脂率(%)", "备注")

    /**
     * 秒级精度、带时区偏移。
     *
     * 不用 ISO_OFFSET_DATE_TIME：它会把毫秒也写出来（`07:59:47.439`），
     * 备份文件不需要这种精度，而且解析时也照样能读回来。
     */
    private val TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /** 导入结果：成功解析出的行 + 被跳过的行数 */
    data class Decoded(
        val rows: List<Row>,
        val skipped: Int,
    )

    data class Row(
        val measuredAt: Instant,
        val weightKg: Double,
        val bodyFatPercent: Double?,
        val note: String?,
    )

    fun encode(records: List<WeightRecord>, zone: ZoneId): String {
        val sb = StringBuilder()
        // 开头的 BOM：Excel 只有见到它才会把 UTF-8 的中文备注认对，否则是乱码。
        // 解析时会 removePrefix 掉，来回一趟不受影响
        sb.append('\uFEFF')
        sb.append(HEADER.joinToString(",")).append("\r\n")

        records.sortedBy { it.measuredAt }.forEach { record ->
            sb.append(TIME_FORMAT.format(record.measuredAt.atZone(zone)))
            sb.append(',').append(formatNumber(record.weightKg))
            sb.append(',').append(record.bodyFatPercent?.let(::formatNumber) ?: "")
            sb.append(',').append(escape(record.note.orEmpty()))
            sb.append("\r\n")
        }
        return sb.toString()
    }

    /**
     * 解析。**尽量多救回几行**：单行格式不对只跳过那一行并计数，
     * 不整份失败 —— 用户拿到一份三十条的备份，不该因为其中一条坏了就全军覆没。
     */
    fun decode(text: String): Decoded {
        val lines = text.removePrefix("\uFEFF")
            .lineSequence()
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return Decoded(emptyList(), 0)

        // 第一行是表头就跳过。只要第一格不是合法时间就当作表头
        val body = if (lines.first().let { isHeader(it) }) lines.drop(1) else lines

        var skipped = 0
        val rows = ArrayList<Row>(body.size)
        body.forEach { line ->
            val cells = parseLine(line)
            val row = toRow(cells)
            if (row == null) skipped++ else rows += row
        }
        return Decoded(rows, skipped)
    }

    private fun isHeader(line: String): Boolean {
        val first = parseLine(line).firstOrNull()?.trim().orEmpty()
        return runCatching { OffsetDateTime.parse(first) }.isFailure
    }

    private fun toRow(cells: List<String>): Row? {
        if (cells.size < 2) return null
        val time = runCatching { OffsetDateTime.parse(cells[0].trim()).toInstant() }.getOrNull()
            ?: return null
        val kg = cells[1].trim().toDoubleOrNull() ?: return null
        if (!kg.isFinite() || kg <= 0.0) return null

        val fat = cells.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (fat != null && (!fat.isFinite() || fat <= 0.0)) return null

        val note = cells.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
        return Row(time, kg, fat, note)
    }

    /**
     * 数值格式化。
     *
     * 不能直接 `toString()` —— 体重是算出来的 Double，会写出
     * `71.86500000000001` 这种浮点噪声，用户拿 Excel 打开会一脸问号。
     * 保留两位小数再抹掉末尾的 0，既够精确又干净。
     */
    private fun formatNumber(value: Double): String =
        BigDecimal(value)
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    /** RFC 4180 的极简实现：支持双引号包裹、内部双写引号表示转义 */
    private fun parseLine(line: String): List<String> {
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }

                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    cells += current.toString()
                    current.clear()
                }

                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }
}
