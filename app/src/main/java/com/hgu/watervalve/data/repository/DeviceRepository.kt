package com.hgu.watervalve.data.repository

import com.hgu.watervalve.data.camera.QrScanResult
import com.hgu.watervalve.data.local.db.DeviceDao
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.remote.api.UwcApiService
import com.hgu.watervalve.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.domain.model.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设备仓库：管理 Room 本地设备存储 + 系统配置 API。
 *
 * ## 职责
 * - 设备 CRUD（Room DeviceDao 封装）
 * - QR 码扫描结果持久化
 * - queryCustom API 获取用水系统配置
 * - 收藏/排序管理
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val deviceDao: DeviceDao,
    private val api: UwcApiService,
    private val sessionManager: SessionManager,
) {

    /** 观察所有设备（按 displayOrder 排序） */
    fun observeAll(): Flow<List<Device>> = deviceDao.observeAll()

    /** 观察收藏的设备 */
    fun observeFavorites(): Flow<List<Device>> = deviceDao.observeFavorites()

    /** 根据 id 获取设备 */
    suspend fun getById(id: String): Device? = deviceDao.getById(id)

    /**
     * 将 QR 码扫描结果保存为新设备。
     *
     * 以 QR 码内容的 MD5 作为设备 id，避免重复。
     * 若已存在同内容设备则不重复添加。
     * **扫描前会校验 QR 内容是否为合法的饮水机设备码**，
     * 非饮水机设备码返回 [DeviceSaveResult.InvalidQr]。
     *
     * @param result QR 码扫描结果
     * @return [DeviceSaveResult.Success] 含 Device，[DeviceSaveResult.AlreadyExists] 已存在，
     *         [DeviceSaveResult.InvalidQr] 非饮水机设备码
     */
    suspend fun saveFromQrScan(result: QrScanResult): DeviceSaveResult = withContext(Dispatchers.IO) {
        // ── 校验 QR 内容是否为饮水机设备码 ──
        if (!isValidDeviceQr(result.content)) {
            return@withContext DeviceSaveResult.InvalidQr(result.content)
        }

        val deviceId = md5(result.content)
        val existing = deviceDao.getById(deviceId)
        if (existing != null) {
            DeviceSaveResult.AlreadyExists(existing)
        } else {
            val displayName = extractDeviceName(result.content)
            val currentUserId = sessionManager.userId.first() ?: ""
            val device = Device(
                id = deviceId,
                qrContent = result.content,
                name = displayName,
                displayOrder = (deviceDao.getMaxDisplayOrder() ?: 0) + 1,
                userId = currentUserId,
            )
            deviceDao.upsert(device)
            DeviceSaveResult.Success(device)
        }
    }

    /** 更新设备自定义名称 */
    suspend fun updateCustomName(deviceId: String, customName: String) {
        val device = deviceDao.getById(deviceId) ?: return
        deviceDao.update(device.copy(customName = customName))
    }

    /** 切换收藏状态 */
    suspend fun toggleFavorite(deviceId: String) {
        val device = deviceDao.getById(deviceId) ?: return
        deviceDao.update(device.copy(isFavorite = !device.isFavorite))
    }

    /** 更新最后使用时间 */
    suspend fun updateLastUsed(deviceId: String) {
        val device = deviceDao.getById(deviceId) ?: return
        deviceDao.update(device.copy(lastUsedAt = System.currentTimeMillis()))
    }

    /** 删除设备 */
    suspend fun delete(device: Device) = deviceDao.delete(device)

    /** 删除所有设备 */
    suspend fun deleteAll() = deviceDao.deleteAll()

    /**
     * 调用 queryCustom API 获取用水系统配置。
     *
     * @return data 字段的 Map（如 allowUseWaterImmediately 等）
     */
    suspend fun fetchSystemConfig(): Result<Map<String, Any?>> = withContext(Dispatchers.IO) {
        try {
            val uwcToken = sessionManager.uwcToken.first() ?: ""
            if (uwcToken.isBlank()) return@withContext Result.failure(
                IllegalStateException("未登录")
            )

            val epId = sessionManager.userEpId.first() ?: "1"

            val paramStr = UwcCrypto.buildParamStr(mapOf("epId" to epId))
            val response = api.queryCustom(
                uwcToken = uwcToken,
                timestamp = UwcCrypto.generateTimestamp().toString(),
                nonce = UwcCrypto.generateNonce(),
                paramStr = paramStr,
            )

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException("queryCustom 失败 (HTTP ${response.code()})")
                )
            }

            val body = response.body()
            val resultMap = body?.get("resultMap") as? String
            if (resultMap.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException("响应中未找到 resultMap")
                )
            }

            val decrypted = UwcCrypto.decryptResponse(resultMap)
            val data = UwcCrypto.parseDataField(decrypted)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 工具方法
    // ═══════════════════════════════════════════════════════════

    /** 从 QR 内容中尝试提取可读设备名 */
    private fun extractDeviceName(qrContent: String): String {
        // 尝试解析 URL 参数中的 deviceName
        val nameParam = Regex("[?&]deviceName=([^&]+)").find(qrContent)?.groupValues?.get(1)
        if (!nameParam.isNullOrBlank()) return nameParam

        // 尝试解析 URL 路径最后一段
        val lastPath = qrContent.substringAfterLast("/").substringBefore("?")
        if (lastPath.isNotBlank() && lastPath.length < 30) return lastPath

        // 直接用 QR 内容的前 20 字符
        return qrContent.take(20) + if (qrContent.length > 20) "..." else ""
    }

    /**
     * 校验 QR 内容是否为合法的饮水机设备码。
     *
     * 合法格式：
     * - URL 包含 `ykt.hgu.edu.cn/uwc_webapp`（学校饮水机 SPA 地址）
     * - 以 `DEV-` 开头的设备 ID
     */
    private fun isValidDeviceQr(qrContent: String): Boolean {
        if (qrContent.isBlank()) return false
        // 学校饮水机 SPA URL
        if (qrContent.contains("ykt.hgu.edu.cn/uwc_webapp", ignoreCase = true)) return true
        // 设备 ID 格式
        if (qrContent.startsWith("DEV-", ignoreCase = true)) return true
        return false
    }

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

/**
 * QR 扫码保存设备的结果。
 */
sealed class DeviceSaveResult {
    /** 保存成功 */
    data class Success(val device: Device) : DeviceSaveResult()

    /** 设备已存在（同 QR 码已扫描过） */
    data class AlreadyExists(val device: Device) : DeviceSaveResult()

    /** 非饮水机设备码，拒绝保存 */
    data class InvalidQr(val qrContent: String) : DeviceSaveResult()
}
