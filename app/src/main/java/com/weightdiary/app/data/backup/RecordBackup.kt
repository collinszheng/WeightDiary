package com.weightdiary.app.data.backup

import android.content.Context
import android.net.Uri
import com.weightdiary.app.domain.model.WeightRecord
import com.weightdiary.app.domain.record.RecordCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * 记录的导入导出。
 *
 * 只负责跟 `ContentResolver` 打交道；CSV 的编解码在 [RecordCsv] 里（纯 Kotlin、可单测），
 * 落库在仓库层。这样这个类薄到几乎不会出错。
 *
 * 用 SAF（`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`）而不是自己写文件：
 * 不需要存储权限，用户自己选存到哪，也不会有「App 偷偷写了什么」的疑虑。
 */
class RecordBackup(private val context: Context) {

    suspend fun write(uri: Uri, records: List<WeightRecord>): Int = withContext(Dispatchers.IO) {
        val text = RecordCsv.encode(records, ZoneId.systemDefault())
        val stream = context.contentResolver.openOutputStream(uri, "wt")
            ?: error("打不开所选文件")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        records.size
    }

    suspend fun read(uri: Uri): RecordCsv.Decoded = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("打不开所选文件")
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        RecordCsv.decode(text)
    }
}
