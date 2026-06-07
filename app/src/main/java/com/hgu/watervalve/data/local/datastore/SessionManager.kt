package com.hgu.watervalve.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.sessionDataStore

    companion object Keys {
        val UIS_TOKEN = stringPreferencesKey("uis_token")
        val UWC_TOKEN = stringPreferencesKey("uwc_token")
        val SESSION_COOKIE = stringPreferencesKey("session_cookie")
        val CAS_TICKET = stringPreferencesKey("cas_ticket")
        val USER_ACC_NUM = stringPreferencesKey("user_acc_num")
        val USER_EP_ID = stringPreferencesKey("user_ep_id")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_PER_CODE = stringPreferencesKey("user_per_code")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val AUTO_CHECK_UPDATE = booleanPreferencesKey("auto_check_update")
        val LAST_UPDATE_CHECK_TIME = longPreferencesKey("last_update_check_time")
        val PENDING_UPDATE_REMINDER = booleanPreferencesKey("pending_update_reminder")
    }

    val uisToken: Flow<String?> = dataStore.data.map { it[UIS_TOKEN] }
    val uwcToken: Flow<String?> = dataStore.data.map { it[UWC_TOKEN] }
    val sessionCookie: Flow<String?> = dataStore.data.map { it[SESSION_COOKIE] }
    val userAccNum: Flow<String?> = dataStore.data.map { it[USER_ACC_NUM] }
    val userEpId: Flow<String?> = dataStore.data.map { it[USER_EP_ID] }
    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }
    val userPerCode: Flow<String?> = dataStore.data.map { it[USER_PER_CODE] }
    val hasSeenOnboarding: Flow<Boolean> = dataStore.data.map { it[HAS_SEEN_ONBOARDING] ?: false }
    val autoCheckUpdate: Flow<Boolean> = dataStore.data.map { it[AUTO_CHECK_UPDATE] ?: true }
    val lastUpdateCheckTime: Flow<Long> = dataStore.data.map { it[LAST_UPDATE_CHECK_TIME] ?: 0L }
    val pendingUpdateReminder: Flow<Boolean> = dataStore.data.map { it[PENDING_UPDATE_REMINDER] ?: false }

    suspend fun saveUisToken(token: String) {
        dataStore.edit { it[UIS_TOKEN] = token }
    }

    suspend fun saveUwcToken(token: String) {
        dataStore.edit { it[UWC_TOKEN] = token }
    }

    suspend fun saveSessionCookie(cookie: String) {
        dataStore.edit { it[SESSION_COOKIE] = cookie }
    }

    suspend fun saveCasTicket(ticket: String) {
        dataStore.edit { it[CAS_TICKET] = ticket }
    }

    suspend fun saveUserInfo(accNum: String, epId: String, userId: String, perCode: String) {
        dataStore.edit {
            it[USER_ACC_NUM] = accNum
            it[USER_EP_ID] = epId
            it[USER_ID] = userId
            it[USER_PER_CODE] = perCode
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    /** 标记用户已看过首次引导 */
    suspend fun markOnboardingSeen() {
        dataStore.edit { it[HAS_SEEN_ONBOARDING] = true }
    }

    /** 设置自动检测更新开关 */
    suspend fun setAutoCheckUpdate(enabled: Boolean) {
        dataStore.edit { it[AUTO_CHECK_UPDATE] = enabled }
    }

    /** 记录上次检查更新时间 */
    suspend fun setLastUpdateCheckTime(time: Long) {
        dataStore.edit { it[LAST_UPDATE_CHECK_TIME] = time }
    }

    /** 设置待提醒标记（下载失败后下次启动立即提醒） */
    suspend fun setPendingUpdateReminder(pending: Boolean) {
        dataStore.edit { it[PENDING_UPDATE_REMINDER] = pending }
    }
}
