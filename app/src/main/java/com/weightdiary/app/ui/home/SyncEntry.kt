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
