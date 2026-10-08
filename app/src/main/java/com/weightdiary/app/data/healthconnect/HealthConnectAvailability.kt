package com.weightdiary.app.data.healthconnect

import androidx.health.connect.client.HealthConnectClient

/** Health Connect 在这台设备上的可用状态。UI 按它决定显示什么（见 `docs/08` §6.3） */
enum class HealthConnectAvailability {
    /** 能用 */
    AVAILABLE,

    /** 装了但版本太老，要用户去更新 */
    NEEDS_UPDATE,

    /** 没装。Android 9–13 需要用户自己从应用商店装一个 */
    NOT_INSTALLED,

    /** 系统版本不够：HC 要 API 28，本项目 minSdk 是 26（`docs/06` §2.2） */
    UNSUPPORTED,
}

/**
 * HC 要求的最低 API level。
 *
 * 官方原句见 `docs/06` §2.2：「Health Connect requires a mobile device running Android 9
 * (API 28) or higher」。本项目 minSdk 保持 26 不动，靠这个门槛在 8.0 / 8.1 上
 * **把设置页那一行整行藏掉** —— 否则用户会看到一个永远可点、永远失败的按钮。
 */
const val HEALTH_CONNECT_MIN_SDK = 28

/**
 * 把「系统版本 + SDK 状态」映射成可用性。
 *
 * **刻意抽成纯函数**：这个门槛是 `docs/06` §2.2 那条硬约束的落地点，而手边的模拟器是
 * API 37，测不到 28 以下。抽出来就能用 JVM 单测把 26 / 27 / 28 的边界钉死，
 * 比去下一张 API 27 的系统镜像便宜得多。
 *
 * @param sdkInt `Build.VERSION.SDK_INT`
 * @param sdkStatus `HealthConnectClient.SDK_*` 之一
 */
fun healthConnectAvailabilityFor(sdkInt: Int, sdkStatus: Int): HealthConnectAvailability = when {
    // 先自己卡版本：HC 的库 minSdk 是 26（能从 AAR 里读到），所以这里不卡的话
    // 在 8.0/8.1 上会一路走到「未安装」，用户看到的是一个查不到原因的失败
    sdkInt < HEALTH_CONNECT_MIN_SDK -> HealthConnectAvailability.UNSUPPORTED

    sdkStatus == HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE

    sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
        HealthConnectAvailability.NEEDS_UPDATE

    else -> HealthConnectAvailability.NOT_INSTALLED
}
