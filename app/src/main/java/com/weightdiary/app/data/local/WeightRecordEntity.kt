package com.weightdiary.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.weightdiary.app.domain.model.RecordSource

@Entity(
    tableName = "weight_records",
    indices = [
        Index(value = ["measuredAt"]),
        // externalId 唯一索引是去重的基石：它挡的是「重复拉取、重叠窗口、点了两次同步」。
        // SQLite 把多个 NULL 视为互不相同 —— 所以手动记录（externalId 为 NULL）可以有很多条。
        // 这一点不是巧合，是设计前提，别"顺手"改成非空。
        Index(value = ["externalId"], unique = true),
    ],
)
data class WeightRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** epoch millis，带时分，支持一天多条 */
    val measuredAt: Long,
    val weightKg: Double,
    val bodyFatPercent: Double?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * 来源枚举名。必须声明 [ColumnInfo] 的 `defaultValue` ——
     * 迁移是用 `ALTER TABLE ADD COLUMN ... DEFAULT 'MANUAL'` 加的列，
     * 实体这边不写默认值的话，Room 的 schema 校验会对不上（开机直接崩）。
     */
    @ColumnInfo(defaultValue = "MANUAL")
    val source: String = RecordSource.MANUAL.name,
    /** Health Connect 的 `metadata.id`。只有同步来的记录非空 */
    val externalId: String? = null,
)
