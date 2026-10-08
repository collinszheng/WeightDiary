package com.weightdiary.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [WeightRecordEntity::class, IgnoredExternalIdEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class WeightDatabase : RoomDatabase() {

    abstract fun weightDao(): WeightDao

    companion object {
        private const val DB_NAME = "weight_diary.db"

        /**
         * v1 → v2：接入 Health Connect（见 `docs/08` §3.2）。
         *
         * 三条纪律：
         * - **只用 ALTER TABLE / CREATE，绝不重建表** —— 重建会丢数据
         * - 加 NOT NULL 列必须给默认值，`source` 老的记录一律算 MANUAL
         * - 唯一索引是去重的基石；`externalId` 允许多个 NULL，所以手动记录不受影响
         *
         * 结构必须与 Room 生成的 schema 完全一致，否则开机 `validateMigration` 会抛
         * `Migration didn't properly handle`。对拍物是 `app/schemas/.../2.json`。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `weight_records` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'MANUAL'"
                )
                db.execSQL(
                    "ALTER TABLE `weight_records` ADD COLUMN `externalId` TEXT"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_weight_records_externalId` " +
                        "ON `weight_records` (`externalId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ignored_external_ids` " +
                        "(`externalId` TEXT NOT NULL, `ignoredAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`externalId`))"
                )
            }
        }

        fun build(context: Context): WeightDatabase =
            Room.databaseBuilder(context.applicationContext, WeightDatabase::class.java, DB_NAME)
                // 刻意不提供 fallbackToDestructiveMigration：
                // 表结构变更必须写 Migration，否则宁可崩溃也不要静默清空用户数据。
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
