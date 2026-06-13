package com.hgu.watervalve.shared.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
actual fun currentTimeMillis(): Long = time(null).toLong() * 1000L
