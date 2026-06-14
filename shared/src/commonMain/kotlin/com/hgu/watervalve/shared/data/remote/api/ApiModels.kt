package com.hgu.watervalve.shared.data.remote.api

import com.hgu.watervalve.shared.domain.model.AppRelease
import com.hgu.watervalve.shared.domain.model.UserInfo
import kotlinx.serialization.Serializable

@Serializable
data class RemoteDeviceDto(
    val id: String,
    val name: String = "",
    val customName: String = "",
    val qrContent: String,
    val isFavorite: Boolean = false,
    val displayOrder: Int = 0,
    val lastUsedAt: Long = 0L,
    val macAddress: String = "",
    val rssi: Int = 0,
    val userId: String = "",
)

@Serializable
data class SyncDevicesRequest(
    val devices: List<RemoteDeviceDto>,
)

data class CasSessionExchange(
    val sessionCookie: String,
)

data class LoginByTokenPayload(
    val token: String,
    val userInfo: UserInfo,
    val accNum: String = "",
    val epId: String = "",
    val perCode: String = "",
    val rawData: Map<String, Any?> = emptyMap(),
)

data class QueryCustomPayload(
    val rawData: Map<String, Any?>,
)

data class SysInfoPayload(
    val rawData: Map<String, Any?>,
)

data class ReleasePayload(
    val release: AppRelease,
    val releasePageUrl: String = "",
)

class BannedException(message: String = "账号已被封禁，无法同步设备") : Exception(message)

class ApiContractException(message: String) : Exception(message)
