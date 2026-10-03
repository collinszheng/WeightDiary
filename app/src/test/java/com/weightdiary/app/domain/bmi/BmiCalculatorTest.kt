package com.weightdiary.app.domain.bmi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BmiCalculatorTest {

    @Test
    fun `标准用例 - 68_5kg 175cm`() {
        // 68.5 / 1.75^2 = 22.367... → 22.4
        assertEquals(22.4, BmiCalculator.calculate(68.5, 175.0)!!, 1e-9)
    }

    @Test
    fun `四舍五入到 1 位小数`() {
        // 70.0 / 1.75^2 = 22.857... → 22.9
        assertEquals(22.9, BmiCalculator.calculate(70.0, 175.0)!!, 1e-9)
        // 60.0 / 1.75^2 = 19.591... → 19.6
        assertEquals(19.6, BmiCalculator.calculate(60.0, 175.0)!!, 1e-9)
    }

    @Test
    fun `身高非法时返回 null`() {
        assertNull(BmiCalculator.calculate(68.5, 0.0))
        assertNull(BmiCalculator.calculate(68.5, -10.0))
    }

    @Test
    fun `体重非法时返回 null`() {
        assertNull(BmiCalculator.calculate(0.0, 175.0))
        assertNull(BmiCalculator.calculate(-1.0, 175.0))
    }

    @Test
    fun `非有限值返回 null`() {
        assertNull(BmiCalculator.calculate(Double.NaN, 175.0))
        assertNull(BmiCalculator.calculate(68.5, Double.NaN))
        assertNull(BmiCalculator.calculate(Double.POSITIVE_INFINITY, 175.0))
        assertNull(BmiCalculator.calculate(68.5, Double.POSITIVE_INFINITY))
    }
}
