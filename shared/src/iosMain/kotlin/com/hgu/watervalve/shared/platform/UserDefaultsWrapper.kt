package com.hgu.watervalve.shared.platform

import platform.Foundation.NSUserDefaults
import platform.Foundation.stringForKey

actual class UserDefaultsWrapper actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun setString(key: String, value: String) {
        defaults.setObject(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getString(key: String): String? = defaults.stringForKey(key)

    actual fun setBool(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getBool(key: String): Boolean = defaults.boolForKey(key)

    actual fun setLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
        defaults.synchronize()
    }

    actual fun getLong(key: String): Long = defaults.integerForKey(key)

    actual fun remove(key: String) {
        defaults.removeObjectForKey(key)
        defaults.synchronize()
    }
}
