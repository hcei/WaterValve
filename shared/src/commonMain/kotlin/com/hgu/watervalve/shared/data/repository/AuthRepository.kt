package com.hgu.watervalve.shared.data.repository

import com.hgu.watervalve.shared.data.remote.api.LoginByTokenPayload
import com.hgu.watervalve.shared.data.remote.api.UwcApi
import com.hgu.watervalve.shared.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.shared.domain.model.UserInfo
import com.hgu.watervalve.shared.platform.KeychainWrapper
import com.hgu.watervalve.shared.platform.UserDefaultsWrapper
import com.hgu.watervalve.shared.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthRepository(
    private val uwcApi: UwcApi,
    private val keychain: KeychainWrapper,
    private val userDefaults: UserDefaultsWrapper,
) {
    private val mutableLoginState = MutableStateFlow<LoginState>(LoginState.Idle)
    private val mutableIsBanned = MutableStateFlow(userDefaults.getBool(Constants.UD_KEY_IS_BANNED))

    val loginState: StateFlow<LoginState> = mutableLoginState.asStateFlow()
    val isBanned: StateFlow<Boolean> = mutableIsBanned.asStateFlow()

    fun startCasLogin(): CasLoginConfig {
        return CasLoginConfig(
            url = Constants.CAS_LOGIN_URL,
            userAgent = Constants.CHROME_IOS_UA,
        )
    }

    suspend fun exchangeCasTicket(ticket: String): LoginResult {
        if (!ticket.startsWith("ST-")) {
            mutableLoginState.value = LoginState.Failed(LoginError.InvalidCredentials)
            return LoginResult.Failed(LoginError.InvalidCredentials)
        }

        return try {
            mutableLoginState.value = LoginState.Loading(step = 1, message = "CAS 认证中")
            val signData = "service=${Constants.CAS_SERVICE_URL}&ticket=$ticket"
            val sessionExchange = uwcApi.exchangeCasTicket(
                ticket = ticket,
                sign = UwcCrypto.signUis(signData),
                nonce = UwcCrypto.generateNonce(),
                timestamp = UwcCrypto.generateTimestamp().toString(),
            )

            mutableLoginState.value = LoginState.Loading(step = 2, message = "获取 UIS Token")
            val uisJwt = uwcApi.getUisToken(sessionExchange.sessionCookie)

            mutableLoginState.value = LoginState.Loading(step = 3, message = "换取 UWC Token")
            val payload = uwcApi.loginByToken(
                headerToken = uisJwt,
                paramStr = UwcCrypto.buildParamStr(mapOf("uiastoken" to uisJwt)),
                timestamp = UwcCrypto.generateTimestamp().toString(),
                nonce = UwcCrypto.generateNonce(),
            )

            saveSession(payload, uisJwt, sessionExchange.sessionCookie)
            mutableIsBanned.value = false
            userDefaults.setBool(Constants.UD_KEY_IS_BANNED, false)
            mutableLoginState.value = LoginState.Success
            LoginResult.Success(payload.userInfo)
        } catch (_: Throwable) {
            mutableLoginState.value = LoginState.Failed(LoginError.Network)
            LoginResult.Failed(LoginError.Network)
        }
    }

    suspend fun refreshUwcToken(): Boolean {
        val uisJwt = keychain.get(Constants.KEYCHAIN_KEY_UIS_JWT).orEmpty()
        val sessionCookie = keychain.get(Constants.KEYCHAIN_KEY_SESSION_COOKIE).orEmpty()
        if (uisJwt.isBlank()) return false

        return try {
            val payload = uwcApi.loginByToken(
                headerToken = uisJwt,
                paramStr = UwcCrypto.buildParamStr(mapOf("uiastoken" to uisJwt)),
                timestamp = UwcCrypto.generateTimestamp().toString(),
                nonce = UwcCrypto.generateNonce(),
            )
            saveSession(payload, uisJwt, sessionCookie)
            userDefaults.setLong(Constants.UD_KEY_LAST_REFRESH_TIME, UwcCrypto.generateTimestamp())
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun hasValidToken(): Boolean = !keychain.get(Constants.KEYCHAIN_KEY_UWC_TOKEN).isNullOrBlank()

    fun getUwcToken(): String? = keychain.get(Constants.KEYCHAIN_KEY_UWC_TOKEN)

    fun getUserId(): String? = keychain.get(Constants.KEYCHAIN_KEY_USER_ID)

    fun getEpId(): String? = userDefaults.getString(Constants.UD_KEY_USER_EP_ID)

    fun markBanned() {
        mutableIsBanned.value = true
        userDefaults.setBool(Constants.UD_KEY_IS_BANNED, true)
    }

    fun clearBanned() {
        mutableIsBanned.value = false
        userDefaults.setBool(Constants.UD_KEY_IS_BANNED, false)
    }

    fun clearAuth() {
        keychain.clear()
        userDefaults.remove(Constants.UD_KEY_NICKNAME)
        userDefaults.remove(Constants.UD_KEY_USER_ACC_NUM)
        userDefaults.remove(Constants.UD_KEY_USER_EP_ID)
        userDefaults.remove(Constants.UD_KEY_USER_PER_CODE)
        userDefaults.remove(Constants.UD_KEY_IS_BANNED)
        userDefaults.remove(Constants.UD_KEY_LAST_REFRESH_TIME)
        mutableIsBanned.value = false
        mutableLoginState.value = LoginState.Idle
    }

    private fun saveSession(
        payload: LoginByTokenPayload,
        uisJwt: String,
        sessionCookie: String,
    ) {
        keychain.set(Constants.KEYCHAIN_KEY_USER_ID, payload.userInfo.userId)
        keychain.set(Constants.KEYCHAIN_KEY_UIS_JWT, uisJwt)
        keychain.set(Constants.KEYCHAIN_KEY_UWC_TOKEN, payload.token)
        if (sessionCookie.isNotBlank()) {
            keychain.set(Constants.KEYCHAIN_KEY_SESSION_COOKIE, sessionCookie)
        }

        userDefaults.setString(Constants.UD_KEY_NICKNAME, payload.userInfo.nickname)
        userDefaults.setString(Constants.UD_KEY_USER_ACC_NUM, payload.accNum)
        userDefaults.setString(Constants.UD_KEY_USER_EP_ID, payload.epId)
        userDefaults.setString(Constants.UD_KEY_USER_PER_CODE, payload.perCode)
    }
}

data class CasLoginConfig(
    val url: String,
    val userAgent: String,
)

sealed class LoginState {
    data object Idle : LoginState()

    data class Loading(
        val step: Int,
        val message: String,
    ) : LoginState()

    data object Success : LoginState()

    data class Failed(
        val error: LoginError,
    ) : LoginState()
}

enum class LoginError {
    Network,
    InvalidCredentials,
    Banned,
    Unknown,
}

sealed class LoginResult {
    data class Success(
        val userInfo: UserInfo,
    ) : LoginResult()

    data class Failed(
        val error: LoginError,
    ) : LoginResult()
}
