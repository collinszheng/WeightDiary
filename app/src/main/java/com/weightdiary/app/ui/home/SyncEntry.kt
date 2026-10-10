package com.weightdiary.app.ui.home

/**
 * 点设置页「体脂秤同步」那一行会做什么。
 *
 * 发起同步的入口**只有两个**：首页顶栏的按钮（手动同步）和冷启动（自动同步），
 * 两者都由「实验功能」里的开关控制。这一行只负责把功能**配好** —— 授权、装 HC、建锚点。
 * 所以这里没有「立刻同步」这种动作：有开关在，行内再藏一个同步入口只会让人分不清哪个算数。
 */
enum class SyncRowAction {
    /** 已经配好了。这一行退化成纯信息，不再可点 —— 挂着箭头却点不动比没有箭头更糟 */
    NONE,

    /** API < 28：整节根本不渲染。这里只是为了判定在那种设备上也是自洽的 */
    UNSUPPORTED,

    /** 库里还没有手动记录，先引导记一条（它同时是时间边界与异常过滤的锚点） */
    ADD_ANCHOR,

    /** 本机没装「健康数据共享」或版本太老：只解释，不发请求 */
    EXPLAIN,

    /** 还没授权：拉起 Health Connect 自己的授权弹窗 */
    REQUEST_PERMISSION,
}

/**
 * **判定的顺序是规则的一部分**，不是随手排的。
 *
 * 缺锚点排在最前：库里一条记录都没有时，时间边界和异常过滤的锚点都不存在，
 * 这时候去读 HC 会把一家人的数据无差别吞进来（`docs/08` §6.2）。
 * 所以哪怕同时还没授权，也要先让用户记一条自己的体重。
 */
fun syncRowAction(state: HealthConnectUi): SyncRowAction = when {
    state.availability == SyncAvailabilityUi.UNSUPPORTED -> SyncRowAction.UNSUPPORTED
    state.needsAnchor -> SyncRowAction.ADD_ANCHOR
    state.availability != SyncAvailabilityUi.AVAILABLE -> SyncRowAction.EXPLAIN
    !state.granted -> SyncRowAction.REQUEST_PERMISSION
    else -> SyncRowAction.NONE
}

/**
 * 冷启动的自动同步要不要真的跑。
 *
 * 四个条件缺一不可，而且**一律静默**：启动第一屏弹授权框、或弹「先记一条体重」的表单
 * 都很烦人。这两个前提不满足时不报错、不提示，用户想同步时手动点一下，
 * 或者下次启动再说 —— 这是明确要求的行为。
 *
 * @param hasAnchor 库里已有至少一条手动记录（`needsAnchor` 的取反）
 */
fun canAutoSyncOnLaunch(
    autoActive: Boolean,
    availability: SyncAvailabilityUi,
    granted: Boolean,
    hasAnchor: Boolean,
): Boolean =
    autoActive &&
        availability == SyncAvailabilityUi.AVAILABLE &&
        granted &&
        hasAnchor

/**
 * 同步时图标转一圈的时长，**同时也是「正在同步」最短持续时长**。
 *
 * 两者必须相等，这不是巧合：图标转一圈刚好回到原来的角度，所以只要让「正在同步」
 * 正好持续**整数圈**，收尾时就与静止态无缝接上。转不到一圈就被拽回原位，看起来就是
 * 一次突兀的抽动 —— 试过 900ms 一圈配 450ms 时长，正是转半圈后猛地弹回去。
 */
const val SYNC_SPIN_TURN_MS = 450L

/**
 * 「正在同步」应该持续多久：把实际耗时**向上补足到整数圈**，且至少一圈。
 *
 * 例（一圈 = 450ms）：0 → 450；30 → 450；450 → 450；451 → 900；900 → 900。
 * 慢的同步（不止一圈）多等的是**当前这一圈的剩余部分**，最多 450ms。
 *
 * @param spentMs 同步实际花掉的毫秒数
 */
fun syncFeedbackMs(spentMs: Long): Long {
    val turns = ((spentMs + SYNC_SPIN_TURN_MS - 1) / SYNC_SPIN_TURN_MS).coerceAtLeast(1L)
    return turns * SYNC_SPIN_TURN_MS
}
