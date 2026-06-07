package com.hgu.watervalve.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hgu.watervalve.domain.model.AppRelease
import kotlin.math.roundToInt

/**
 * 更新对话框。
 *
 * 根据 [UpdateUiState] 自动切换模式：
 * - FORCED:  强制更新，不可关闭，仅「立即更新」
 * - OPTIONAL: 可选更新，「先不更新」+「立即更新」
 * - DOWNLOADING: 下载进度条 + 百分比
 * - COMPLETED: 下载完成，「稍后」+「立即安装」
 * - FAILED:   下载失败，「跳过」+「重新下载」
 *
 * 只在状态为 Idle / Checking / Error 时不显示弹窗。
 */
@Composable
fun UpdateDialog(
    viewModel: UpdateViewModel,
) {
    val state by viewModel.uiState.collectAsState()

    when (val s = state) {
        is UpdateUiState.Available -> {
            if (s.isForced) {
                ForcedUpdateDialog(
                    release = s.release,
                    onUpdateClick = { viewModel.downloadUpdate() },
                )
            } else {
                OptionalUpdateDialog(
                    release = s.release,
                    onUpdateClick = { viewModel.downloadUpdate() },
                    onNotNowClick = { viewModel.dismissUpdate() },
                )
            }
        }

        is UpdateUiState.Downloading -> {
            DownloadingDialog(
                release = s.release,
                progress = s.progress,
            )
        }

        is UpdateUiState.DownloadCompleted -> {
            DownloadCompletedDialog(
                release = s.release,
                onInstallClick = { viewModel.installApk(s.apkFile) },
                onLaterClick = { viewModel.reset() },
            )
        }

        is UpdateUiState.DownloadFailed -> {
            DownloadFailedDialog(
                release = s.release,
                errorMessage = s.message,
                onRetryClick = { viewModel.downloadUpdate() },
                onSkipClick = { viewModel.skipAfterDownloadFailed() },
            )
        }

        is UpdateUiState.SuggestEnable -> {
            SuggestEnableDialog(
                release = s.release,
                onEnableClick = { viewModel.enableAndRecheck() },
                onDismiss = { viewModel.reset() },
            )
        }

        // Idle / Checking / Error → 不显示弹窗
        is UpdateUiState.Idle,
        is UpdateUiState.Checking,
        is UpdateUiState.Error,
            -> { /* 不显示 */ }
    }
}

// ═══════════════════════════════════════════════════════════
// 强制更新（不可关闭）
// ═══════════════════════════════════════════════════════════

@Composable
private fun ForcedUpdateDialog(
    release: AppRelease,
    onUpdateClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* 不可关闭 */ },
        title = {
            Text(
                "🔔 发现新版本 ${release.versionName}",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                if (release.body.isNotBlank()) {
                    Text(
                        release.body
                            .replace(Regex("\\[FORCED\\]", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("\\[MIN_VER:[^]]+]", RegexOption.IGNORE_CASE), "")
                            .trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "此版本为强制更新，请立即更新后继续使用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdateClick) {
                Text("立即更新")
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════
// 可选更新
// ═══════════════════════════════════════════════════════════

@Composable
private fun OptionalUpdateDialog(
    release: AppRelease,
    onUpdateClick: () -> Unit,
    onNotNowClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onNotNowClick,
        title = {
            Text(
                "🔔 发现新版本 ${release.versionName}",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (release.body.isNotBlank()) {
                Text(
                    release.body
                        .replace(Regex("\\[FORCED\\]", RegexOption.IGNORE_CASE), "")
                        .replace(Regex("\\[MIN_VER:[^]]+]", RegexOption.IGNORE_CASE), "")
                        .trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = {
            Button(onClick = onUpdateClick) {
                Text("立即更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onNotNowClick) {
                Text("先不更新")
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════
// 下载中
// ═══════════════════════════════════════════════════════════

@Composable
private fun DownloadingDialog(
    release: AppRelease,
    progress: Float,
) {
    AlertDialog(
        onDismissRequest = { /* 下载中不可关闭，但用户可通过 HomeScreen 切换 */ },
        title = {
            Text("⬇ 正在下载 ${release.versionName}")
        },
        text = {
            Column {
                LinearProgressIndicator(
                    progress = { if (progress >= 0f) progress else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (progress >= 0f) {
                    Text(
                        "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (release.apkSize > 0) {
                    val downloaded = (release.apkSize * progress).toLong()
                    Text(
                        "${formatSize(downloaded)} / ${formatSize(release.apkSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {}, // 无按钮
    )
}

// ═══════════════════════════════════════════════════════════
// 下载完成
// ═══════════════════════════════════════════════════════════

@Composable
private fun DownloadCompletedDialog(
    release: AppRelease,
    onInstallClick: () -> Unit,
    onLaterClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLaterClick,
        title = { Text("✅ 下载完成") },
        text = {
            Text("新版本 ${release.versionName} 已准备就绪，是否立即安装？")
        },
        confirmButton = {
            Button(onClick = onInstallClick) {
                Text("立即安装")
            }
        },
        dismissButton = {
            TextButton(onClick = onLaterClick) {
                Text("稍后")
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════
// 下载失败
// ═══════════════════════════════════════════════════════════

@Composable
private fun DownloadFailedDialog(
    release: AppRelease,
    errorMessage: String,
    onRetryClick: () -> Unit,
    onSkipClick: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkipClick,
        title = { Text("❌ 下载失败") },
        text = {
            Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            OutlinedButton(onClick = onRetryClick) {
                Text("重新下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkipClick) {
                Text("跳过")
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════
// 建议开启更新开关
// ═══════════════════════════════════════════════════════════

@Composable
private fun SuggestEnableDialog(
    release: AppRelease,
    onEnableClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "🔔 发现新版本 ${release.versionName}",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "自动检测更新已关闭，但检测到较新的版本。\n是否开启自动更新检测？",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(onClick = onEnableClick) {
                Text("开启并检查")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不开启")
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════
// 工具
// ═══════════════════════════════════════════════════════════

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
