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
 * 默认数据集与 [docs/assets/home.png] 设计稿对齐，方便截图逐像素比对：
 * 当前 68.5 / 上一条 68.8（差值 −0.3）/ 目标 67.0（起点 71.8）/ 身高 175 / 体脂 21.8。
 *
 * ⚠️ 造数必须在 `pm clear` / `install -r` **之后隔几秒**再启动。紧接着执行的话，
 * 安装触发的进程重启会和启动抢跑，intent 里的 extra 会被丢掉 —— 表现为「跑了但一条数据都没有」。
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

    /**
     * 只造这么多条记录。负数（缺省）表示用标准数据集。
     * - `--ei debugSeedCount 1` → 验证「删掉最后一条回空状态」这类退化场景
     * - `--ei debugSeedCount 1000` → 验证长列表滚动性能
     */
    const val EXTRA_COUNT = "debugSeedCount"

    fun maybeSeed(intent: Intent?, repository: WeightRepository, scope: CoroutineScope) {
        if (!BuildConfig.DEBUG) return
        val override = intent?.getFloatExtra(EXTRA_WEIGHT, 0f)?.takeIf { it > 0f }?.toDouble()
        if (intent?.getBooleanExtra(EXTRA_SEED, false) != true) return
        val count = intent.getIntExtra(EXTRA_COUNT, -1)
        scope.launch { seedDemoData(repository, override ?: DEFAULT_WEIGHT, count) }
    }

    private const val DEFAULT_WEIGHT = 68.5

    /** 让曲线不要是一条完美直线的小扰动 */
    private val NOISE = listOf(0.0, -0.18, 0.12, -0.25, 0.2, 0.0, -0.1, 0.28)

    private suspend fun seedDemoData(
        repository: WeightRepository,
        latestWeight: Double,
        count: Int,
    ) {
        // 种子模拟的是「已经配好的用户」，否则每次造数后首次引导都会挡在前面
        repository.setHeight(175.0)
        repository.setTargetWeight(targetWeightKg = 67.0, setAtWeightKg = 71.8)
        repository.setOnboardingCompleted(true)

        val now = Instant.now()

        // 显式指定条数：每天一条往回铺，用于退化场景与长列表压测
        if (count >= 0) {
            repeat(count) { i ->
                repository.add(
                    weightKg = latestWeight + i * 0.02 + NOISE[i % NOISE.size],
                    measuredAt = now.minus(i.toLong(), ChronoUnit.DAYS),
                    bodyFatPercent = 21.8,
                    note = "seed",
                )
            }
            return
        }

        // 标准数据集
        val head = listOf(
            Triple(latestWeight, 0L, 21.8),
            Triple(68.8, 1L, 22.0),
            Triple(68.0, 2L, 21.6),
        )
        head.forEach { (kg, daysAgo, fat) ->
            repository.add(
                weightKg = kg,
                measuredAt = now.minus(daysAgo, ChronoUnit.DAYS),
                bodyFatPercent = fat,
                note = "seed",
            )
        }

        // 再往前铺 110 天的趋势数据，并与前三条接得上（否则图上会出现一个假的暴跌）。
        // 铺到 110 天是有意的：超过 120 天「总」视图就会切到按月聚合，反而没几个单位、滚不动了。
        // 中间故意留几处缺口，用来验证「跨空缺仍是实线」。
        val gapDays = setOf(9L, 10L, 22L, 47L, 48L, 49L, 73L, 90L, 91L)
        var index = 0
        for (day in 3L..110L) {
            if (day !in gapDays) {
                repository.add(
                    weightKg = 68.0 + (day - 2) * 0.035 + NOISE[index % NOISE.size],
                    measuredAt = now.minus(day, ChronoUnit.DAYS),
                    bodyFatPercent = (22.0 - day * 0.02).coerceAtLeast(15.0),
                    note = "seed",
                )
            }
            index++
        }
    }
}
