package com.weightdiary.app.ui.common

/**
 * 数字输入的清洗。纯函数，可单测。
 *
 * 做三件事：只留数字与小数点、只允许一个小数点、限制整数位与小数位。
 * 放在输入侧而不是保存时校验，用户就不会先看到非法值再被打回。
 */
object NumberInput {

    private const val MAX_INT_DIGITS = 4

    /**
     * @param maxDecimals 小数位上限。体重与体脂都是 1 位
     */
    fun sanitizeDecimal(raw: String, maxDecimals: Int = 1): String {
        val filtered = raw.filter { it.isDigit() || it == '.' }
        if (filtered.isEmpty()) return ""

        val firstDot = filtered.indexOf('.')
        if (firstDot < 0) {
            return normalizeInteger(filtered.take(MAX_INT_DIGITS))
        }

        val intPart = normalizeInteger(filtered.substring(0, firstDot).take(MAX_INT_DIGITS))
        val decPart = filtered.substring(firstDot + 1).filter { it.isDigit() }.take(maxDecimals)
        return "$intPart.$decPart"
    }

    /** 去掉前导零，但保留单独一个 `0`；空串也补成 `0` */
    private fun normalizeInteger(digits: String): String =
        digits.trimStart('0').ifEmpty { "0" }
}
