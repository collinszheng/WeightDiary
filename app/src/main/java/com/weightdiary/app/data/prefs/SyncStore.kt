package com.weightdiary.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

private val Context.syncDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "sync_state")

/**
 * 同步机制自己的状态：水位线 + 实验功能的三个开关。
 *
 * 刻意**不塞进 [ProfileStore]** —— 那个是用户档案（身高 / 目标 / 标准），
 * 这个是同步机制自己的状态，两者的生命周期和清空语义都不一样：
 * 「清空所有数据」要清水位线，但不动档案，也不动这里的开关。
 */
class SyncStore(private val context: Context) {

    private object Keys {
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")

        /**
         * 实验功能总开关。默认**关**：体脂秤同步能不能用取决于第三方 App 愿不愿意
         * 往 Health Connect 写，不是本 App 的能力，不该默认亮在设置里。
         */
        val EXPERIMENTAL_ENABLED = booleanPreferencesKey("experimental_enabled")

        /** 手动同步：打开后首页顶栏出现同步按钮，点了才同步 */
        val MANUAL_SYNC_ENABLED = booleanPreferencesKey("manual_sync_enabled")

        /** 自动同步：每次冷启动同步一次。默认**关**（用户明确要求） */
        val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
    }

    /** 上次**成功**同步的时刻。拉取窗口的起点由它推出来（见 `docs/08` §5） */
    val lastSyncAt: Flow<Instant?> = context.syncDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.LAST_SYNC_AT]?.let(Instant::ofEpochMilli) }

    val experimentalEnabled: Flow<Boolean> = booleanFlow(Keys.EXPERIMENTAL_ENABLED)
    val manualSyncEnabled: Flow<Boolean> = booleanFlow(Keys.MANUAL_SYNC_ENABLED)
    val autoSyncEnabled: Flow<Boolean> = booleanFlow(Keys.AUTO_SYNC_ENABLED)

    /** 同步成功后才推进水位线（`WeightSyncCoordinator` 在整条流程走完后才调它） */
    suspend fun setLastSyncAt(at: Instant) {
        context.syncDataStore.edit { it[Keys.LAST_SYNC_AT] = at.toEpochMilli() }
    }

    suspend fun setExperimentalEnabled(enabled: Boolean) =
        setBoolean(Keys.EXPERIMENTAL_ENABLED, enabled)

    suspend fun setManualSyncEnabled(enabled: Boolean) =
        setBoolean(Keys.MANUAL_SYNC_ENABLED, enabled)

    suspend fun setAutoSyncEnabled(enabled: Boolean) =
        setBoolean(Keys.AUTO_SYNC_ENABLED, enabled)

    /**
     * 清空同步**水位线**。
     *
     * 刻意只删 `last_sync_at`，不碰三个开关 —— 「清空所有数据」是清数据，
     * 不是恢复出厂设置：用户选好的同步方式不该被顺手抹掉。
     * （加开关之前这里是 `it.clear()`，那之后就不能再那样了。）
     */
    suspend fun clear() {
        context.syncDataStore.edit { it.remove(Keys.LAST_SYNC_AT) }
    }

    /** 读不到或读失败一律当 false：这三个开关的默认值都是「关」 */
    private fun booleanFlow(key: Preferences.Key<Boolean>): Flow<Boolean> =
        context.syncDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[key] ?: false }

    private suspend fun setBoolean(key: Preferences.Key<Boolean>, value: Boolean) {
        context.syncDataStore.edit { it[key] = value }
    }
}
