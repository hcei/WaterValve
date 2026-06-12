package com.hgu.watervalve.shared.platform

expect class UserDefaultsWrapper() {
    fun setString(key: String, value: String)
    fun getString(key: String): String?
    fun setBool(key: String, value: Boolean)
    fun getBool(key: String): Boolean
    fun setLong(key: String, value: Long)
    fun getLong(key: String): Long
    fun remove(key: String)
}
