package com.weightdiary.app.di

import android.content.Context
import com.weightdiary.app.data.backup.RecordBackup
import com.weightdiary.app.data.local.WeightDatabase
import com.weightdiary.app.data.prefs.ProfileStore
import com.weightdiary.app.data.repository.WeightRepository

/**
 * 手写依赖容器。
 *
 * 刻意不引入 Hilt：AGP 9 + KSP 的版本兼容需要先验证，而本期依赖图很浅（数据库 / 档案 / 仓库三个），
 * 手写容器足够且没有注解处理开销。见决策记录 «被否方案汇总»。
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val database: WeightDatabase by lazy { WeightDatabase.build(appContext) }

    val profileStore: ProfileStore by lazy { ProfileStore(appContext) }

    val weightRepository: WeightRepository by lazy {
        WeightRepository(database.weightDao(), profileStore)
    }

    val recordBackup: RecordBackup by lazy { RecordBackup(appContext) }
}
