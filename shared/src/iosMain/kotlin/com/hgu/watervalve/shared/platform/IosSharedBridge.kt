package com.hgu.watervalve.shared.platform

import com.hgu.watervalve.shared.data.local.WaterValveDb
import com.hgu.watervalve.shared.data.remote.api.ReleaseApi
import com.hgu.watervalve.shared.data.remote.api.SyncApi
import com.hgu.watervalve.shared.data.remote.api.UwcApi
import com.hgu.watervalve.shared.data.remote.api.UpdateApiService
import com.hgu.watervalve.shared.data.repository.AuthRepository
import com.hgu.watervalve.shared.data.repository.DeviceRepository
import com.hgu.watervalve.shared.data.repository.UpdateRepository
import com.hgu.watervalve.shared.domain.model.AppRelease
import com.hgu.watervalve.shared.domain.model.Device
import com.hgu.watervalve.shared.domain.model.WaterRecord
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

class IosSharedBridge {
    private val httpClient = HttpClient(Darwin)
    private val keychain = KeychainWrapper()
    private val userDefaults = UserDefaultsWrapper()
    private val database = WaterValveDb(DatabaseDriverFactory().createDriver())
    private val uwcApi = UwcApi(httpClient)
    private val syncApi = SyncApi(httpClient)
    private val releaseApi = ReleaseApi(httpClient)
    private val updateApiService = UpdateApiService(releaseApi)

    val authRepository = AuthRepository(
        uwcApi = uwcApi,
        keychain = keychain,
        userDefaults = userDefaults,
    )

    val deviceRepository = DeviceRepository(
        syncApi = syncApi,
        database = database,
        authRepository = authRepository,
    )

    val updateRepository = UpdateRepository(updateApiService)

    fun currentSessionSnapshot(): IosSessionSnapshot? {
        val userId = authRepository.getUserId().orEmpty()
        val uwcToken = authRepository.getUwcToken().orEmpty()
        val uisToken = keychain.get(com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_UIS_JWT).orEmpty()
        if (userId.isBlank() || uwcToken.isBlank() || uisToken.isBlank()) {
            return null
        }
        return IosSessionSnapshot(
            userId = userId,
            nickname = userDefaults.getString(com.hgu.watervalve.shared.util.Constants.UD_KEY_NICKNAME).orEmpty(),
            accNum = userDefaults.getString(com.hgu.watervalve.shared.util.Constants.UD_KEY_USER_ACC_NUM).orEmpty(),
            epId = userDefaults.getString(com.hgu.watervalve.shared.util.Constants.UD_KEY_USER_EP_ID).orEmpty(),
            perCode = userDefaults.getString(com.hgu.watervalve.shared.util.Constants.UD_KEY_USER_PER_CODE).orEmpty(),
            uisToken = uisToken,
            uwcToken = uwcToken,
            sessionCookie = keychain.get(com.hgu.watervalve.shared.util.Constants.KEYCHAIN_KEY_SESSION_COOKIE).orEmpty(),
        )
    }

    fun currentDeviceSnapshot(): List<IosDeviceSnapshot> {
        @Suppress("UNCHECKED_CAST")
        val devices = deviceRepository.devices.value as? List<Device> ?: emptyList()
        return devices.map(Device::toSnapshot)
    }

    fun currentRecordSnapshot(): List<IosWaterRecordSnapshot> {
        @Suppress("UNCHECKED_CAST")
        val records = deviceRepository.records.value as? List<WaterRecord> ?: emptyList()
        return records.map(WaterRecord::toSnapshot)
    }

    fun currentBannedState(): Boolean = authRepository.isBanned.value as? Boolean ?: false

    fun clearBannedState() {
        userDefaults.setBool(com.hgu.watervalve.shared.util.Constants.UD_KEY_IS_BANNED, false)
    }

    fun currentLoginState(): IosLoginStateSnapshot {
        return when (val state = authRepository.loginState.value) {
            is com.hgu.watervalve.shared.data.repository.LoginState.Loading -> IosLoginStateSnapshot(
                phase = "loading",
                step = state.step.toLong(),
                message = state.message,
            )

            is com.hgu.watervalve.shared.data.repository.LoginState.Success -> IosLoginStateSnapshot(
                phase = "success",
                step = 3L,
                message = "success",
            )

            is com.hgu.watervalve.shared.data.repository.LoginState.Failed -> IosLoginStateSnapshot(
                phase = "failed",
                step = 0L,
                message = state.error.name,
            )

            else -> IosLoginStateSnapshot(
                phase = "idle",
                step = 0L,
                message = "",
            )
        }
    }

    fun latestReleaseSnapshot(currentVersion: String, release: AppRelease): IosAppReleaseSnapshot {
        return IosAppReleaseSnapshot(
            tagName = release.tagName,
            versionName = release.versionName,
            body = release.body,
            downloadUrl = release.downloadUrl,
            isForced = release.isForced,
            minToleratedVersion = release.minToleratedVersion.orEmpty(),
            isNewerThanCurrent = updateRepository.isNewerVersion(currentVersion, release.tagName),
        )
    }

    suspend fun addDevice(qrUrl: String): IosDeviceSnapshot {
        return deviceRepository.addDevice(qrUrl).getOrThrow().toSnapshot()
    }

    suspend fun renameDevice(deviceId: String, name: String) {
        deviceRepository.renameDevice(deviceId, name).getOrThrow()
    }

    suspend fun starDevice(deviceId: String, starred: Boolean) {
        deviceRepository.starDevice(deviceId, starred).getOrThrow()
    }

    suspend fun deleteDevice(deviceId: String) {
        deviceRepository.deleteDevice(deviceId).getOrThrow()
    }

    suspend fun pullFromCloud() {
        deviceRepository.pullFromCloud().getOrThrow()
    }

    suspend fun pushToCloud() {
        deviceRepository.pushToCloud().getOrThrow()
    }

    suspend fun addRecord(deviceName: String) {
        deviceRepository.addRecord(deviceName).getOrThrow()
    }

    suspend fun deleteRecord(id: Long) {
        deviceRepository.deleteRecord(id).getOrThrow()
    }

    suspend fun deleteAllRecords() {
        deviceRepository.deleteAllRecords().getOrThrow()
    }
}

private fun Device.toSnapshot(): IosDeviceSnapshot {
    return IosDeviceSnapshot(
        id = id,
        name = name,
        qrUrl = qrUrl,
        starred = starred,
        createdAt = createdAt,
    )
}

private fun WaterRecord.toSnapshot(): IosWaterRecordSnapshot {
    return IosWaterRecordSnapshot(
        id = id,
        deviceName = deviceName,
        timestamp = timestamp,
    )
}

data class IosDeviceSnapshot(
    val id: String,
    val name: String,
    val qrUrl: String,
    val starred: Boolean,
    val createdAt: Long,
)

data class IosSessionSnapshot(
    val userId: String,
    val nickname: String,
    val accNum: String,
    val epId: String,
    val perCode: String,
    val uisToken: String,
    val uwcToken: String,
    val sessionCookie: String,
)

data class IosWaterRecordSnapshot(
    val id: Long,
    val deviceName: String,
    val timestamp: Long,
)

data class IosLoginStateSnapshot(
    val phase: String,
    val step: Long,
    val message: String,
)

data class IosAppReleaseSnapshot(
    val tagName: String,
    val versionName: String,
    val body: String,
    val downloadUrl: String,
    val isForced: Boolean,
    val minToleratedVersion: String,
    val isNewerThanCurrent: Boolean,
)
