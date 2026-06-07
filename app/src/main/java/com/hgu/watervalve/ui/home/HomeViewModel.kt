package com.hgu.watervalve.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.data.camera.QrScanResult
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.remote.cookie.SessionCookieJar
import com.hgu.watervalve.data.repository.DeviceRepository
import com.hgu.watervalve.domain.model.Device
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {

    /** 设备列表（来自 Room，Flow 自动更新） */
    val devices: StateFlow<List<Device>> = deviceRepository.observeAll()
        .let { flow ->
            val state = MutableStateFlow<List<Device>>(emptyList())
            viewModelScope.launch { flow.collect { state.value = it } }
            state.asStateFlow()
        }

    // ═══════════════════════════════════════════════════════════
    // QR 扫描
    // ═══════════════════════════════════════════════════════════

    /**
     * 处理 QR 码扫描结果：保存为新设备。
     *
     * @return 保存成功时返回设备 id，重复时返回 null
     */
    fun onQrScanned(result: QrScanResult): String? {
        var deviceId: String? = null
        viewModelScope.launch {
            val device = deviceRepository.saveFromQrScan(result)
            deviceId = device.id
        }
        return deviceId // 注意：这是异步的，实际需要回调
    }

    /** 保存 QR 扫描结果（挂起版本，由 Composable 调用） */
    suspend fun saveQrResult(result: QrScanResult): Device {
        return deviceRepository.saveFromQrScan(result)
    }

    // ═══════════════════════════════════════════════════════════
    // 设备操作
    // ═══════════════════════════════════════════════════════════

    /** 切换收藏 */
    fun toggleFavorite(deviceId: String) {
        viewModelScope.launch {
            deviceRepository.toggleFavorite(deviceId)
        }
    }

    /** 删除设备 */
    fun deleteDevice(device: Device) {
        viewModelScope.launch {
            deviceRepository.delete(device)
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
        }
    }

    /** 退出登录 */
    fun logout() {
        viewModelScope.launch {
            cookieJar.clearCookie()
            sessionManager.clearAll()
        }
    }
}
