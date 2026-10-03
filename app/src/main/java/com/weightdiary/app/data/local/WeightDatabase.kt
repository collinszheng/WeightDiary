package com.weightdiary.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WeightRecordEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class WeightDatabase : RoomDatabase() {

    abstract fun weightDao(): WeightDao

    companion object {
        private const val DB_NAME = "weight_diary.db"

        fun build(context: Context): WeightDatabase =
            Room.databaseBuilder(context.applicationContext, WeightDatabase::class.java, DB_NAME)
                // 刻意不提供 fallbackToDestructiveMigration：
                // 表结构变更必须写 Migration，否则宁可崩溃也不要静默清空用户数据。
                .build()
    }
}
