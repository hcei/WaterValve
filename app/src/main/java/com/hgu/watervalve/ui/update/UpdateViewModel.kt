package com.hgu.watervalve.ui.update

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hgu.watervalve.BuildConfig
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.repository.AllMirrorsFailedException
import com.hgu.watervalve.data.repository.UpdateRepository
import com.hgu.watervalve.domain.model.AppRelease
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * 更新检查结果：是否应该弹出更新对话框。
 */
sealed interface UpdateDecision {
    /** 静默跳过（无更新 / 相同版本 / 预发布） */
    data object Silent : UpdateDecision

    /** 强制更新（对话框不可关闭） */
    data class ForceShow(val release: AppRelease) : UpdateDecision

    /** 可选更新 */
    data class OptionalShow(val release: AppRelease) : UpdateDecision

    /** 开关关闭且当前版本 ≥ 最低容忍 → 建议开启更新开关 */
    data class SuggestEnable(val release: AppRelease) : UpdateDecision
}

/**
 * 更新 UI 状态机。
 */
sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: AppRelease, val isForced: Boolean) : UpdateUiState
    data class SuggestEnable(val release: AppRelease) : UpdateUiState
    data class Downloading(val progress: Float, val release: AppRelease) : UpdateUiState
    data class DownloadCompleted(val apkFile: File, val release: AppRelease) : UpdateUiState
    data class DownloadFailed(val message: String, val release: AppRelease) : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

