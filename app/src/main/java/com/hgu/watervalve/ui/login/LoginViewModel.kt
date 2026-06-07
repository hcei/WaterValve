package com.hgu.watervalve.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.data.repository.AuthRepository
import com.hgu.watervalve.data.repository.AuthResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录页 ViewModel：管理 CAS WebView 认证 → Token 兑换的完整流程。
 *
 * ## 状态机
 * ```
 * Idle ── ticket 拦截 ──▶ Authenticating ──▶ Success / Error ──▶ Idle（重试）
 * ```
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * 收到 CAS 回调 ticket 后调用，执行完整认证链。
     *
     * @param ticket CAS 返回的 ST-xxxxx 格式 ticket
     */
    fun onTicketReceived(ticket: String) {
        if (_uiState.value is LoginUiState.Authenticating) return

        _uiState.value = LoginUiState.Authenticating

        viewModelScope.launch {
            when (val result = authRepository.authenticate(ticket)) {
                is AuthResult.Success -> {
                    _uiState.value = LoginUiState.Success(
                        accNum = result.accNum,
                        uwcToken = result.uwcToken,
                    )
                }
                is AuthResult.Error -> {
                    _uiState.value = LoginUiState.Error(
                        message = result.message,
                        stage = result.stage.name,
                    )
                }
            }
        }
    }

    /** 重置状态以允许重试 */
    fun reset() {
        _uiState.value = LoginUiState.Idle
    }
}

/** 登录 UI 状态 */
sealed class LoginUiState {
    /** 初始状态：WebView 加载 CAS 登录页，等待用户操作 */
    data object Idle : LoginUiState()

    /** 认证中：ticket 已拦截，正在调用 API */
    data object Authenticating : LoginUiState()

    /** 认证成功：已获取 UWC Token */
    data class Success(
        val accNum: String,
        val uwcToken: String,
    ) : LoginUiState()

    /** 认证失败 */
    data class Error(
        val message: String,
        val stage: String = "",
    ) : LoginUiState()
}
