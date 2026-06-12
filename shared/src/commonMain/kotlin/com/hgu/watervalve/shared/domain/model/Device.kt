package com.hgu.watervalve.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * 设备数据类
 * @param id QR 内容 MD5（32位十六进制小写），主键
 * @param name 用户自定义名称，默认用 id 前 8 位
 * @param qrUrl 原始 QR 码内容
 * @param starred 星标状态
 * @param createdAt 创建时间 Unix timestamp (ms)
 */
@Serializable
data class Device(
    val id: String,
    val name: String,
    val qrUrl: String,
    val starred: Boolean = false,
    val createdAt: Long = 0L
)
