package com.weightdiary.app.domain.sync

import com.weightdiary.app.domain.model.RecordSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

class SyncPlannerTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    /** 「现在」固定在 10-10 12:00，好让未来时间的用例是确定的 */
    private val now = at("2026-10-10T12:00:00+08:00")

    private var nextId = 1L

    private fun at(text: String): Instant = OffsetDateTime.parse(text).toInstant()

    private fun manual(text: String, kg: Double, externalId: String? = null) =
        ExistingRecord(nextId++, at(text), kg, externalId, RecordSource.MANUAL)

    private fun synced(text: String, kg: Double, externalId: String) =
        ExistingRecord(nextId++, at(text), kg, externalId, RecordSource.HEALTH_CONNECT)

    private fun pulled(text: String, kg: Double, id: String, fat: Double? = null) =
        PulledMeasurement(id, at(text), kg, fat)

    private fun plan(
        existing: List<ExistingRecord> = emptyList(),
        ignored: Set<String> = emptySet(),
        pulled: List<PulledMeasurement>,
    ): SyncPlan = SyncPlanner.plan(existing, ignored, pulled, zone, now)

    // ─────────────── 时间边界（一刀切） ───────────────

    @Test
    fun `早于最后一条手动记录的一律丢弃 - 这是去重的第一道闸`() {
        val result = plan(
            existing = listOf(manual("2026-10-05T22:00:00+08:00", 78.0)),
            pulled = listOf(
                pulled("2026-10-05T07:00:00+08:00", 78.0, "a"),
                pulled("2026-10-06T07:48:00+08:00", 77.9, "b"),
            ),
        )

        assertEquals(listOf("b"), result.inserts.map { it.externalId })
        assertEquals(1, result.skipped[SkipReason.BOUNDARY])
    }

    @Test
    fun `边界只认手动记录 - 库里全是同步记录时不设边界`() {
        val result = plan(
            existing = listOf(synced("2026-10-01T07:00:00+08:00", 68.0, "old")),
            pulled = listOf(pulled("2026-10-02T07:00:00+08:00", 68.2, "new")),
        )

        assertEquals(listOf("new"), result.inserts.map { it.externalId })
        assertTrue(result.skipped.isEmpty())
    }

    // ─────────────── 幂等 / 墓碑 ───────────────

    @Test
    fun `externalId 已在库里就跳过 - 重复拉取不能变成重复行`() {
        val result = plan(
            existing = listOf(
                manual("2026-10-01T22:00:00+08:00", 68.0),
                synced("2026-10-02T07:00:00+08:00", 68.1, "hc-1"),
            ),
            pulled = listOf(pulled("2026-10-02T07:00:00+08:00", 68.1, "hc-1")),
        )

        assertTrue("不该新增", result.inserts.isEmpty())
        assertEquals(1, result.skipped[SkipReason.DUPLICATE])
    }

    @Test
    fun `墓碑里的 externalId 跳过 - 用户删掉的记录不该复活`() {
        val result = plan(
            existing = listOf(manual("2026-10-01T22:00:00+08:00", 68.0)),
            ignored = setOf("hc-del"),
            pulled = listOf(pulled("2026-10-02T07:00:00+08:00", 68.1, "hc-del")),
        )

        assertTrue(result.inserts.isEmpty())
        assertEquals(1, result.skipped[SkipReason.TOMBSTONED])
    }

    // ─────────────── 邻近认领 ───────────────

    @Test
    fun `同一本地日且体重相同就认领 - 不新增行而是把外部 id 挂到手动记录上`() {
        val existing = manual("2026-10-03T07:00:00+08:00", 68.5)
        val result = plan(
            existing = listOf(existing),
            pulled = listOf(pulled("2026-10-03T07:45:00+08:00", 68.5, "hc-1")),
        )

        assertTrue("认领不该新增行", result.inserts.isEmpty())
        assertEquals(listOf(Claim(existing.id, "hc-1")), result.claims)
    }

    @Test
    fun `同一天但体重差得远是两次真实称重 - 要收下`() {
        val result = plan(
            existing = listOf(manual("2026-10-03T07:00:00+08:00", 68.5)),
            pulled = listOf(pulled("2026-10-03T20:00:00+08:00", 68.9, "hc-1")),
        )

        assertEquals(listOf("hc-1"), result.inserts.map { it.externalId })
        assertTrue(result.claims.isEmpty())
    }

    @Test
    fun `同一天同重但那条已经挂过别的外部 id - 判重而不是抢过来`() {
        val result = plan(
            existing = listOf(synced("2026-10-03T07:00:00+08:00", 68.5, "hc-A")),
            pulled = listOf(pulled("2026-10-03T07:45:00+08:00", 68.5, "hc-B")),
        )

        assertTrue(result.inserts.isEmpty())
        assertTrue("不能覆盖已有的外部 id，否则下次同步会重复导入旧的", result.claims.isEmpty())
        assertEquals(1, result.skipped[SkipReason.DUPLICATE])
    }

    @Test
    fun `同一批里被写了两遍的称重只留一条`() {
        val result = plan(
            existing = listOf(manual("2026-10-01T22:00:00+08:00", 68.0)),
            pulled = listOf(
                pulled("2026-10-02T07:00:00+08:00", 68.2, "hc-1"),
                pulled("2026-10-02T07:01:00+08:00", 68.2, "hc-2"),
            ),
        )

        assertEquals(listOf("hc-1"), result.inserts.map { it.externalId })
        assertEquals(1, result.skipped[SkipReason.DUPLICATE])
    }

    // ─────────────── 合法性 ───────────────

    @Test
    fun `体重超出合法区间就跳过 - 坏数据不该把 Y 轴顶爆`() {
        val result = plan(
            existing = listOf(manual("2026-10-01T22:00:00+08:00", 68.0)),
            pulled = listOf(pulled("2026-10-02T07:00:00+08:00", 685.0, "hc-bad")),
        )

        assertTrue(result.inserts.isEmpty())
        assertEquals(1, result.skipped[SkipReason.OUT_OF_RANGE])
    }

    @Test
    fun `体脂率超出合法区间也跳过`() {
        val result = plan(
            existing = listOf(manual("2026-10-01T22:00:00+08:00", 68.0)),
            pulled = listOf(pulled("2026-10-02T07:00:00+08:00", 68.1, "hc-1", fat = 90.0)),
        )

        assertTrue(result.inserts.isEmpty())
        assertEquals(1, result.skipped[SkipReason.OUT_OF_RANGE])
    }

    @Test
    fun `未来时间跳过 - 时钟偏移不该写出未来的记录`() {
        val result = plan(
            existing = listOf(manual("2026-10-01T22:00:00+08:00", 68.0)),
            pulled = listOf(pulled("2026-10-10T13:00:00+08:00", 68.1, "hc-1")),
        )

        assertTrue(result.inserts.isEmpty())
        assertEquals(1, result.skipped[SkipReason.FUTURE])
    }

    @Test
    fun `未来 10 分钟容差内的接受 - 秤的时钟快几分钟是常态`() {
        val result = plan(
            existing = listOf(manual("2026-10-01T22:00:00+08:00", 68.0)),
            pulled = listOf(pulled("2026-10-10T12:05:00+08:00", 68.1, "hc-1")),
        )

        assertEquals(listOf("hc-1"), result.inserts.map { it.externalId })
    }

    // ─────────────── 异常过滤（共用体脂秤） ───────────────

    @Test
    fun `共用秤上别人的体重被挡下 - 差异过大直接跳过`() {
        val result = plan(
            existing = listOf(manual("2026-10-03T22:00:00+08:00", 78.0)),
            pulled = listOf(pulled("2026-10-04T07:48:00+08:00", 55.0, "mom")),
        )

        assertTrue(result.inserts.isEmpty())
        assertEquals(1, result.skipped[SkipReason.OUTLIER])
    }

    @Test
    fun `被挡下的记录不推进锚点 - 自己的数据仍能正常进`() {
        val result = plan(
            existing = listOf(manual("2026-10-03T22:00:00+08:00", 78.0)),
            pulled = listOf(
                pulled("2026-10-04T07:48:00+08:00", 55.0, "mom"),
                pulled("2026-10-04T07:50:00+08:00", 78.2, "me"),
            ),
        )

        assertEquals(listOf("me"), result.inserts.map { it.externalId })
        assertEquals(1, result.skipped[SkipReason.OUTLIER])
    }

    /**
     * 这条是「锚点必须是 fold」的守门测试：四步各 2.0kg 都在绝对阈值内，
     * 但累计 8.0kg。如果拿初始锚点一次性比较，最后一条会被误杀。
     */
    @Test
    fun `缓慢减重不会被误杀 - 锚点跟着走，不是跟初始值比`() {
        val result = plan(
            existing = listOf(manual("2026-10-03T22:00:00+08:00", 78.0)),
            pulled = listOf(
                pulled("2026-10-04T07:00:00+08:00", 76.0, "d1"),
                pulled("2026-10-05T07:00:00+08:00", 74.0, "d2"),
                pulled("2026-10-06T07:00:00+08:00", 72.0, "d3"),
                pulled("2026-10-07T07:00:00+08:00", 70.0, "d4"),
            ),
        )

        assertEquals(listOf("d1", "d2", "d3", "d4"), result.inserts.map { it.externalId })
        assertTrue(result.skipped.isEmpty())
    }

    /** 大体重者按 5% 放宽：120kg 的阈值是 6.0kg，不是 3.0kg */
    @Test
    fun `体重大时按比例放宽阈值`() {
        val existing = listOf(manual("2026-10-03T22:00:00+08:00", 120.0))

        val fits = plan(existing, pulled = listOf(pulled("2026-10-04T07:00:00+08:00", 126.0, "a")))
        assertEquals(1, fits.inserts.size)

        val tooFar = plan(existing, pulled = listOf(pulled("2026-10-04T07:00:00+08:00", 126.1, "a")))
        assertEquals(1, tooFar.skipped[SkipReason.OUTLIER])
    }

    @Test
    fun `库里为空时没有基准也没有边界 - 全收`() {
        val result = plan(
            pulled = listOf(
                pulled("2026-10-04T07:48:00+08:00", 55.0, "a"),
                pulled("2026-10-05T07:48:00+08:00", 78.0, "b"),
            ),
        )

        assertEquals(listOf("a", "b"), result.inserts.map { it.externalId })
    }

    @Test
    fun `没拉到任何记录时返回空计划`() {
        val result = plan(pulled = emptyList())

        assertEquals(SyncPlan.EMPTY, result)
    }

    @Test
    fun `插入按时间升序 - 后续写入依赖这个顺序`() {
        val result = plan(
            existing = listOf(manual("2026-10-01T22:00:00+08:00", 68.0)),
            pulled = listOf(
                pulled("2026-10-04T07:00:00+08:00", 68.3, "晚"),
                pulled("2026-10-02T07:00:00+08:00", 68.1, "早"),
            ),
        )

        assertEquals(listOf("早", "晚"), result.inserts.map { it.externalId })
    }
}
