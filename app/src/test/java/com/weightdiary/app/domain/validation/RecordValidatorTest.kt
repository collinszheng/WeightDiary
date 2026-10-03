package com.weightdiary.app.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecordValidatorTest {

    // ─────────────── 体重：必填 ───────────────

    @Test
    fun `体重留空报必填`() {
        assertEquals(
            ValidationError.Required(Field.WEIGHT),
            RecordValidator.validateWeight(null),
        )
    }

    @Test
    fun `体重区间边界`() {
        assertNull(RecordValidator.validateWeight(20.0))
        assertNull(RecordValidator.validateWeight(500.0))
        assertNull(RecordValidator.validateWeight(68.5))

        assertEquals(
            ValidationError.OutOfRange(Field.WEIGHT, RecordValidator.WEIGHT_KG),
            RecordValidator.validateWeight(19.9),
        )
        assertEquals(
            ValidationError.OutOfRange(Field.WEIGHT, RecordValidator.WEIGHT_KG),
            RecordValidator.validateWeight(500.1),
        )
    }

    @Test
    fun `体重 685 被拒绝 - 挡住手滑`() {
        assertEquals(
            ValidationError.OutOfRange(Field.WEIGHT, RecordValidator.WEIGHT_KG),
            RecordValidator.validateWeight(685.0),
        )
    }

    @Test
    fun `体重非有限值被拒绝`() {
        assertEquals(
            ValidationError.Required(Field.WEIGHT),
            RecordValidator.validateWeight(Double.NaN),
        )
        assertEquals(
            ValidationError.Required(Field.WEIGHT),
            RecordValidator.validateWeight(Double.POSITIVE_INFINITY),
        )
    }

    // ─────────────── 体脂率：选填 ───────────────

    @Test
    fun `体脂率留空合法`() {
        assertNull(RecordValidator.validateBodyFat(null))
    }

    @Test
    fun `体脂率区间边界`() {
        assertNull(RecordValidator.validateBodyFat(1.0))
        assertNull(RecordValidator.validateBodyFat(75.0))
        assertEquals(
            ValidationError.OutOfRange(Field.BODY_FAT, RecordValidator.BODY_FAT_PERCENT),
            RecordValidator.validateBodyFat(0.9),
        )
        assertEquals(
            ValidationError.OutOfRange(Field.BODY_FAT, RecordValidator.BODY_FAT_PERCENT),
            RecordValidator.validateBodyFat(75.1),
        )
    }

    // ─────────────── 身高：选填 ───────────────

    @Test
    fun `身高留空合法`() {
        assertNull(RecordValidator.validateHeight(null))
    }

    @Test
    fun `身高区间边界`() {
        assertNull(RecordValidator.validateHeight(50.0))
        assertNull(RecordValidator.validateHeight(260.0))
        assertNull(RecordValidator.validateHeight(175.0))
        assertEquals(
            ValidationError.OutOfRange(Field.HEIGHT, RecordValidator.HEIGHT_CM),
            RecordValidator.validateHeight(49.9),
        )
        assertEquals(
            ValidationError.OutOfRange(Field.HEIGHT, RecordValidator.HEIGHT_CM),
            RecordValidator.validateHeight(260.1),
        )
    }

    // ─────────────── 目标体重 ───────────────

    @Test
    fun `目标体重留空合法`() {
        assertNull(RecordValidator.validateTargetWeight(null))
    }

    @Test
    fun `目标体重越界被拒绝`() {
        assertNull(RecordValidator.validateTargetWeight(65.0))
        assertEquals(
            ValidationError.OutOfRange(Field.WEIGHT, RecordValidator.WEIGHT_KG),
            RecordValidator.validateTargetWeight(10.0),
        )
    }

    // ─────────────── 未来时间 ───────────────

    @Test
    fun `未来时间被拒绝`() {
        val now = Instant.parse("2026-06-30T12:00:00Z")
        assertEquals(
            ValidationError.FutureDate,
            RecordValidator.validateMeasuredAt(now.plusSeconds(3600), now),
        )
    }

    @Test
    fun `当前时刻与过去时刻都合法`() {
        val now = Instant.parse("2026-06-30T12:00:00Z")
        assertNull(RecordValidator.validateMeasuredAt(now, now))
        assertNull(RecordValidator.validateMeasuredAt(now.minusSeconds(86400), now))
    }

    @Test
    fun `留 60 秒容差 - 避免跨分钟的假失败`() {
        val now = Instant.parse("2026-06-30T12:00:00Z")
        assertNull(RecordValidator.validateMeasuredAt(now.plusSeconds(30), now))
        assertEquals(
            ValidationError.FutureDate,
            RecordValidator.validateMeasuredAt(now.plusSeconds(120), now),
        )
    }
}
