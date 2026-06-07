package com.hgu.watervalve.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    }

    val uisToken: Flow<String?> = dataStore.data.map { it[UIS_TOKEN] }
    val uwcToken: Flow<String?> = dataStore.data.map { it[UWC_TOKEN] }
    val sessionCookie: Flow<String?> = dataStore.data.map { it[SESSION_COOKIE] }

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

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
