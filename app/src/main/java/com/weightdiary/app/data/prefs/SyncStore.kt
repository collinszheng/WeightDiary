package com.weightdiary.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
 * 同步的运行时状态。
 *
 * 刻意**不塞进 [ProfileStore]** —— 那个是用户档案（身高 / 目标 / 标准），
 * 这个是同步机制自己的水位线，两者的生命周期和清空语义都不一样：
 * 「清空所有数据」要清这个，但不动档案。
 */
class SyncStore(private val context: Context) {

    private object Keys {
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }

    /** 上次**成功**同步的时刻。拉取窗口的起点由它推出来（见 `docs/08` §5） */
    val lastSyncAt: Flow<Instant?> = context.syncDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.LAST_SYNC_AT]?.let(Instant::ofEpochMilli) }

    suspend fun setLastSyncAt(instant: Instant) {
        context.syncDataStore.edit { it[Keys.LAST_SYNC_AT] = instant.toEpochMilli() }
    }

    suspend fun clear() {
        context.syncDataStore.edit { it.clear() }
    }
}
