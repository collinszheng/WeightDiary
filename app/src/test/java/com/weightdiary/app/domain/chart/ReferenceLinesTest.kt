package com.weightdiary.app.domain.chart

import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.model.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BMI 阈值线。
 *
 * 两条容易写错的规则：
 * 1. **单位换算** —— 身高在数据里是 cm，BMI 公式要 m
 * 2. **视野过滤** —— 落在窗口外的一律不画，绝不为了画它去撑大坐标轴
 */
class ReferenceLinesTest {

    /** 175cm → 身高² = 3.0625 m²。中国标准下三条阈值换算成体重：56.66 / 73.50 / 85.75 */
    private val height175 = 175.0

    private fun axis(lower: Double, upper: Double) =
        YAxis(lower = lower, upper = upper, majorStep = 1.0, subdivisions = 4)

    @Test
    fun `身高换算用的是米而不是厘米`() {
        // 若误用 cm²(30625)，阈值会变成 56 万 kg 这种离谱数字
        val lines = ReferenceLines.visibleIn(axis(50.0, 90.0), height175)
        assertEquals(3, lines.size)
        assertEquals(56.66, lines[0].value, 0.01)
        assertEquals(73.50, lines[1].value, 0.01)
        assertEquals(85.75, lines[2].value, 0.01)
    }

    @Test
    fun `每条阈值标注它开启的那一档`() {
        val lines = ReferenceLines.visibleIn(axis(50.0, 90.0), height175)
        assertEquals(
            listOf(Level.NORMAL, Level.OVERWEIGHT, Level.OBESE),
            lines.map { it.opensLevel },
        )
    }

    @Test
    fun `窗口外的阈值不画`() {
        // 体重在 68 附近、窗口 66–69，最近的「超重」在 73.5 —— 一条都不该出现
        val lines = ReferenceLines.visibleIn(axis(66.0, 69.0), height175)
        assertTrue("窗口里不该有任何阈值线，实际 $lines", lines.isEmpty())
    }

    @Test
    fun `贴近超重临界时只出现超重那条`() {
        // 体重 73 附近 → 窗口 72–75，只有 73.50 落进来
        val lines = ReferenceLines.visibleIn(axis(72.0, 75.0), height175)
        assertEquals(1, lines.size)
        assertEquals(Level.OVERWEIGHT, lines[0].opensLevel)
        assertEquals(73.50, lines[0].value, 0.01)
    }

    @Test
    fun `总视图的宽窗口能同时露出多条`() {
        val lines = ReferenceLines.visibleIn(axis(60.0, 90.0), height175)
        assertEquals(2, lines.size)
        assertEquals(listOf(Level.OVERWEIGHT, Level.OBESE), lines.map { it.opensLevel })
    }

    @Test
    fun `没有身高时不画 - 宁可功能不出现也不能画错位置`() {
        assertTrue(ReferenceLines.visibleIn(axis(50.0, 90.0), null).isEmpty())
        assertTrue(ReferenceLines.visibleIn(axis(50.0, 90.0), 0.0).isEmpty())
        assertTrue(ReferenceLines.visibleIn(axis(50.0, 90.0), -170.0).isEmpty())
    }

    @Test
    fun `WHO 标准的阈值与中国标准不同`() {
        val china = ReferenceLines.visibleIn(axis(50.0, 100.0), height175, BmiStandard.CHINA)
        val who = ReferenceLines.visibleIn(axis(50.0, 100.0), height175, BmiStandard.WHO)
        assertEquals(73.50, china[1].value, 0.01)
        // WHO 的「超重」从 25.0 起算 → 25.0 × 3.0625 = 76.56
        assertEquals(76.56, who[1].value, 0.01)
    }

    @Test
    fun `边界值本身不算落在窗口内`() {
        // 恰好在窗口下沿的线会被折线本身盖住，也画不出标签，所以不画
        val lines = ReferenceLines.visibleIn(axis(73.50, 80.0), height175)
        assertTrue(lines.none { it.opensLevel == Level.OVERWEIGHT })
    }
}
