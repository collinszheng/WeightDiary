package com.weightdiary.app.di

import android.content.Context
import com.weightdiary.app.data.backup.RecordBackup
import com.weightdiary.app.data.healthconnect.HealthConnectSource
import com.weightdiary.app.data.local.WeightDatabase
import com.weightdiary.app.data.prefs.ProfileStore
import com.weightdiary.app.data.prefs.SyncStore
import com.weightdiary.app.data.repository.WeightRepository
import com.weightdiary.app.data.sync.WeightSyncCoordinator

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

    val syncStore: SyncStore by lazy { SyncStore(appContext) }

    val weightRepository: WeightRepository by lazy {
        WeightRepository(database, profileStore, syncStore)
    }

    val recordBackup: RecordBackup by lazy { RecordBackup(appContext) }

    val healthConnectSource: HealthConnectSource by lazy { HealthConnectSource(appContext) }

    val weightSyncCoordinator: WeightSyncCoordinator by lazy {
        WeightSyncCoordinator(weightRepository, syncStore, healthConnectSource)
    }
}
