package com.weightdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.weightdiary.app.domain.bmi.BmiCalculator
import com.weightdiary.app.domain.bmi.BmiClassifier
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ⚠️ **临时调试台**，仅用于验证 M0 的数据层，M1 开始会被真正的首页替换。
 *
 * 两个入口：
 * 1. 屏幕上的按钮
 * 2. adb 带 `--ez debugInsert true` 启动，用于脚本化验证「杀进程后数据仍在」
 */
class MainActivity : ComponentActivity() {

    private val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = (application as WeightDiaryApp).container.weightRepository

        // 脚本化写入入口
        if (intent?.getBooleanExtra(EXTRA_DEBUG_INSERT, false) == true) {
            lifecycleScope.launch {
                repo.add(
                    weightKg = nextDebugWeight(),
                    measuredAt = Instant.now(),
                    bodyFatPercent = 21.8,
                    note = "debug insert",
                )
            }
        }

        setContent {
            MaterialTheme {
                val count by repo.count.collectAsStateWithLifecycle(initialValue = -1)
                val latest by repo.latest.collectAsStateWithLifecycle(initialValue = null)
                val profile by repo.profile.collectAsStateWithLifecycle(initialValue = null)
                val scope = rememberCoroutineScope()

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("体重日记 · M0 调试台", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "⚠️ 临时页面，M1 会被真正的首页替换",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        HorizontalDivider()

                        Text("记录数：$count", style = MaterialTheme.typography.titleLarge)

                        val l = latest
                        Text(
                            if (l == null) "最新记录：无"
                            else "最新记录：${l.weightKg} kg\n" +
                                "时间：${timeFormatter.format(l.measuredAt.atZone(ZoneId.systemDefault()))}\n" +
                                "体脂：${l.bodyFatPercent ?: "--"} %\n" +
                                "备注：${l.note ?: "--"}"
                        )

                        HorizontalDivider()

                        val p = profile
                        val height = p?.heightCm
                        val bmi = if (l != null && height != null) {
                            BmiCalculator.calculate(l.weightKg, height)
                        } else null
                        Text("身高：${height ?: "未设置"}")
                        if (bmi != null) {
                            val level = BmiClassifier.classify(bmi)
                            Text("BMI：$bmi  →  ${level.level}  （滑块位置 ${"%.3f".format(level.sliderPos)}）")
                        } else {
                            Text("BMI：--（先设置身高）")
                        }

                        HorizontalDivider()

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    repo.add(
                                        weightKg = nextDebugWeight(),
                                        measuredAt = Instant.now(),
                                        bodyFatPercent = 21.8,
                                        note = "via button",
                                    )
                                }
                            },
                        ) { Text("写入一条测试记录") }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    // 首次运行时设一个身高，方便看 BMI 联动
                                    if (height == null) repo.setHeight(175.0)
                                }
                            },
                        ) { Text("设置身高 175 cm（首次用）") }
                    }
                }
            }
        }
    }

    /** 每次写入一个 60.0–75.0 之间的伪随机体重，方便肉眼区分条目 */
    private fun nextDebugWeight(): Double =
        60.0 + (System.currentTimeMillis() % 150) / 10.0

    companion object {
        const val EXTRA_DEBUG_INSERT = "debugInsert"
    }
}
