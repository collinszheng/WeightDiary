package com.weightdiary.app.domain.bmi

import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BmiClassifierTest {

    private val eps = 1e-4f

    // ─────────────── 分级：中国标准 ───────────────

    @Test
    fun `中国标准分级边界`() {
        val s = BmiStandard.CHINA
        assertEquals(Level.UNDERWEIGHT, BmiClassifier.levelOf(18.49, s))
        assertEquals(Level.NORMAL, BmiClassifier.levelOf(18.5, s))
        assertEquals(Level.NORMAL, BmiClassifier.levelOf(23.9, s))
        assertEquals(Level.OVERWEIGHT, BmiClassifier.levelOf(24.0, s))
        assertEquals(Level.OVERWEIGHT, BmiClassifier.levelOf(27.9, s))
        assertEquals(Level.OBESE, BmiClassifier.levelOf(28.0, s))
    }

    @Test
    fun `24_0 归入超重而不是标准的末尾`() {
        // 这是刻意的边界归属：24.0 起算超重，与分级文案一致
        assertEquals(Level.OVERWEIGHT, BmiClassifier.levelOf(24.0, BmiStandard.CHINA))
    }

    @Test
    fun `非中国标准在 v1 中不使用但仍可用`() {
        val s = BmiStandard.WHO
        assertEquals(Level.NORMAL, BmiClassifier.levelOf(24.9, s))
        assertEquals(Level.OVERWEIGHT, BmiClassifier.levelOf(25.0, s))
        assertEquals(Level.OVERWEIGHT, BmiClassifier.levelOf(29.9, s))
        assertEquals(Level.OBESE, BmiClassifier.levelOf(30.0, s))
    }

    // ─────────────── 四色条滑块定位 ───────────────

    @Test
    fun `四段等宽 - 四个分界点应落在条的四等分处`() {
        // 15.0 → 0%（黄段起点）
        assertEquals(0.00f, BmiClassifier.sliderPosition(15.0), eps)
        // 18.5 → 25%（绿段起点）
        assertEquals(0.25f, BmiClassifier.sliderPosition(18.5), eps)
        // 24.0 → 50%（浅红段起点）
        assertEquals(0.50f, BmiClassifier.sliderPosition(24.0), eps)
        // 28.0 → 75%（深红段起点）
        assertEquals(0.75f, BmiClassifier.sliderPosition(28.0), eps)
        // 35.0 及以上 → 100%（贴最右）
        assertEquals(1.00f, BmiClassifier.sliderPosition(35.0), eps)
        assertEquals(1.00f, BmiClassifier.sliderPosition(42.0), eps)
    }

    @Test
    fun `越界值滑块贴边`() {
        assertEquals(0f, BmiClassifier.sliderPosition(10.0), eps)
        assertEquals(0f, BmiClassifier.sliderPosition(15.0), eps)
        assertEquals(1f, BmiClassifier.sliderPosition(35.0), eps)
        assertEquals(1f, BmiClassifier.sliderPosition(99.0), eps)
    }

    @Test
    fun `设计稿校验值 - BMI 22_4 应落在绿段偏右`() {
        // 绿段 (18.5..24.0)，段内 (22.4-18.5)/5.5 = 0.709
        // pos = (1 + 0.709) / 4 = 0.4273
        assertEquals(0.4273f, BmiClassifier.sliderPosition(22.4), 1e-3f)
    }

    /**
     * 不变量：**滑块位置所属的色块，必须与 BMI 分级结果一致。**
     *
     * 这条曾经出过 bug——四段改成等宽后如果仍按 BMI 线性映射，滑块会和色块完全对不上。
     */
    @Test
    fun `滑块所在色块必须与分级一致`() {
        val s = BmiStandard.CHINA
        val expectedSegment = mapOf(
            Level.UNDERWEIGHT to 0,
            Level.NORMAL to 1,
            Level.OVERWEIGHT to 2,
            Level.OBESE to 3,
        )

        var bmi = 10.0
        while (bmi <= 40.0) {
            val pos = BmiClassifier.sliderPosition(bmi, s)
            // 1f 时归入最后一段
            val segment = (pos * BmiClassifier.SEGMENT_COUNT).toInt()
                .coerceIn(0, BmiClassifier.SEGMENT_COUNT - 1)
            val level = BmiClassifier.levelOf(bmi, s)
            assertEquals(
                "BMI=$bmi 滑块落在第 $segment 段，但分级是 $level",
                expectedSegment[level],
                segment,
            )
            bmi += 0.05
        }
    }

    /**
     * M1 出口标准里点名的一组值，逐个验一遍「滑块落在哪一段」。
     *
     * 与上面的扫描测试是同一个不变量，这里用具体值再钉一次，避免以后扫描步长被改小/改大而漏掉。
     */
    @Test
    fun `M1 出口标准点名的 BMI 值 - 滑块色块与分级一致`() {
        val expected = mapOf(
            17.0 to 0, // 黄 较轻
            19.0 to 1, // 绿 标准
            22.0 to 1,
            25.0 to 2, // 浅红 超重
            29.0 to 3, // 深红 肥胖
            33.0 to 3,
        )
        expected.forEach { (bmi, segment) ->
            val pos = BmiClassifier.sliderPosition(bmi)
            val actual = (pos * BmiClassifier.SEGMENT_COUNT).toInt()
                .coerceIn(0, BmiClassifier.SEGMENT_COUNT - 1)
            assertEquals("BMI=$bmi", segment, actual)
        }
    }

    @Test
    fun `四段边界之和覆盖完整区间`() {
        val bounds = BmiClassifier.sliderBounds(BmiStandard.CHINA)
        assertEquals(4, bounds.size)
        assertEquals(BmiClassifier.SLIDER_MIN, bounds.first().first, 1e-9)
        assertEquals(BmiClassifier.SLIDER_MAX, bounds.last().second, 1e-9)
        // 相邻段必须首尾相接，不能有缝也不能重叠
        for (i in 0 until bounds.size - 1) {
            assertEquals(bounds[i].second, bounds[i + 1].first, 1e-9)
        }
    }

    @Test
    fun `滑块位置始终在 0 到 1 之间`() {
        var bmi = -50.0
        while (bmi <= 200.0) {
            val pos = BmiClassifier.sliderPosition(bmi)
            assertTrue("BMI=$bmi 产生越界位置 $pos", pos in 0f..1f)
            bmi += 0.5
        }
    }

    @Test
    fun `classify 返回一致的结果`() {
        val r = BmiClassifier.classify(22.4)
        assertEquals(Level.NORMAL, r.level)
        assertEquals(BmiStandard.CHINA, r.standard)
        assertEquals(0.4273f, r.sliderPos, 1e-3f)
    }
}
