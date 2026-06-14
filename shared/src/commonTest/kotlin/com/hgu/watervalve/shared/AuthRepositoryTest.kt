package com.hgu.watervalve.shared

import com.hgu.watervalve.shared.data.remote.api.UwcApi
import com.hgu.watervalve.shared.data.repository.AuthRepository
import com.hgu.watervalve.shared.data.repository.LoginError
import com.hgu.watervalve.shared.data.repository.LoginResult
import com.hgu.watervalve.shared.data.repository.LoginState
import com.hgu.watervalve.shared.platform.KeychainWrapper
import com.hgu.watervalve.shared.platform.UserDefaultsWrapper
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_SESSION_COOKIE
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_UIS_JWT
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_USER_ID
import com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_UWC_TOKEN
import com.hgu.watervalve.shared.util.Constants.UD_KEY_IS_BANNED
import com.hgu.watervalve.shared.util.Constants.UD_KEY_LAST_REFRESH_TIME
import com.hgu.watervalve.shared.util.Constants.UD_KEY_NICKNAME
import com.hgu.watervalve.shared.util.Constants.UD_KEY_USER_ACC_NUM
import com.hgu.watervalve.shared.util.Constants.UD_KEY_USER_EP_ID
import com.hgu.watervalve.shared.util.Constants.UD_KEY_USER_PER_CODE
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthRepositoryTest {
    @Test
    fun invalidTicketFailsFastWithoutNetwork() {
        runBlocking {
            val repository = createRepository()

            val result = repository.exchangeCasTicket("bad-ticket")

            assertIs<LoginResult.Failed>(result)
            assertEquals(LoginError.InvalidCredentials, result.error)
            assertIs<LoginState.Failed>(repository.loginState.value)
        }
    }

    @Test
    fun successfulExchangePersistsSessionAndClearsBannedFlag() {
        runBlocking {
            val keychain = KeychainWrapper()
            val userDefaults = UserDefaultsWrapper().apply {
                setBool(UD_KEY_IS_BANNED, true)
            }
            val repository = createRepository(
                api = AuthRepositoryTestFixtures.createApi(),
                keychain = keychain,
                userDefaults = userDefaults,
            )

            val result = repository.exchangeCasTicket("ST-valid-ticket")

            assertIs<LoginResult.Success>(result)
            assertEquals("u-1001", result.userInfo.userId)
            assertIs<LoginState.Success>(repository.loginState.value)
            assertEquals("uwc-token-123", keychain.get(KEYCHAIN_KEY_UWC_TOKEN))
            assertEquals("uis-jwt-456", keychain.get(KEYCHAIN_KEY_UIS_JWT))
            assertEquals("session-cookie-789", keychain.get(KEYCHAIN_KEY_SESSION_COOKIE))
            assertEquals("u-1001", keychain.get(KEYCHAIN_KEY_USER_ID))
            assertEquals("Alice", userDefaults.getString(UD_KEY_NICKNAME))
            assertEquals("acc-77", userDefaults.getString(UD_KEY_USER_ACC_NUM))
            assertEquals("ep-9", userDefaults.getString(UD_KEY_USER_EP_ID))
            assertEquals("pc-1", userDefaults.getString(UD_KEY_USER_PER_CODE))
            assertFalse(repository.isBanned.value)
        }
    }

    @Test
    fun refreshUsesStoredJwtAndUpdatesLastRefreshTime() {
        runBlocking {
            val keychain = KeychainWrapper().apply {
                set(KEYCHAIN_KEY_UIS_JWT, "stored-uis-jwt")
                set(KEYCHAIN_KEY_SESSION_COOKIE, "stored-session")
            }
            val userDefaults = UserDefaultsWrapper()
            val repository = createRepository(
                api = AuthRepositoryTestFixtures.createApi(expectedJwt = "stored-uis-jwt"),
                keychain = keychain,
                userDefaults = userDefaults,
            )

            val success = repository.refreshUwcToken()

            assertTrue(success)
            assertEquals("uwc-token-123", keychain.get(KEYCHAIN_KEY_UWC_TOKEN))
            assertTrue(userDefaults.getLong(UD_KEY_LAST_REFRESH_TIME) > 0L)
        }
    }

    @Test
    fun clearAuthResetsPersistedState() {
        val keychain = KeychainWrapper().apply {
            set(KEYCHAIN_KEY_UWC_TOKEN, "uwc")
            set(KEYCHAIN_KEY_UIS_JWT, "uis")
            set(KEYCHAIN_KEY_USER_ID, "user")
        }
        val userDefaults = UserDefaultsWrapper().apply {
            setString(UD_KEY_NICKNAME, "Nick")
            setString(UD_KEY_USER_ACC_NUM, "acc")
            setString(UD_KEY_USER_EP_ID, "ep")
            setString(UD_KEY_USER_PER_CODE, "per")
            setBool(UD_KEY_IS_BANNED, true)
            setLong(UD_KEY_LAST_REFRESH_TIME, 123L)
        }
        val repository = createRepository(
            keychain = keychain,
            userDefaults = userDefaults,
        )

        repository.clearAuth()

        assertFalse(repository.hasValidToken())
        assertFalse(repository.isBanned.value)
        assertIs<LoginState.Idle>(repository.loginState.value)
        assertEquals(null, keychain.get(KEYCHAIN_KEY_UWC_TOKEN))
        assertEquals(null, userDefaults.getString(UD_KEY_NICKNAME))
        assertEquals(0L, userDefaults.getLong(UD_KEY_LAST_REFRESH_TIME))
    }

    private fun createRepository(
        api: UwcApi = AuthRepositoryTestFixtures.createApi(),
        keychain: KeychainWrapper = KeychainWrapper(),
        userDefaults: UserDefaultsWrapper = UserDefaultsWrapper(),
    ): AuthRepository {
        return AuthRepository(
            uwcApi = api,
            keychain = keychain,
            userDefaults = userDefaults,
        )
    }
}
