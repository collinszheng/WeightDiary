package com.weightdiary.app.domain.model

/**
 * 一条记录的来源。
 *
 * 存进 Room 的是 [name] 而不是 `ordinal`（与 [BmiStandard] / [UnitSystem] 的既有做法一致）——
 * 将来往中间插一个枚举值，老数据不会错位。
 *
 * **CSV 导入回来的记录是 [MANUAL]**，不是单独一类：那份文件里没有 `externalId`，
 * 去重上也等同于手动记录（见 `docs/08` §4.1）。
 */
enum class RecordSource {

    /** 用户手动录入（含 CSV 导入） */
    MANUAL,

    /** 从 Health Connect 同步进来的 */
    HEALTH_CONNECT,
}
