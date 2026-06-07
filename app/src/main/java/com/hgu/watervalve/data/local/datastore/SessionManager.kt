package com.hgu.watervalve.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }

    val uisToken: Flow<String?> = dataStore.data.map { it[UIS_TOKEN] }
    val uwcToken: Flow<String?> = dataStore.data.map { it[UWC_TOKEN] }
    val sessionCookie: Flow<String?> = dataStore.data.map { it[SESSION_COOKIE] }
    val userAccNum: Flow<String?> = dataStore.data.map { it[USER_ACC_NUM] }
    val userEpId: Flow<String?> = dataStore.data.map { it[USER_EP_ID] }
    val userId: Flow<String?> = dataStore.data.map { it[USER_ID] }
    val userPerCode: Flow<String?> = dataStore.data.map { it[USER_PER_CODE] }
    val hasSeenOnboarding: Flow<Boolean> = dataStore.data.map { it[HAS_SEEN_ONBOARDING] ?: false }

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
}
