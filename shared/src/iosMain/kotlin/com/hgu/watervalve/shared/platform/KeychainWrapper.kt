package com.hgu.watervalve.shared.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRetain
import platform.Foundation.CFBridgingRelease
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

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, UnsafeNumber::class)
actual class KeychainWrapper actual constructor() {
    private val serviceName = "com.hgu.watervalve"

    actual fun set(key: String, value: String): Boolean {
        delete(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        return cfRetain(serviceName, key, data) { cfService, cfKey, cfData ->
            val query = cfDictionaryOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to cfService,
                kSecAttrAccount to cfKey,
                kSecValueData to cfData,
                kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            )
            SecItemAdd(query, null) == errSecSuccess
        }
    }

    actual fun get(key: String): String? = cfRetain(serviceName, key) { cfService, cfKey ->
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfKey,
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        if (status != errSecSuccess || result.value == null) {
            return@cfRetain null
        }
        val data = CFBridgingRelease(result.value) as? NSData ?: return@cfRetain null
        NSString.create(data = data, encoding = NSUTF8StringEncoding) as String?
    }

    actual fun delete(key: String): Boolean = cfRetain(serviceName, key) { cfService, cfKey ->
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
            kSecAttrAccount to cfKey,
        )
        val status = SecItemDelete(query)
        status == errSecSuccess || status == errSecItemNotFound
    }

    actual fun clear(): Boolean = cfRetain(serviceName) { cfService ->
        val query = cfDictionaryOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to cfService,
        )
        val status = SecItemDelete(query)
        status == errSecSuccess || status == errSecItemNotFound
    }
}

private fun MemScope.cfDictionaryOf(vararg items: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? {
    val keys = allocArrayOf(*items.map { it.first }.toTypedArray())
    val values = allocArrayOf(*items.map { it.second }.toTypedArray())
    return CFDictionaryCreate(
        kCFAllocatorDefault,
        keys.reinterpret(),
        values.reinterpret(),
        items.size.convert(),
        null,
        null,
    )
}

private inline fun <T> cfRetain(
    value: Any?,
    block: MemScope.(CFTypeRef?) -> T,
): T = memScoped {
    val retained = CFBridgingRetain(value)
    try {
        block(retained)
    } finally {
        CFBridgingRelease(retained)
    }
}

private inline fun <T> cfRetain(
    value1: Any?,
    value2: Any?,
    block: MemScope.(CFTypeRef?, CFTypeRef?) -> T,
): T = memScoped {
    val retained1 = CFBridgingRetain(value1)
    val retained2 = CFBridgingRetain(value2)
    try {
        block(retained1, retained2)
    } finally {
        CFBridgingRelease(retained2)
        CFBridgingRelease(retained1)
    }
}

private inline fun <T> cfRetain(
    value1: Any?,
    value2: Any?,
    value3: Any?,
    block: MemScope.(CFTypeRef?, CFTypeRef?, CFTypeRef?) -> T,
): T = memScoped {
    val retained1 = CFBridgingRetain(value1)
    val retained2 = CFBridgingRetain(value2)
    val retained3 = CFBridgingRetain(value3)
    try {
        block(retained1, retained2, retained3)
    } finally {
        CFBridgingRelease(retained3)
        CFBridgingRelease(retained2)
        CFBridgingRelease(retained1)
    }
}
