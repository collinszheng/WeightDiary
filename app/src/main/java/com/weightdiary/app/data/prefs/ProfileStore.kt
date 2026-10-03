package com.weightdiary.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.weightdiary.app.domain.model.BmiStandard
import com.weightdiary.app.domain.model.UnitSystem
import com.weightdiary.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime

private val Context.profileDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "user_profile")

private object ProfileKeys {
    val HEIGHT_CM = doublePreferencesKey("height_cm")
    val TARGET_WEIGHT_KG = doublePreferencesKey("target_weight_kg")
    val TARGET_SET_AT_WEIGHT_KG = doublePreferencesKey("target_set_at_weight_kg")
    val BMI_STANDARD = stringPreferencesKey("bmi_standard")
    val UNIT_SYSTEM = stringPreferencesKey("unit_system")
    val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
    val REMINDER_HOUR = intPreferencesKey("reminder_hour")
    val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

/** 用户档案存取。低频变更，用 DataStore 而非 Room 表。 */
class ProfileStore(private val context: Context) {

    val profile: Flow<UserProfile> = context.profileDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toUserProfile() }

    suspend fun setHeight(heightCm: Double?) {
        context.profileDataStore.edit { it.putOrRemove(ProfileKeys.HEIGHT_CM, heightCm) }
    }

    /**
     * 设置目标体重，同时记录**设置目标时的体重**作为进度条起点（见设计规范 §4.3）。
     * 传 `null` 表示清除目标，此时起点也一并清除。
     */
    suspend fun setTargetWeight(targetWeightKg: Double?, setAtWeightKg: Double?) {
        context.profileDataStore.edit { prefs ->
            prefs.putOrRemove(ProfileKeys.TARGET_WEIGHT_KG, targetWeightKg)
            prefs.putOrRemove(
                ProfileKeys.TARGET_SET_AT_WEIGHT_KG,
                if (targetWeightKg == null) null else setAtWeightKg,
            )
        }
    }

    suspend fun setBmiStandard(standard: BmiStandard) {
        context.profileDataStore.edit { it[ProfileKeys.BMI_STANDARD] = standard.name }
    }

    /** 首次启动的身高引导：填写或跳过都调它，避免每次冷启动都弹 */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.profileDataStore.edit { it[ProfileKeys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setUnitSystem(unitSystem: UnitSystem) {
        context.profileDataStore.edit { it[ProfileKeys.UNIT_SYSTEM] = unitSystem.name }
    }

    suspend fun setReminder(enabled: Boolean, time: LocalTime?) {
        context.profileDataStore.edit { prefs ->
            prefs[ProfileKeys.REMINDER_ENABLED] = enabled
            if (time == null) {
                prefs.remove(ProfileKeys.REMINDER_HOUR)
                prefs.remove(ProfileKeys.REMINDER_MINUTE)
            } else {
                prefs[ProfileKeys.REMINDER_HOUR] = time.hour
                prefs[ProfileKeys.REMINDER_MINUTE] = time.minute
            }
        }
    }
}

private fun MutablePreferences.putOrRemove(key: Preferences.Key<Double>, value: Double?) {
    if (value == null) remove(key) else set(key, value)
}

private fun Preferences.toUserProfile(): UserProfile {
    val hour = this[ProfileKeys.REMINDER_HOUR]
    val minute = this[ProfileKeys.REMINDER_MINUTE]
    return UserProfile(
        heightCm = this[ProfileKeys.HEIGHT_CM],
        targetWeightKg = this[ProfileKeys.TARGET_WEIGHT_KG],
        targetSetAtWeightKg = this[ProfileKeys.TARGET_SET_AT_WEIGHT_KG],
        bmiStandard = this[ProfileKeys.BMI_STANDARD]
            ?.let { name -> BmiStandard.entries.firstOrNull { it.name == name } }
            ?: BmiStandard.CHINA,
        unitSystem = this[ProfileKeys.UNIT_SYSTEM]
            ?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } }
            ?: UnitSystem.METRIC,
        reminderEnabled = this[ProfileKeys.REMINDER_ENABLED] ?: false,
        reminderTime = if (hour != null && minute != null) LocalTime.of(hour, minute) else null,
        onboardingCompleted = this[ProfileKeys.ONBOARDING_COMPLETED] ?: false,
    )
}
