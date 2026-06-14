package com.hgu.watervalve.shared.domain.model

import kotlinx.serialization.Serializable

/**
 * 开阀记录数据类
 * @param id 自增主键
 * @param deviceName 设备名称（冗余存储）
 * @param timestamp 开阀时间 Unix timestamp (ms)
 */
@Serializable
data class WaterRecord(
    val id: Long = 0L,
    val deviceName: String,
    val timestamp: Long
)
