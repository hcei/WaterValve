package com.hgu.watervalve.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import platform.posix.gettimeofday
import platform.posix.timeval

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long = memScoped {
    val timeValue = alloc<timeval>()
    gettimeofday(timeValue.ptr, null)
    (timeValue.tv_sec * 1000L) + (timeValue.tv_usec / 1000L)
}
