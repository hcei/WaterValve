package com.hgu.watervalve.shared.data.repository

import com.hgu.watervalve.shared.data.local.WaterValveDb
import com.hgu.watervalve.shared.data.remote.api.BannedException
import com.hgu.watervalve.shared.data.remote.api.RemoteDeviceDto
import com.hgu.watervalve.shared.data.remote.api.SyncApi
import com.hgu.watervalve.shared.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.shared.domain.model.Device
import com.hgu.watervalve.shared.domain.model.WaterRecord
import com.hgu.watervalve.shared.platform.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DeviceRepository(
    private val syncApi: SyncApi,
    private val database: WaterValveDb,
    private val authRepository: AuthRepository,
) {
    private val mutableDevices = MutableStateFlow(loadDevices())
    private val mutableRecords = MutableStateFlow(loadRecords())

    val devices: StateFlow<List<Device>> = mutableDevices.asStateFlow()
    val records: StateFlow<List<WaterRecord>> = mutableRecords.asStateFlow()

    suspend fun addDevice(qrUrl: String): Result<Device> {
        return runCatching {
            val trimmed = qrUrl.trim()
            val id = UwcCrypto.md5(trimmed)
            val existing = mutableDevices.value.firstOrNull { it.id == id }
            val device = Device(
                id = id,
                name = existing?.name ?: id.take(8),
                qrUrl = trimmed,
                starred = existing?.starred ?: false,
                createdAt = existing?.createdAt ?: currentTimeMillis(),
            )
            database.deviceQueries.insertOrReplace(
                id = device.id,
                name = device.name,
                qrUrl = device.qrUrl,
                starred = if (device.starred) 1L else 0L,
                createdAt = device.createdAt,
            )
            refreshDevices()
            pushToCloud().getOrThrow()
            device
        }
    }

    suspend fun renameDevice(deviceId: String, name: String): Result<Unit> {
        return runCatching {
            database.deviceQueries.updateName(name.trim(), deviceId)
            refreshDevices()
            pushToCloud().getOrThrow()
        }
    }

    suspend fun starDevice(deviceId: String, starred: Boolean): Result<Unit> {
        return runCatching {
            database.deviceQueries.updateStarred(if (starred) 1L else 0L, deviceId)
            refreshDevices()
            pushToCloud().getOrThrow()
        }
    }

    suspend fun deleteDevice(deviceId: String): Result<Unit> {
        return runCatching {
            database.deviceQueries.deleteById(deviceId)
            refreshDevices()
            pushToCloud().getOrThrow()
        }
    }

    suspend fun pullFromCloud(): Result<Unit> {
        return runCatching {
            val userId = authRepository.getUserId().orEmpty()
            require(userId.isNotBlank()) { "userId 为空" }

            val localById = mutableDevices.value.associateBy { it.id }
            val remoteDevices = try {
                syncApi.getDevices(userId)
            } catch (exception: BannedException) {
                authRepository.markBanned()
                throw exception
            }

            database.deviceQueries.deleteAll()
            remoteDevices.forEachIndexed { index, remote ->
                val local = localById[remote.id]
                database.deviceQueries.insertOrReplace(
                    id = remote.id,
                    name = remote.customName.ifBlank { local?.name ?: remote.id.take(8) },
                    qrUrl = remote.qrContent,
                    starred = if (remote.isFavorite) 1L else 0L,
                    createdAt = local?.createdAt ?: remote.lastUsedAt.takeIf { it > 0 } ?: (index + 1).toLong(),
                )
            }
            refreshDevices()
        }
    }

    suspend fun pushToCloud(): Result<Unit> {
        return runCatching {
            val userId = authRepository.getUserId().orEmpty()
            require(userId.isNotBlank()) { "userId 为空" }

            val payload = mutableDevices.value.mapIndexed { index, device ->
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

            try {
                syncApi.pushDevices(userId, payload)
            } catch (exception: BannedException) {
                authRepository.markBanned()
                throw exception
            }
        }
    }

    suspend fun addRecord(deviceName: String): Result<Unit> {
        return runCatching {
            database.waterRecordQueries.insert(deviceName = deviceName, timestamp = currentTimeMillis())
            refreshRecords()
        }
    }

    suspend fun deleteRecord(id: Long): Result<Unit> {
        return runCatching {
            database.waterRecordQueries.deleteById(id)
            refreshRecords()
        }
    }

    suspend fun deleteAllRecords(): Result<Unit> {
        return runCatching {
            database.waterRecordQueries.deleteAll()
            refreshRecords()
        }
    }

    private fun refreshDevices() {
        mutableDevices.value = loadDevices()
    }

    private fun refreshRecords() {
        mutableRecords.value = loadRecords()
    }

    private fun loadDevices(): List<Device> {
        return database.deviceQueries.selectAll().executeAsList().map { row ->
            Device(
                id = row.id,
                name = row.name,
                qrUrl = row.qrUrl,
                starred = row.starred != 0L,
                createdAt = row.createdAt,
            )
        }
    }

    private fun loadRecords(): List<WaterRecord> {
        return database.waterRecordQueries.selectAll().executeAsList().map { row ->
            WaterRecord(
                id = row.id,
                deviceName = row.deviceName,
                timestamp = row.timestamp,
            )
        }
    }
}
