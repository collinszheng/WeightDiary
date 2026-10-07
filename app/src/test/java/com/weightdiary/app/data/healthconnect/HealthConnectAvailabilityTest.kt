package com.weightdiary.app.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * API 28 门槛（`docs/06` §2.2）。
 *
 * 这条门槛是「设置页那一行显不显示」的唯一依据：低于 28 必须**整行藏掉**，
 * 否则用户看到一个永远可点、永远失败的按钮。
 *
 * 手边的模拟器是 API 37，覆盖不到 28 以下，所以门槛被抽成纯函数在这里钉边界。
 */
class HealthConnectAvailabilityTest {

    // ─────────────── 版本门槛 ───────────────

    @Test
    fun `API 26 一律不支持 - 本项目的 minSdk 就是 26`() {
        assertEquals(
            HealthConnectAvailability.UNSUPPORTED,
            healthConnectAvailabilityFor(sdkInt = 26, sdkStatus = HealthConnectClient.SDK_AVAILABLE),
        )
    }

    @Test
    fun `API 27 也不支持 - 门槛是 28 而不是 27`() {
        assertEquals(
            HealthConnectAvailability.UNSUPPORTED,
            healthConnectAvailabilityFor(sdkInt = 27, sdkStatus = HealthConnectClient.SDK_AVAILABLE),
        )
    }

    /** 版本不够时**不看** SDK 状态 —— 先卡版本，别让用户看到查不到原因的失败 */
    @Test
    fun `版本不够时忽略 SDK 状态 - 哪怕它说是可用的`() {
        assertEquals(
            HealthConnectAvailability.UNSUPPORTED,
            healthConnectAvailabilityFor(
                sdkInt = 27,
                sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
            ),
        )
    }

    @Test
    fun `API 28 就是门槛本身 - 从这里开始才看 SDK 状态`() {
        assertEquals(
            HealthConnectAvailability.AVAILABLE,
            healthConnectAvailabilityFor(sdkInt = 28, sdkStatus = HealthConnectClient.SDK_AVAILABLE),
        )
    }

    // ─────────────── SDK 状态 ───────────────

    @Test
    fun `装了但版本太老 → 提示更新`() {
        assertEquals(
            HealthConnectAvailability.NEEDS_UPDATE,
            healthConnectAvailabilityFor(
                sdkInt = 33,
                sdkStatus = HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED,
            ),
        )
    }

    /** Android 9–13 上 HC 是要用户自己装的，所以这是常态而不是异常 */
    @Test
    fun `没装 → 提示去装`() {
        assertEquals(
            HealthConnectAvailability.NOT_INSTALLED,
            healthConnectAvailabilityFor(
                sdkInt = 30,
                sdkStatus = HealthConnectClient.SDK_UNAVAILABLE,
            ),
        )
    }

    @Test
    fun `Android 14 及以上系统内置 - 直接就可用`() {
        assertEquals(
            HealthConnectAvailability.AVAILABLE,
            healthConnectAvailabilityFor(sdkInt = 34, sdkStatus = HealthConnectClient.SDK_AVAILABLE),
        )
    }
}
