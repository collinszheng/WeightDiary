package com.weightdiary.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「实验功能」开启之后到底会发生什么 —— 全部规则都在两个纯函数里，不需要设备。
 *
 * 这里守的是**顺序**与**静默**：
 *  - 缺锚点要排在授权之前（否则第一次同步会把一家人的数据吞进来，`docs/08` §6.2）；
 *  - 冷启动自动同步的前提不满足时一律静默跳过，不能弹窗打断启动。
 *
 * 这两条都是行为规则，不是实现细节 —— 改它们等于改行为。
 */
class SyncEntryTest {

    private fun available(
        granted: Boolean = true,
        needsAnchor: Boolean = false,
    ) = HealthConnectUi(
        availability = SyncAvailabilityUi.AVAILABLE,
        granted = granted,
        needsAnchor = needsAnchor,
    )

    // ─────────────── syncRowAction ───────────────

    @Test
    fun `系统不支持时判定先返回不支持 - 那种设备上整节都不渲染`() {
        val state = HealthConnectUi(availability = SyncAvailabilityUi.UNSUPPORTED)
        assertEquals(SyncRowAction.UNSUPPORTED, syncRowAction(state))
    }

    @Test
    fun `缺锚点排在授权之前 - 先让用户记一条自己的体重`() {
        // 哪怕同时还没授权也是先建锚点：没有锚点就去读 HC，
        // 时间边界与异常过滤都不存在，会把一家人的数据无差别收下
        val state = available(granted = false, needsAnchor = true)
        assertEquals(SyncRowAction.ADD_ANCHOR, syncRowAction(state))
    }

    @Test
    fun `没装或版本太老时只解释 - 不去请求权限`() {
        // 应用不在时请求权限，系统弹窗会以「应用不存在」这种看不懂的方式失败
        assertEquals(
            SyncRowAction.EXPLAIN,
            syncRowAction(HealthConnectUi(availability = SyncAvailabilityUi.NOT_INSTALLED)),
        )
        assertEquals(
            SyncRowAction.EXPLAIN,
            syncRowAction(HealthConnectUi(availability = SyncAvailabilityUi.NEEDS_UPDATE)),
        )
    }

    @Test
    fun `可用但没授权时拉起授权弹窗`() {
        assertEquals(SyncRowAction.REQUEST_PERMISSION, syncRowAction(available(granted = false)))
    }

    @Test
    fun `配好之后退化成纯信息 - 不可点也不画箭头`() {
        assertEquals(SyncRowAction.NONE, syncRowAction(available(granted = true)))
    }

    // ─────────────── canAutoSyncOnLaunch ───────────────

    @Test
    fun `四个条件都满足才自动同步`() {
        assertTrue(
            canAutoSyncOnLaunch(
                autoActive = true,
                availability = SyncAvailabilityUi.AVAILABLE,
                granted = true,
                hasAnchor = true,
            )
        )
    }

    @Test
    fun `总开关关着时不自动同步 - 开关值保留但不生效`() {
        assertFalse(canAutoSyncOnLaunch(false, SyncAvailabilityUi.AVAILABLE, true, true))
    }

    @Test
    fun `没装或没授权时静默跳过 - 不弹授权框打断启动`() {
        assertFalse(canAutoSyncOnLaunch(true, SyncAvailabilityUi.NOT_INSTALLED, true, true))
        assertFalse(canAutoSyncOnLaunch(true, SyncAvailabilityUi.NEEDS_UPDATE, true, true))
        assertFalse(canAutoSyncOnLaunch(true, SyncAvailabilityUi.AVAILABLE, false, true))
    }

    @Test
    fun `库里还没有记录时静默跳过 - 不在启动时弹引导表单`() {
        assertFalse(canAutoSyncOnLaunch(true, SyncAvailabilityUi.AVAILABLE, true, false))
    }
}
