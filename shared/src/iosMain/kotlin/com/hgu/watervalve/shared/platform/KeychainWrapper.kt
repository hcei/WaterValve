@file:Suppress("CAST_NEVER_SUCCEEDS")

package com.hgu.watervalve.shared.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class KeychainWrapper actual constructor() {
    private val serviceName = "com.hgu.watervalve"

    actual fun set(key: String, value: String): Boolean {
        delete(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        val query = buildQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key,
            kSecValueData to data,
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        )
        val status = SecItemAdd(query, null)
        return status == errSecSuccess
    }

    actual fun get(key: String): String? = memScoped {
        val query = buildQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess || result.value == null) {
            return null
        }
        val data = CFBridgingRelease(result.value) as? NSData ?: return null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    actual fun delete(key: String): Boolean {
        val query = buildQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
            kSecAttrAccount to key,
        )
        val status = SecItemDelete(query)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    actual fun clear(): Boolean {
        val query = buildQuery(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName,
        )
        val status = SecItemDelete(query)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    private fun buildQuery(vararg pairs: Pair<Any?, Any?>): CFDictionaryRef? {
        val keys = pairs.map { it.first }
        val values = pairs.map { it.second }
        return NSDictionary.dictionaryWithObjects(objects = values, forKeys = keys) as CFDictionaryRef
    }
}
