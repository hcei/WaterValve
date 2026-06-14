package com.hgu.watervalve.shared.data.repository

import com.hgu.watervalve.shared.platform.KeychainWrapper
import com.hgu.watervalve.shared.util.Constants

interface SessionStore {
    fun saveUisToken(token: String)
    fun getUisToken(): String?
    fun saveUwcToken(token: String)
    fun getUwcToken(): String?
    fun saveUserId(userId: String)
    fun getUserId(): String?
    fun saveAccNum(accNum: String)
    fun getAccNum(): String?
    fun saveEpId(epId: String)
    fun getEpId(): String?
    fun savePerCode(perCode: String)
    fun getPerCode(): String?
    fun clear()
}

class KeychainSessionStore(
    private val keychainWrapper: KeychainWrapper,
) : SessionStore {
    override fun saveUisToken(token: String) {
        keychainWrapper.set(Constants.KEYCHAIN_KEY_UIS_JWT, token)
    }

    override fun getUisToken(): String? = keychainWrapper.get(Constants.KEYCHAIN_KEY_UIS_JWT)

    override fun saveUwcToken(token: String) {
        keychainWrapper.set(Constants.KEYCHAIN_KEY_UWC_TOKEN, token)
    }

    override fun getUwcToken(): String? = keychainWrapper.get(Constants.KEYCHAIN_KEY_UWC_TOKEN)

    override fun saveUserId(userId: String) {
        keychainWrapper.set(Constants.KEYCHAIN_KEY_USER_ID, userId)
    }

    override fun getUserId(): String? = keychainWrapper.get(Constants.KEYCHAIN_KEY_USER_ID)

    override fun saveAccNum(accNum: String) {
        keychainWrapper.set(Constants.UD_KEY_USER_ACC_NUM, accNum)
    }

    override fun getAccNum(): String? = keychainWrapper.get(Constants.UD_KEY_USER_ACC_NUM)

    override fun saveEpId(epId: String) {
        keychainWrapper.set(Constants.UD_KEY_USER_EP_ID, epId)
    }

    override fun getEpId(): String? = keychainWrapper.get(Constants.UD_KEY_USER_EP_ID)

    override fun savePerCode(perCode: String) {
        keychainWrapper.set(Constants.UD_KEY_USER_PER_CODE, perCode)
    }

    override fun getPerCode(): String? = keychainWrapper.get(Constants.UD_KEY_USER_PER_CODE)

    override fun clear() {
        keychainWrapper.clear()
    }
}
