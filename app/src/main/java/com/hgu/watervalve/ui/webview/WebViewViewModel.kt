package com.hgu.watervalve.ui.webview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.remote.cookie.SessionCookieJar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SPA WebView ViewModel：管理 WebView 加载状态、Token 注入、h5call 拦截日志。
 *
 * ## 状态机
 * ```
 * Idle → Loading → Ready（SPA 加载完成）
 *                 → Error（加载失败）
 * ```
 */
@HiltViewModel
class WebViewViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val cookieJar: SessionCookieJar,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WebViewUiState>(WebViewUiState.Idle)
    val uiState: StateFlow<WebViewUiState> = _uiState.asStateFlow()

    /** h5call 拦截日志（最近 50 条） */
    private val _h5CallLog = MutableStateFlow<List<H5CallLogEntry>>(emptyList())
    val h5CallLog: StateFlow<List<H5CallLogEntry>> = _h5CallLog.asStateFlow()

    /** Token 注入信息 */
    private val _tokenInfo = MutableStateFlow<TokenInfo?>(null)
    val tokenInfo: StateFlow<TokenInfo?> = _tokenInfo.asStateFlow()

    /** 用户信息（注入到 localStorage） */
    private val _userInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val userInfo: StateFlow<Map<String, String>> = _userInfo.asStateFlow()

    init {
        loadTokenInfo()
        loadUserInfo()
    }

    private fun loadTokenInfo() {
        viewModelScope.launch {
            val uwcToken = sessionManager.uwcToken.first() ?: ""
            val uisToken = sessionManager.uisToken.first() ?: ""
            val sessionCookie = sessionManager.sessionCookie.first() ?: ""
            _tokenInfo.value = TokenInfo(
                uwcToken = uwcToken,
                uisToken = uisToken,
                sessionCookie = sessionCookie,
                hasToken = uwcToken.isNotBlank(),
            )
        }
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            val accNum = sessionManager.userAccNum.first() ?: ""
            val epId = sessionManager.userEpId.first() ?: ""
            val userId = sessionManager.userId.first() ?: ""
            val perCode = sessionManager.userPerCode.first() ?: ""
            _userInfo.value = mapOf(
                "uwcAccNum" to accNum,
                "uwcEpid" to epId,
                "uwcUserId" to userId,
                "uwcPerCode" to perCode,
            )
        }
    }

    /** WebView 开始加载 */
    fun onPageStarted(url: String) {
        _uiState.value = WebViewUiState.Loading(url)
    }

    /** WebView 加载完成 */
    fun onPageFinished(url: String) {
        _uiState.value = WebViewUiState.Ready(url)
    }

    /** WebView 加载错误 */
    fun onPageError(errorCode: Int, description: String, failingUrl: String?) {
        _uiState.value = WebViewUiState.Error(
            errorCode = errorCode,
            description = description,
            failingUrl = failingUrl,
        )
    }

    /** h5call:// 协议被拦截 */
    fun onH5CallIntercepted(result: H5CallResult) {
        val entry = H5CallLogEntry(
            timestamp = System.currentTimeMillis(),
            action = result.action,
            callback = result.callback,
            rawJson = result.rawJson,
        )
        val current = _h5CallLog.value.toMutableList()
        current.add(0, entry) // 最新的排前面
        if (current.size > 50) current.removeAt(current.lastIndex)
        _h5CallLog.value = current
    }

    /** 重置状态（页面刷新时用） */
    fun reset() {
        _uiState.value = WebViewUiState.Idle
    }

    /** 退出登录：清除所有 Token/Cookie/用户信息 */
    fun logout() {
        viewModelScope.launch {
            cookieJar.clearCookie()
            sessionManager.clearAll()
            _tokenInfo.value = null
            _userInfo.value = emptyMap()
            _uiState.value = WebViewUiState.Idle
        }
    }

}

/** WebView UI 状态 */
sealed class WebViewUiState {
    /** 初始状态 */
    data object Idle : WebViewUiState()

    /** 加载中 */
    data class Loading(val url: String) : WebViewUiState()

    /** 加载完成 */
    data class Ready(val url: String) : WebViewUiState()

    /** 加载失败 */
    data class Error(
        val errorCode: Int,
        val description: String,
        val failingUrl: String?,
    ) : WebViewUiState()
}

/** Token 信息 */
data class TokenInfo(
    val uwcToken: String,
    val uisToken: String,
    val sessionCookie: String = "",
    val hasToken: Boolean,
)

/** h5call 拦截日志条目 */
data class H5CallLogEntry(
    val timestamp: Long,
    val action: String,
    val callback: String,
    val rawJson: String,
)
