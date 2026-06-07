package com.hgu.watervalve.domain.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 饮水机设备实体。
 *
 * @param id 唯一标识（QR 码内容 hash 或设备 ID）
 * @param qrContent QR 码原始内容（URL、设备 ID 等）
 * @param name 设备名称（从 QR 码解析或用户自定义）
 * @param customName 用户自定义名称
 * @param macAddress 设备 MAC 地址（如有）
 * @param rssi 最后一次扫描的信号强度 (dBm)
 * @param displayOrder 排序权重（越小越靠前）
 * @param isFavorite 是否收藏
 * @param lastUsedAt 最后一次开阀时间戳
 */
@Entity(tableName = "device")
data class Device(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "qr_content") val qrContent: String = "",
    val name: String = "",
    val customName: String = "",
    @ColumnInfo(name = "mac_address") val macAddress: String = "",
    val rssi: Int = 0,
    val displayOrder: Int = 0,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long = 0L,
)
