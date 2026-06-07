package com.hgu.watervalve.data.repository

import android.util.Log
import com.hgu.watervalve.data.local.db.DeviceDao
import com.hgu.watervalve.data.remote.api.DeviceSyncApiService
import com.hgu.watervalve.data.remote.api.SyncDevicesRequest
import com.hgu.watervalve.domain.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备云端同步仓库。
 *
 * 负责在本地 Room DB 和云端 sync_server 之间同步设备列表。
 *
 * ## 同步策略
 * - **拉取（Pull）**：登录成功后从云端拉取设备覆盖本地
 * - **推送（Push）**：设备列表变更后推送本地到云端（全量替换）
 */
@Singleton
class DeviceSyncRepository @Inject constructor(
    private val syncApi: DeviceSyncApiService,
    private val deviceDao: DeviceDao,
) {
    companion object {
        private const val TAG = "DeviceSyncRepo"
    }

    /**
     * 从云端拉取设备列表并合并到本地。
     *
     * 合并策略：云端有但本地没有的设备 → 添加到本地；
     * 本地已有设备保留本地数据（本地优先）。
     *
     * @param userId 当前登录用户 ID
     * @return 合并后的设备数量
     */
    suspend fun pullFromCloud(userId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank()) return@withContext Result.failure(
                IllegalStateException("userId 为空")
            )

            val cloudDevices = syncApi.getDevices(userId)
            Log.d(TAG, "从云端拉取到 ${cloudDevices.size} 个设备")

            // 合并：云端设备写入本地（以 id 去重）
            val localDevices = deviceDao.getAllSync()
            val localIds = localDevices.map { it.id }.toSet()

            var added = 0
            for (device in cloudDevices) {
                if (device.id !in localIds) {
                    // Gson 反序列化可能将缺失字段设为 null（绕过 Kotlin null-safety）
                    // 安全构造新的 Device 实例，所有字段用 ?: "" 兜底
                    val normalized = try {
                        Device(
                            id = device.id,
                            qrContent = device.qrContent ?: "",
                            name = device.name ?: "",
                            customName = device.customName ?: "",
                            macAddress = device.macAddress ?: "",
                            rssi = device.rssi,
                            displayOrder = device.displayOrder,
                            isFavorite = device.isFavorite,
                            lastUsedAt = device.lastUsedAt,
                            userId = userId,
                        )
                    } catch (_: Exception) {
                        Device(id = device.id, userId = userId)
                    }
                    deviceDao.upsert(normalized)
                    added++
                }
            }
            Log.d(TAG, "合并完成：新增 $added 个设备")
            Result.success(added)
        } catch (e: Exception) {
            Log.w(TAG, "从云端拉取失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 推送本地设备列表到云端（全量替换）。
     *
     * @param userId 当前登录用户 ID
     * @param devices 要推送的设备列表
     */
    suspend fun pushToCloud(userId: String, devices: List<Device>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (userId.isBlank()) return@withContext Result.failure(
                IllegalStateException("userId 为空")
            )

            val request = SyncDevicesRequest(devices = devices)
            syncApi.saveDevices(userId, request)
            Log.d(TAG, "推送完成：${devices.size} 个设备已同步到云端")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "推送到云端失败: ${e.message}", e)
            Result.failure(e)
        }
    }
}
