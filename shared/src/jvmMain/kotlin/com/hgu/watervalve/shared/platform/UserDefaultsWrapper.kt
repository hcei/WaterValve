package com.hgu.watervalve.shared.platform

actual class UserDefaultsWrapper actual constructor() {
    private val values = mutableMapOf<String, Any>()

    actual fun setString(key: String, value: String) {
        values[key] = value
    }

    actual fun getString(key: String): String? = values[key] as? String

    actual fun setBool(key: String, value: Boolean) {
        values[key] = value
    }

    actual fun getBool(key: String): Boolean = values[key] as? Boolean ?: false

    actual fun setLong(key: String, value: Long) {
        values[key] = value
    }

    actual fun getLong(key: String): Long = values[key] as? Long ?: 0L

    actual fun remove(key: String) {
        values.remove(key)
    }
}
