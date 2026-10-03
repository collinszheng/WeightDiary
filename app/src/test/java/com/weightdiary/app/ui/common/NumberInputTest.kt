package com.weightdiary.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberInputTest {

    @Test
    fun `只保留数字和小数点`() {
        assertEquals("68.5", NumberInput.sanitizeDecimal("68.5kg"))
        assertEquals("68.5", NumberInput.sanitizeDecimal("a6b8c.5"))
        assertEquals("", NumberInput.sanitizeDecimal("abc"))
    }

    @Test
    fun `只允许一个小数点`() {
        assertEquals("68.5", NumberInput.sanitizeDecimal("68.5.5"))
        assertEquals("68.55", NumberInput.sanitizeDecimal("68.5.5", maxDecimals = 2))
    }

    @Test
    fun `小数位受 maxDecimals 限制`() {
        assertEquals("68.5", NumberInput.sanitizeDecimal("68.567"))
        assertEquals("68.56", NumberInput.sanitizeDecimal("68.567", maxDecimals = 2))
    }

    @Test
    fun `整数位最多 4 位 - 挡住手滑多按`() {
        // 685 本身合法，但用户要是多按了两位数不该被接受成天文数字
        assertEquals("6850", NumberInput.sanitizeDecimal("685012"))
    }

    @Test
    fun `以小数点开头时补前导零`() {
        assertEquals("0.5", NumberInput.sanitizeDecimal(".5"))
    }

    @Test
    fun `前导零被规整`() {
        assertEquals("68", NumberInput.sanitizeDecimal("0068"))
    }

    @Test
    fun `删除到只剩小数点时保留可继续输入`() {
        // 用户从 "68." 继续输入小数位是正常操作，不能把点吃掉
        assertEquals("68.", NumberInput.sanitizeDecimal("68."))
    }

    @Test
    fun `空串保持为空`() {
        assertEquals("", NumberInput.sanitizeDecimal(""))
    }
}
