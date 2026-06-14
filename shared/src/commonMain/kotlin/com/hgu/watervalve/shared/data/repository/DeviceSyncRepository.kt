package com.hgu.watervalve.shared.data.repository

import com.hgu.watervalve.shared.data.remote.api.DeviceSyncApiService
import com.hgu.watervalve.shared.data.remote.api.RemoteDeviceDto
import com.hgu.watervalve.shared.data.remote.api.SyncDevicesRequest
import com.hgu.watervalve.shared.domain.model.Device
import io.ktor.client.plugins.ClientRequestException

class DeviceSyncRepository(
    private val syncApi: DeviceSyncApiService,
    private val localDeviceDataSource: LocalDeviceDataSource,
) {
    class BannedException(message: String) : Exception(message)

    suspend fun pullFromCloud(userId: String): Result<Int> {
        return runCatching {
            require(userId.isNotBlank()) { "userId is blank" }
            val cloudDevices = syncApi.getDevices(userId)
            localDeviceDataSource.replaceAll(cloudDevices)
            cloudDevices.size
        }.recover403(userId)
    }

    suspend fun pushToCloud(userId: String, devices: List<Device>): Result<Unit> {
        return runCatching {
            require(userId.isNotBlank()) { "userId is blank" }
            syncApi.saveDevices(
                userId,
                SyncDevicesRequest(
                    devices.mapIndexed { index, device ->
                        RemoteDeviceDto(
                            id = device.id,
                            name = device.qrUrl,
                            customName = device.name,
                            qrContent = device.qrUrl,
                            isFavorite = device.starred,
                            displayOrder = index + 1,
                            lastUsedAt = device.createdAt,
                            userId = userId,
                        )
                    }
                )
            )
            Unit
        }.recover403(userId)
    }

    suspend fun pushLocalToCloud(userId: String): Result<Unit> {
        return pushToCloud(userId, localDeviceDataSource.getAll())
    }
}

private fun <T> Result<T>.recover403(userId: String): Result<T> {
    val throwable = exceptionOrNull() ?: return this
    return if (throwable is ClientRequestException && throwable.response.status.value == 403) {
        Result.failure(DeviceSyncRepository.BannedException("User $userId is banned"))
    } else {
        Result.failure(throwable)
    }
}
