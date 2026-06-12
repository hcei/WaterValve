package com.hgu.watervalve.shared.platform

/**
 * JVM 平台的 KeychainWrapper no-op 实现（仅用于本地编译验证）。
 * JVM 桌面环境没有 Keychain，所有操作返回 false/null。
 */
actual class KeychainWrapper actual constructor() {
    private val store = mutableMapOf<String, String>()

    actual fun set(key: String, value: String): Boolean {
        store[key] = value
        return true
    }

    actual fun get(key: String): String? = store[key]

    actual fun delete(key: String): Boolean {
        store.remove(key)
        return true
    }

    actual fun clear(): Boolean {
        store.clear()
        return true
    }
}
