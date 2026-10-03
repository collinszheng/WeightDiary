package com.weightdiary.app

import android.content.Intent
import com.weightdiary.app.data.repository.WeightRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * ⚠️ **开发期造数工具**，只在 debug 构建里生效（[BuildConfig.DEBUG] 守卫）。
 *
 * 用法：
 * ```
 * adb shell am start -n com.weightdiary.app/.MainActivity --ez debugSeed true
 * ```
 *
 * 数据与 [docs/assets/home.png] 设计稿对齐，方便截图逐像素比对：
 * 当前 68.5 / 上一条 68.8（差值 −0.3）/ 目标 67.0（起点 71.8）/ 身高 175 / 体脂 21.8。
 *
 * TODO(M5): 换成 `androidTest` 的 instrumented test，把这段从 main 源码集里挪走。
 */
object DebugSeed {

    const val EXTRA_SEED = "debugSeed"

    /**
     * 覆盖最新一条的体重，用于验证「大号数字是等宽字形」：
     * 18.5 与 68.5 都是 3 位数字 + 小数点，若字形等宽则两者墨迹总宽应当一致；
     * 若用了比例字形，'1' 明显更窄，总宽会偏小。
     */
    const val EXTRA_WEIGHT = "debugWeight"

    fun maybeSeed(intent: Intent?, repository: WeightRepository, scope: CoroutineScope) {
        if (!BuildConfig.DEBUG) return
        val override = intent?.getFloatExtra(EXTRA_WEIGHT, 0f)?.takeIf { it > 0f }?.toDouble()
        if (intent?.getBooleanExtra(EXTRA_SEED, false) != true) return
        scope.launch { seedDemoData(repository, override ?: DEFAULT_WEIGHT) }
    }

    private const val DEFAULT_WEIGHT = 68.5

    private suspend fun seedDemoData(repository: WeightRepository, latestWeight: Double) {
        repository.setHeight(175.0)
        repository.setTargetWeight(targetWeightKg = 67.0, setAtWeightKg = 71.8)
        // 种子模拟的是「已经配好的用户」，否则每次造数后首次引导都会挡在前面
        repository.setOnboardingCompleted(true)

        val now = Instant.now()
        // repository.records 按时间倒序，所以最新的一条决定概览卡片与"当前体重"
        repository.add(weightKg = latestWeight, measuredAt = now, bodyFatPercent = 21.8, note = "seed")
        repository.add(
            weightKg = 68.8,
            measuredAt = now.minus(1, ChronoUnit.DAYS),
            bodyFatPercent = 22.0,
            note = "seed",
        )
        repository.add(
            weightKg = 68.0,
            measuredAt = now.minus(2, ChronoUnit.DAYS),
            bodyFatPercent = 21.6,
            note = "seed",
        )
        // 再往前铺 30 天的趋势数据，并与前三条接得上（否则图上会出现一个假的暴跌）。
        // 中间故意留两处缺口，用来验证「跨空缺仍是实线」。
        val noise = listOf(0.0, -0.18, 0.12, -0.25, 0.2, 0.0, -0.1, 0.28)
        val gapDays = setOf(9L, 10L, 22L)
        var index = 0
        for (day in 3L..30L) {
            if (day !in gapDays) {
                repository.add(
                    weightKg = 68.0 + (day - 2) * 0.13 + noise[index % noise.size],
                    measuredAt = now.minus(day, ChronoUnit.DAYS),
                    bodyFatPercent = (22.0 - day * 0.04).coerceAtLeast(15.0),
                    note = "seed",
                )
            }
            index++
        }
    }
}
