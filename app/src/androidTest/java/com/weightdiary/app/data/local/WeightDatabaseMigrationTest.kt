package com.weightdiary.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weightdiary.app.domain.model.RecordSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * v1 → v2 迁移的**自动化**验证（跑在设备上：`connectedDebugAndroidTest`）。
 *
 * 为什么要有它：本项目刻意不提供 `fallbackToDestructiveMigration`（AGENTS 红线 #5），
 * 写错迁移就是老用户数据全丢。而在这之前只能手工覆盖安装验（`docs/06` §7.4）——
 * 那方法**测不出**「哪些数据丢了」，只能看出「应用崩没崩」，而且每加一版迁移都要重跑。
 *
 * **怎么做**：裸 SQL 建一个真正的 v1 库、塞进老数据，然后**用生产同样的配置打开它**。
 * Room 会自己跑 `MIGRATION_1_2`，再拿生成的 identity hash 校验结构 ——
 * 结构对不上就抛 `IllegalStateException`。也就是说"迁移写错了"这件事由 Room 自己举报，
 * 不需要额外断言，这比手写结构断言更可靠。
 *
 * **为什么不用 `room-testing` 的 `MigrationTestHelper`**：它会把
 * `androidx.lifecycle:lifecycle-viewmodel-savedstate` 带的 `kotlinx-serialization-core:1.7.3`
 * 和 `room-migration:2.8.5` 要的 `1.8.1` 撞在一起（AGP 的 consistent resolution 会把
 * app 侧的 1.7.3 作为 **strict** 约束复制进 androidTest 的 classpath），运行时直接
 * `AbstractMethodError`。要修就得为一个测试去抬生产的依赖版本，不划算 ——
 * 而上面这条路验的是同一条真实路径，且零新增生产依赖。
 */
@RunWith(AndroidJUnit4::class)
class WeightDatabaseMigrationTest {

    private companion object {
        const val TEST_DB = "migration-1to2.db"

        /**
         * v1 的表结构。**逐字抄自
         * `app/schemas/com.weightdiary.app.data.local.WeightDatabase/1.json` 的 `createSql`**
         * —— 那是 KSP 导出的、当时真正建出来的结构，不是凭印象写的。
         *
         * 手写它是有代价的（会漂移）；但它是自检的：`index_weight_records_measuredAt`
         * 只存在于 v1、迁移不会重建它，所以这里漏了它，之后的 Room 结构校验就会失败。
         */
        val V1_DDL = listOf(
            "CREATE TABLE IF NOT EXISTS `weight_records` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`measuredAt` INTEGER NOT NULL, " +
                "`weightKg` REAL NOT NULL, " +
                "`bodyFatPercent` REAL, " +
                "`note` TEXT, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_weight_records_measuredAt` " +
                "ON `weight_records` (`measuredAt`)",
        )

        const val INSERT_V1 =
            "INSERT INTO `weight_records` " +
                "(`measuredAt`, `weightKg`, `bodyFatPercent`, `note`, `createdAt`, `updatedAt`) " +
                "VALUES (?, ?, ?, ?, ?, ?)"
    }

    private lateinit var context: Context
    private var opened: WeightDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        opened?.close()
        context.deleteDatabase(TEST_DB)
    }

    /** 造一个真正的 v1 库：v1 的 DDL + 两条老记录 + `user_version = 1` */
    private fun createLegacyV1Database() {
        val file: File = context.getDatabasePath(TEST_DB)
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            V1_DDL.forEach { legacy.execSQL(it) }

            // 有体脂的一条
            legacy.execSQL(
                INSERT_V1,
                arrayOf<Any?>(1_700_000_000_000L, 68.5, 22.5, "迁移前", 1L, 1L),
            )
            // 没体脂的一条 —— 可空列在迁移里最容易出事
            legacy.execSQL(
                INSERT_V1,
                arrayOf<Any?>(1_700_100_000_000L, 67.5, null, null, 2L, 2L),
            )

            // 不置 1 的话 Room 不会认为该跑迁移
            legacy.version = 1
        }
    }

    private fun synced(measuredAt: Long, weightKg: Double, externalId: String) =
        WeightRecordEntity(
            measuredAt = measuredAt,
            weightKg = weightKg,
            bodyFatPercent = null,
            note = null,
            createdAt = 1L,
            updatedAt = 1L,
            source = RecordSource.HEALTH_CONNECT.name,
            externalId = externalId,
        )

    private fun manual(measuredAt: Long, weightKg: Double) =
        WeightRecordEntity(
            measuredAt = measuredAt,
            weightKg = weightKg,
            bodyFatPercent = null,
            note = null,
            createdAt = 1L,
            updatedAt = 1L,
            source = RecordSource.MANUAL.name,
            externalId = null,
        )

    @Test
    fun 迁移后旧数据与体脂保留且结构与v2一致() = runBlocking {
        createLegacyV1Database()

        // 用**生产同样的配置**打开 —— 迁移与结构校验都在这一步发生
        val db = Room.databaseBuilder(context, WeightDatabase::class.java, TEST_DB)
            .addMigrations(WeightDatabase.MIGRATION_1_2)
            .build()
        opened = db
        val dao = db.weightDao()

        // ── 1. 老数据还在，体脂没丢，新列拿到正确默认值 ──
        val all = dao.getAllOnce()
        assertEquals("两条老记录都该在", 2, all.size)

        val older = all[0]
        assertEquals(68.5, older.weightKg, 1e-9)
        assertEquals("体脂在迁移里丢了", 22.5, older.bodyFatPercent!!, 1e-9)
        assertEquals("迁移前", older.note)
        assertEquals("老记录一律算手动", RecordSource.MANUAL.name, older.source)
        assertNull("老记录不该有外部 id", older.externalId)

        val newer = all[1]
        assertEquals(67.5, newer.weightKg, 1e-9)
        assertNull("这条本来就没填体脂，迁移后也该是 NULL", newer.bodyFatPercent)
        assertEquals(RecordSource.MANUAL.name, newer.source)

        // ── 2. 墓碑表建好了，能读能写 ──
        assertEquals(emptyList<String>(), dao.allIgnoredIds())
        dao.insertIgnored(IgnoredExternalIdEntity("hc-x", 1L))
        assertEquals(listOf("hc-x"), dao.allIgnoredIds())

        // ── 3. externalId 唯一索引真的生效 ──
        val inserted = dao.insertAllIgnore(
            listOf(
                synced(10L, 70.0, "hc-1"),
                synced(11L, 71.0, "hc-1"),
            )
        )
        assertEquals(
            "同一个 externalId 插了两次没被挡下 —— 唯一的去重保险失效了",
            1,
            inserted.count { it != -1L },
        )
        assertEquals(3, dao.getAllOnce().size)

        // ── 4. 多个 NULL 可以共存（手动记录不受唯一索引影响）──
        //    这一条是整个去重设计的**前提**：SQLite 把多个 NULL 视为互不相同。
        //    哪天有人把 externalId 改成非空，这里会先炸。
        dao.insertAllIgnore(listOf(manual(12L, 72.0), manual(13L, 73.0)))
        assertEquals("2 条老的 + 1 条同步 + 2 条手动 = 5", 5, dao.getAllOnce().size)
    }
}
