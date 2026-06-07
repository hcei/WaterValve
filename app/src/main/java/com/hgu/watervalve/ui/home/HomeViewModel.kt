package com.hgu.watervalve.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.data.camera.QrScanResult
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.remote.cookie.SessionCookieJar
import com.hgu.watervalve.data.repository.DeviceRepository
import com.hgu.watervalve.data.repository.DeviceSaveResult
import com.hgu.watervalve.data.repository.DeviceSyncRepository
import com.hgu.watervalve.domain.model.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
) : ViewModel() {

    /** 设备列表（来自 Room，Flow 自动更新） */
    val devices: StateFlow<List<Device>> = deviceRepository.observeAll()
        .let { flow ->
            val state = MutableStateFlow<List<Device>>(emptyList())
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

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

    /** 退出登录 */
    fun logout() {
        viewModelScope.launch {
            cookieJar.clearCookie()
            sessionManager.clearAll()
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
        }
    }

    /** 推送当前设备列表到云端 */
    fun pushToCloud() {
        viewModelScope.launch {
            val userId = sessionManager.userId.first() ?: return@launch
            val currentDevices = devices.value
            deviceSyncRepository.pushToCloud(userId, currentDevices)
        }
    }

    /** 标记首次引导已查看 */
    fun markOnboardingSeen() {
        viewModelScope.launch {
            sessionManager.markOnboardingSeen()
        }
    }
}
