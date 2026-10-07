package com.weightdiary.app.data.healthconnect

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.weightdiary.app.domain.sync.RawBodyFat
import com.weightdiary.app.domain.sync.RawWeight
import java.time.Instant

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

/** 一次拉取的原始记录。配对是 domain 层的事，这里只负责把 HC 的形状翻译成我们的形状 */
data class PulledRawRecords(
    val weights: List<RawWeight>,
    val bodyFats: List<RawBodyFat>,
)

/**
 * Health Connect 的适配层。
 *
 * 照 `data/backup/RecordBackup` 的先例：**碰 SDK 的代码放 data 层**，
 * 判断逻辑（配对、去重、过滤）全在 `domain/sync`，那样才能 JVM 单测。
 *
 * 这里只读、不写 —— 本期是单向拉取（`docs/08` §5）。
 */
class HealthConnectSource(private val context: Context) {

    companion object {
        /**
         * 只请求读体重与体脂率。
         *
         * 刻意用 `HealthPermission.getReadPermission(...)` 而不是手写字符串 ——
         * 权限名由库自己推导，不会因为记错而**静默失效**。
         * 实际推导出来是 `android.permission.health.READ_WEIGHT` 和 `READ_BODY_FAT`
         * （已从 connect-client 1.1.0 的 AAR 里提取确认），清单里那两条必须与之一致。
         *
         * 不申请 `READ_HEALTH_DATA_HISTORY` / `READ_HEALTH_DATA_IN_BACKGROUND`：
         * 前者要走 Play 审批，后者要用户另外去系统里开（`docs/08` §5）。
         */
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(BodyFatRecord::class),
        )

        /** 一次读取最多翻几页，防上游异常导致死循环 */
        private const val MAX_PAGES = 20
    }

    fun availability(): HealthConnectAvailability = when {
        // 先自己卡版本：HC 要 API 28，而本项目的 minSdk 是 26
        Build.VERSION.SDK_INT < 28 -> HealthConnectAvailability.UNSUPPORTED

        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE ->
            HealthConnectAvailability.AVAILABLE

        HealthConnectClient.getSdkStatus(context) ==
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
            HealthConnectAvailability.NEEDS_UPDATE

        else -> HealthConnectAvailability.NOT_INSTALLED
    }

    suspend fun grantedPermissions(): Set<String> =
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    /**
     * 读 `[from, to]` 区间内的体重与体脂率。
     *
     * 权限不足时会抛 `SecurityException` —— 由调用方转成「未授权」状态，这里不吞。
     */
    suspend fun read(from: Instant, to: Instant): PulledRawRecords {
        val client = HealthConnectClient.getOrCreate(context)
        return PulledRawRecords(
            weights = readWeights(client, from, to),
            bodyFats = readBodyFats(client, from, to),
        )
    }

    private suspend fun readWeights(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): List<RawWeight> {
        val out = ArrayList<RawWeight>()
        var token: String? = null
        var page = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = token,
                )
            )
            response.records.forEach { record ->
                // id 为空的记录不能要：它不是唯一的，塞进唯一索引会让后面所有记录
                // 都撞在同一个空串上被静默丢掉
                val id = record.metadata.id
                if (id.isNotBlank()) {
                    out += RawWeight(
                        externalId = id,
                        measuredAt = record.time,
                        weightKg = record.weight.inKilograms,
                    )
                }
            }
            token = response.pageToken
            page++
        } while (token != null && page < MAX_PAGES)
        return out
    }

    private suspend fun readBodyFats(
        client: HealthConnectClient,
        from: Instant,
        to: Instant,
    ): List<RawBodyFat> {
        val out = ArrayList<RawBodyFat>()
        var token: String? = null
        var page = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = BodyFatRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, to),
                    pageToken = token,
                )
            )
            response.records.forEach { record ->
                val id = record.metadata.id
                if (id.isNotBlank()) {
                    out += RawBodyFat(
                        externalId = id,
                        measuredAt = record.time,
                        percent = record.percentage.value,
                    )
                }
            }
            token = response.pageToken
            page++
        } while (token != null && page < MAX_PAGES)
        return out
    }
}
