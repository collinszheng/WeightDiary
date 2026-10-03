package com.weightdiary.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weight_records",
    indices = [Index(value = ["measuredAt"])],
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
)
