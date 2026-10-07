package com.weightdiary.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户删掉的、**同步来的**记录的外部 id 墓碑。
 *
 * 没有它的话，下次同步会把删掉的记录又拉回来（`docs/06` §7.3）。
 * 只对手动记录无效 —— 手动记录没有 externalId，也不会被同步覆盖。
 *
 * 设置页「清空所有数据」会**一并清掉**这张表（连同记录一起清空才算"重来"）。
 */
@Entity(tableName = "ignored_external_ids")
data class IgnoredExternalIdEntity(
    @PrimaryKey val externalId: String,
    /** 记下什么时候删的，只为排查用 */
    val ignoredAt: Long,
)
