package com.hgu.watervalve.ui.valve

import android.util.Log
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.local.db.WaterRecordDao
import com.hgu.watervalve.data.repository.DeviceRepository
import com.hgu.watervalve.domain.model.WaterRecord
import com.hgu.watervalve.ui.webview.H5CallResult
import com.hgu.watervalve.ui.webview.UwcJsBridge
import com.hgu.watervalve.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 开阀页 ViewModel：管理 WebView + h5call 桥接。
 *
 * SPA 通过 h5call:// 调用原生能力（如扫码），
 * 原生通过 evaluateJavascript 回传结果。
 *
 * @param deviceId 目标设备 ID
 */
@HiltViewModel
class ValveViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val sessionManager: SessionManager,
    private val waterRecordDao: WaterRecordDao,
) : ViewModel() {

    companion object {
        private const val TAG = "ValveViewModel"
    }

    /** WebView 引用（由 ValveScreen 设置） */
    var webView: WebView? = null

    /** 目标设备 ID */
    private var deviceId: String = ""

    private val _uiState = MutableStateFlow<ValveUiState>(ValveUiState.Idle)
    val uiState: StateFlow<ValveUiState> = _uiState.asStateFlow()

    private val _h5CallLog = MutableStateFlow<List<String>>(emptyList())
    val h5CallLog: StateFlow<List<String>> = _h5CallLog.asStateFlow()

    /** 目标设备的 QR 内容（优先作为 SPA URL） */
    private var deviceQrContent: String = ""

    /** SPA 加载的 URL：优先用 QR 内容中的 URL，否则用通用地址 */
    val spaUrl: String
        get() {
            if (deviceQrContent.startsWith("http://") || deviceQrContent.startsWith("https://")) {
                return deviceQrContent
            }
            // 如果不是 URL，可能是设备 ID，附加到通用 URL
            return if (deviceQrContent.isNotBlank())
                "${Constants.H5_OPEN_VALVE}?deviceId=$deviceQrContent"
            else
                Constants.H5_OPEN_VALVE
        }

    /** 用户信息 */
    val userInfo: Map<String, String> get() = _userInfo
    private var _userInfo: Map<String, String> = emptyMap()

    val uwcToken: String get() = _uwcToken
    val uisToken: String get() = _uisToken
    val sessionCookie: String get() = _sessionCookie
    private var _uwcToken: String = ""
    private var _uisToken: String = ""
    private var _sessionCookie: String = ""

    /** 扫码请求回调（由 ValveScreen 设置，当 SPA 请求扫码时触发） */
    var onQrScanRequested: (() -> Unit)? = null

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uwcToken = sessionManager.uwcToken.first() ?: ""
            _uisToken = sessionManager.uisToken.first() ?: ""
            _sessionCookie = sessionManager.sessionCookie.first() ?: ""
            _userInfo = mapOf(
                "uwcAccNum" to (sessionManager.userAccNum.first() ?: ""),
                "uwcEpid" to (sessionManager.userEpId.first() ?: ""),
                "uwcUserId" to (sessionManager.userId.first() ?: ""),
                "uwcPerCode" to (sessionManager.userPerCode.first() ?: ""),
            )
        }
    }

    fun setDeviceId(id: String, qrContent: String = "") {
        deviceId = id
        deviceQrContent = qrContent
        Log.d(TAG, "设备: id=$id, qrContent=$qrContent")
        viewModelScope.launch {
            deviceRepository.updateLastUsed(id)
            // 记录开阀操作
            val device = deviceRepository.getById(id)
            val deviceName = device?.customName?.ifBlank { device?.name } ?: "饮水机设备"
            waterRecordDao.insert(
                WaterRecord(
                    deviceId = id,
                    deviceName = deviceName,
                    action = "access",
                    result = "success",
                    message = "打开设备控制页",
                )
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    // h5call 拦截
    // ═══════════════════════════════════════════════════════════

    /**
     * 处理来自 WebView 的 h5call:// 拦截。
     */
    fun onH5Call(result: H5CallResult) {
        Log.d(TAG, "h5call: action=${result.action}, callback=${result.callback}")
        addLog("→ ${result.action}")

        when (result.action) {
            "openScan" -> {
                // SPA 请求扫码 → 触发原生 QR 扫描
                addLog("  SPA 请求扫码，打开相机...")
                onQrScanRequested?.invoke()
            }
            "closeWin" -> {
                addLog("  SPA 请求关闭窗口")
            }
            "setNativeHeadColor" -> { /* 忽略 */ }
            else -> {
                Log.w(TAG, "未处理的 h5call: ${result.action}")
                addLog("⚠ 未处理: ${result.action}")
            }
        }
    }

    /**
     * 当原生 QR 扫码完成后，将结果回传给 SPA。
     */
    fun onQrScanCompleted(content: String, callbackName: String = "sendScanInfo") {
        val js = UwcJsBridge.buildCallbackJs(callbackName, mapOf(
            "content" to content,
            "success" to true,
        ))
        webView?.post {
            webView?.evaluateJavascript(js, null)
        }
        addLog("← $callbackName: $content")
    }

    private fun addLog(msg: String) {
        val current = _h5CallLog.value.toMutableList()
        current.add(msg)
        if (current.size > 60) current.removeAt(0)
        _h5CallLog.value = current
    }

    override fun onCleared() {
        super.onCleared()
        webView = null
    }
}

/** 开阀页 UI 状态 */
sealed class ValveUiState {
    data object Idle : ValveUiState()
    data object Loading : ValveUiState()
    data class Success(val message: String) : ValveUiState()
    data class Error(val message: String) : ValveUiState()
}