/**
 * 更新 ViewModel：管理版本检查 → 下载 → 安装全流程。
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
    private val sessionManager: SessionManager,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    companion object {
        private const val TAG = "UpdateVM"
    }

    private val _uiState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    /** 自动检测更新开关状态（供 HelpSheet 读取） */
    val autoCheckUpdate: StateFlow<Boolean> = sessionManager.autoCheckUpdate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** 当前已决定展示的 Release（下载失败时复用） */
    private var currentRelease: AppRelease? = null

    // ═══════════════════════════════════════════════════════════
    // 公共 API
    // ═══════════════════════════════════════════════════════════

    /**
     * 检查更新（应用完整决策矩阵）。
     *
     * @param ignoreCooldown 是否跳过 1h 冷却（手动检查时为 true）
     */
    fun checkForUpdate(ignoreCooldown: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Checking

            try {
                // 冷却检查（除非手动触发或  pending reminder）
                if (!ignoreCooldown) {
                    val pendingReminder = sessionManager.pendingUpdateReminder.first()
                    val lastCheck = sessionManager.lastUpdateCheckTime.first()
                    val now = System.currentTimeMillis()
                    if (!pendingReminder && lastCheck > 0 && (now - lastCheck) < com.hgu.watervalve.util.Constants.UPDATE_CHECK_COOLDOWN_MS) {
                        Log.d(TAG, "距上次检查不足 1h，跳过")
                        _uiState.value = UpdateUiState.Idle
                        return@launch
                    }
                }

                // 清除 pending reminder（只要进入了检查流程）
                sessionManager.setPendingUpdateReminder(false)

                val release = repository.fetchLatestRelease()
                if (release == null) {
                    Log.d(TAG, "无可用更新源")
                    _uiState.value = UpdateUiState.Idle
                    return@launch
                }

                sessionManager.setLastUpdateCheckTime(System.currentTimeMillis())

                val decision = evaluateDecision(release)
                when (decision) {
                    is UpdateDecision.Silent -> {
                        Log.d(TAG, "决策：静默跳过 (forced=${release.isForced})")
                        _uiState.value = UpdateUiState.Idle
                    }
                    is UpdateDecision.ForceShow -> {
                        Log.i(TAG, "决策：强制更新 ${release.versionName}")
                        currentRelease = release
                        _uiState.value = UpdateUiState.Available(release, isForced = true)
                    }
                    is UpdateDecision.OptionalShow -> {
                        Log.i(TAG, "决策：可选更新 ${release.versionName}")
                        currentRelease = release
                        _uiState.value = UpdateUiState.Available(release, isForced = false)
                    }
                    is UpdateDecision.SuggestEnable -> {
                        Log.i(TAG, "决策：建议开启更新开关 (v${release.versionName})")
                        currentRelease = release
                        _uiState.value = UpdateUiState.SuggestEnable(release)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "检查更新异常: ${e.message}", e)
                _uiState.value = UpdateUiState.Error(e.message ?: "检查更新失败")
            }
        }
    }

    /** 开始下载 APK */
    fun downloadUpdate() {
        val release = currentRelease ?: return
        viewModelScope.launch {
            _uiState.value = UpdateUiState.Downloading(0f, release)

            try {
                repository.downloadApk(release.tagName, release.apkAssetUrl).collect { progress ->
                    if (progress.isComplete) {
                        val file = repository.getDestFile(release.tagName)
                        _uiState.value = UpdateUiState.DownloadCompleted(file, release)
                    } else {
                        _uiState.value = UpdateUiState.Downloading(progress.fraction, release)
                    }
                }
            } catch (e: AllMirrorsFailedException) {
                Log.e(TAG, "下载失败: ${e.message}")
                _uiState.value = UpdateUiState.DownloadFailed(
                    "无法下载更新包，请检查网络连接\n\n${e.details()}",
                    release,
                )
            } catch (e: Exception) {
                Log.e(TAG, "下载失败: ${e.message}", e)
                _uiState.value = UpdateUiState.DownloadFailed(
                    "下载失败: ${e.message}",
                    release,
                )
            }
        }
    }

    /** 下载失败后跳过（记录 pending reminder，下次启动立即提醒） */
    fun skipAfterDownloadFailed() {
        viewModelScope.launch {
            sessionManager.setPendingUpdateReminder(true)
            _uiState.value = UpdateUiState.Idle
        }
    }

    /** 用户选择「先不更新」，关闭弹窗 */
    fun dismissUpdate() {
        _uiState.value = UpdateUiState.Idle
    }

    /** 调起系统安装器安装 APK */
    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.update_provider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** 切换自动检测更新开关 */
    fun toggleAutoCheckUpdate(enabled: Boolean) {
        viewModelScope.launch {
            sessionManager.setAutoCheckUpdate(enabled)
        }
    }

    /** 手动检查更新（忽略冷却和 pending reminder） */
    fun manualCheck() {
        checkForUpdate(ignoreCooldown = true)
    }

    /** 开启自动检测更新开关并重新检查 */
    fun enableAndRecheck() {
        viewModelScope.launch {
            sessionManager.setAutoCheckUpdate(true)
            checkForUpdate(ignoreCooldown = true)
        }
    }

    /** 重置到 Idle（用于外部关闭弹窗） */
    fun reset() {
        _uiState.value = UpdateUiState.Idle
    }

    // ═══════════════════════════════════════════════════════════
    // 决策矩阵
    // ═══════════════════════════════════════════════════════════

    private suspend fun evaluateDecision(release: AppRelease): UpdateDecision {
        // 版本比较
        val currentVersion = BuildConfig.VERSION_NAME
        if (!repository.isNewerVersion(currentVersion, release.versionName)) {
            return UpdateDecision.Silent
        }

        // 预发布默认不提示（除非手动触发，由 ignoreCooldown 覆盖）
        // 这里简单处理：预发布且非强制则静默
        if (release.isPrerelease && !release.isForced) {
            return UpdateDecision.Silent
        }

        // 强制更新：绕开所有限制
        if (release.isForced) {
            return UpdateDecision.ForceShow(release)
        }

        // 自动检测开关关闭
        val autoCheck = sessionManager.autoCheckUpdate.first()
        if (!autoCheck) {
            val minVer = release.minToleratedVersion
            if (!repository.isNewerVersion(currentVersion, minVer)) {
                // current >= minTolerated → 建议开启更新开关
                return UpdateDecision.SuggestEnable(release)
            }
            // current < minTolerated → 仍需提醒
        }

        return UpdateDecision.OptionalShow(release)
    }
}
