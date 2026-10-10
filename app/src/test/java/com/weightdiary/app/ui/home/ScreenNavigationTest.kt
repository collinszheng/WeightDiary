package com.weightdiary.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 导航的两条纯规则：返回键逐级回退、切换动画按层级定方向。
 *
 * 规则本身很简单，但**只靠手点很容易漏页**：设置现在有四页，
 * 漏掉一页的表现是「返回键直接退出 App」，动画方向错了则是「退回时反着滑」，
 * 都属要点两下才发现的毛病。
 */
class ScreenNavigationTest {

    private val settingsPages = listOf(
        Screen.SETTINGS_BMI,
        Screen.SETTINGS_DATA,
        Screen.SETTINGS_EXPERIMENTAL,
        Screen.SETTINGS_ABOUT,
    )

    @Test
    fun `设置主页的返回键回首页`() {
        assertEquals(Screen.HOME, backTarget(Screen.SETTINGS))
    }

    @Test
    fun `每个子页的返回键都回设置主页`() {
        settingsPages.forEach { page ->
            assertEquals(page.name, Screen.SETTINGS, backTarget(page))
        }
    }

    @Test
    fun `首页的返回键不往别处去`() {
        // 首页按返回键是退出 App（由 BackHandler 的 enabled 决定），
        // 所以这里只需要保证它不会退到某个设置页
        assertEquals(Screen.HOME, backTarget(Screen.HOME))
    }

    @Test
    fun `层级只分三档：首页 设置主页 子页`() {
        assertEquals(0, screenDepth(Screen.HOME))
        assertEquals(1, screenDepth(Screen.SETTINGS))
        settingsPages.forEach { page ->
            assertEquals(page.name, 2, screenDepth(page))
        }
    }

    @Test
    fun `往里走的层级一定变大`() {
        settingsPages.forEach { page ->
            assertEquals(page.name, true, screenDepth(page) > screenDepth(Screen.SETTINGS))
        }
    }
}
