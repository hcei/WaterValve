package com.hgu.watervalve.shared.data.remote.api

import com.hgu.watervalve.shared.domain.model.Device

class DeviceSyncApiService(
    private val syncApi: SyncApi,
) {
    suspend fun getDevices(userId: String): List<Device> {
        return syncApi.getDevices(userId).map { dto ->
            Device(
                id = dto.id,
                name = dto.customName.ifBlank { dto.id.take(8) },
                qrUrl = dto.qrContent,
                starred = dto.isFavorite,
                createdAt = dto.lastUsedAt,
            )
        }
    }

    suspend fun saveDevices(
        userId: String,
        body: SyncDevicesRequest,
    ): Map<String, Any?> {
        syncApi.pushDevices(userId, body.devices)
        return mapOf("status" to "ok", "count" to body.devices.size)
    }
}
