package com.hgu.watervalve.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import platform.CoreFoundation.CFBridgingRelease
import platform.CoreFoundation.CFBridgingRetain
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
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
import kotlinx.cinterop.CValuesRef

@OptIn(ExperimentalForeignApi::class)
actual class KeychainWrapper actual constructor() {
    private val serviceName = "com.hgu.watervalve"

    actual fun set(key: String, value: String): Boolean {
        delete(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName.toCFType(),
            kSecAttrAccount to key.toCFType(),
            kSecValueData to data.toCFType(),
            kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        )
        val status = SecItemAdd(query, null)
        return status == errSecSuccess
    }

    actual fun get(key: String): String? = memScoped {
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName.toCFType(),
            kSecAttrAccount to key.toCFType(),
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
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName.toCFType(),
            kSecAttrAccount to key.toCFType(),
        )
        val status = SecItemDelete(query)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    actual fun clear(): Boolean {
        val query = query(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to serviceName.toCFType(),
        )
        val status = SecItemDelete(query)
        return status == errSecSuccess || status == errSecItemNotFound
    }

    private fun query(vararg pairs: Pair<CValuesRef<*>?, CValuesRef<*>?>): CFMutableDictionaryRef? {
        val dictionary = CFDictionaryCreateMutable(null, pairs.size.toLong(), null, null)
        pairs.forEach { (key, value) ->
            CFDictionaryAddValue(dictionary, key, value)
        }
        return dictionary
    }

    private fun String.toCFType(): CFTypeRef? = CFBridgingRetain(this as NSString)

    private fun NSData.toCFType(): CFTypeRef? = CFBridgingRetain(this)
}
