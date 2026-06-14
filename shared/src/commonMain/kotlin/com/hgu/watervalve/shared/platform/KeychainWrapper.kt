package com.hgu.watervalve.shared.platform

/**
 * iOS Keychain 访问封装（iosMain 平台实现）。
 */
expect class KeychainWrapper() {
    fun set(key: String, value: String): Boolean
    fun get(key: String): String?
    fun delete(key: String): Boolean
    fun clear(): Boolean
}
