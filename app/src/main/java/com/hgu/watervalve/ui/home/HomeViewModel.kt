package com.hgu.watervalve.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.data.camera.QrScanResult
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.local.db.WaterRecordDao
import com.hgu.watervalve.data.remote.cookie.SessionCookieJar
import com.hgu.watervalve.data.repository.DeviceRepository
import com.hgu.watervalve.data.repository.DeviceSaveResult
import com.hgu.watervalve.data.repository.DeviceSyncRepository
import com.hgu.watervalve.domain.model.Device
import com.hgu.watervalve.data.repository.DeviceSyncRepository.BannedException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * 主页 ViewModel：管理设备列表、QR 扫码添加、收藏/删除。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val sessionManager: SessionManager,
    private val cookieJar: SessionCookieJar,
    private val deviceSyncRepository: DeviceSyncRepository,
    private val waterRecordDao: WaterRecordDao,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    /** 设备列表（来自 Room，Flow 自动更新） */
    val devices: StateFlow<List<Device>> = deviceRepository.observeAll()
        .let { flow ->
            val state = MutableStateFlow<List<Device>>(emptyList())
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    /** 用户是否已被封禁 */
    private val _isBanned = MutableStateFlow(false)
    val isBanned: StateFlow<Boolean> = _isBanned.asStateFlow()

    init {
        // 进入主页时从云端拉取设备
        pullFromCloud()
    }

    // ═══════════════════════════════════════════════════════════
    // QR 扫描
    // ═══════════════════════════════════════════════════════════

    /**
     * 处理 QR 码扫描结果：保存为新设备。
     *
     * @return 保存成功时返回设备 id，重复时返回 null
     */
    @Deprecated("使用 saveQrResult 替代", ReplaceWith("saveQrResult(result)"))
    fun onQrScanned(result: QrScanResult): String? {
        var deviceId: String? = null
        viewModelScope.launch {
            when (val saveResult = deviceRepository.saveFromQrScan(result)) {
                is DeviceSaveResult.Success -> deviceId = saveResult.device.id
                is DeviceSaveResult.AlreadyExists -> deviceId = saveResult.device.id
                is DeviceSaveResult.InvalidQr -> { /* 无效码，不保存 */ }
            }
        }
        return deviceId
    }

    /** 保存 QR 扫描结果（挂起版本，由 Composable 调用） */
    suspend fun saveQrResult(result: QrScanResult): DeviceSaveResult {
        return deviceRepository.saveFromQrScan(result)
    }

    // ═══════════════════════════════════════════════════════════
    // 设备操作
    // ═══════════════════════════════════════════════════════════

    /** 切换收藏 */
    fun toggleFavorite(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.toggleFavorite(deviceId)
            pushToCloud()
        }
    }

    /** 删除设备 */
    fun deleteDevice(device: Device) {
        viewModelScope.launch {
            deviceRepository.delete(device)
            pushToCloud()
        }
    }

    /** 更新设备使用时间 */
    fun markDeviceUsed(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.updateLastUsed(deviceId)
        }
    }

    /** 重命名设备 */
    fun renameDevice(deviceId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            deviceRepository.updateCustomName(deviceId, newName)
            pushToCloud()
        }
    }

    /**
     * 清除 App 全部本地数据（相当于重新安装）。
     *
     * 清除范围：
     * - Room 数据库（设备 + 用水记录）
     * - DataStore（Token、用户信息、偏好设置）
     * - OkHttp Cookie 缓存
     * - WebView 数据（Cookie、Storage、Cache）
     * - App 内部缓存目录
     *
     * 设备列表存储在服务端，重新登录后会自动从云端拉回。
     */
    suspend fun clearAllAppData() {
        withContext(Dispatchers.IO) {
            // 1. 清除 Room 数据库
            deviceRepository.deleteAll()
            waterRecordDao.deleteAll()

            // 2. 清除 DataStore
            sessionManager.clearAll()

            // 3. 清除 OkHttp Cookie 缓存
            cookieJar.clearCookie()

            // 4. 清除 WebView 数据
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()

            // 5. 清除 App 内部缓存目录
            try {
                appContext.cacheDir.deleteRecursively()
            } catch (_: Exception) {
                // 缓存目录清理失败不影响核心功能
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 云端同步
    // ═══════════════════════════════════════════════════════════

    /** 从云端拉取设备并合并到本地 */
    private fun pullFromCloud() {
        viewModelScope.launch {
            val userId = sessionManager.userId.first() ?: return@launch
            deviceSyncRepository.pullFromCloud(userId)
                .onFailure { e ->
                    if (e is BannedException) {
                        _isBanned.value = true
                        sessionManager.setBanned(true)
                    }
                }
        }
    }

    /** 推送当前设备列表到云端 */
    fun pushToCloud() {
        viewModelScope.launch {
            val userId = sessionManager.userId.first() ?: return@launch
            val currentDevices = devices.value
            deviceSyncRepository.pushToCloud(userId, currentDevices)
                .onFailure { e ->
                    if (e is BannedException) {
                        _isBanned.value = true
                        sessionManager.setBanned(true)
                    }
                }
        }
    }

    /** 标记首次引导已查看 */
    fun markOnboardingSeen() {
        viewModelScope.launch {
            sessionManager.markOnboardingSeen()
        }
    }
}
