package com.hgu.watervalve.ui.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hgu.watervalve.R
import com.hgu.watervalve.data.camera.QrScanResult
import com.hgu.watervalve.data.repository.DeviceSaveResult
import com.hgu.watervalve.domain.model.Device
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主页：设备列表 + QR 码扫码添加。
 *
 * @param onDeviceClick 点击设备卡片回调，传入设备 id 和 qrContent
 * @param onRecordsClick 点击记录按钮回调
 * @param onLogout 退出登录回调
 * @param showHelpInitially 首次使用自动弹出帮助页（仅在首次为 true）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDeviceClick: ((deviceId: String, qrContent: String) -> Unit)? = null,
    onRecordsClick: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
    showHelpInitially: Boolean = false,
) {
    val devices by viewModel.devices.collectAsState()
    var showQrScan by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(showHelpInitially) }
    var qrErrorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 首次使用自动弹出帮助页，弹出后立即标记已读
    LaunchedEffect(showHelpInitially) {
        if (showHelpInitially) {
            showHelp = true
            viewModel.markOnboardingSeen()
        }
    }

    // ── 重命名对话框状态 ──
    var renameTarget by remember { mutableStateOf<Device?>(null) }
    var renameText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("河滴答@一键开阀器（Beta)") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    // ── 帮助/反馈 ──
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "帮助与反馈",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    if (onRecordsClick != null) {
                        IconButton(onClick = onRecordsClick) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "开阀记录",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    if (onLogout != null) {
                        IconButton(onClick = {
                            viewModel.logout()
                            onLogout()
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "退出登录",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showQrScan = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "扫码添加设备")
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (devices.isEmpty()) {
                // ── 空状态 ──
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onScanClick = { showQrScan = true },
                )
            } else {
                // ── 设备列表 ──
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp,
                    ),
                ) {
                    item {
                        Text(
                            text = "我的设备 (${devices.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            onClick = {
                                viewModel.markDeviceUsed(device.id)
                                onDeviceClick?.invoke(device.id, device.qrContent)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(device.id) },
                            onRename = {
                                renameTarget = device
                                renameText = device.customName.ifBlank { device.name }
                            },
                            onDelete = { viewModel.deleteDevice(device) },
                        )
                    }
                }
            }
        }
    }

    // ── 全屏 QR 扫码 ──
    if (showQrScan) {
        QrScanScreen(
            onScanned = { result ->
                scope.launch {
                    when (val saveResult = viewModel.saveQrResult(result)) {
                        is DeviceSaveResult.Success -> {
                            viewModel.pushToCloud()
                        }
                        is DeviceSaveResult.AlreadyExists -> { /* 已存在，静默处理 */ }
                        is DeviceSaveResult.InvalidQr -> {
                            qrErrorMessage = "扫描的二维码不是饮水机设备码，请扫描饮水机上的二维码"
                        }
                    }
                }
                showQrScan = false
            },
            onBack = { showQrScan = false },
        )
    }

    // ── 重命名对话框 ──
    if (renameTarget != null) {
        RenameDialog(
            currentName = renameText,
            onNameChange = { renameText = it },
            onConfirm = {
                val device = renameTarget ?: return@RenameDialog
                viewModel.renameDevice(device.id, renameText.trim())
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    // ── 帮助与反馈 ──
    if (showHelp) {
        HelpSheet(onDismiss = { showHelp = false })
    }

    // ── QR 扫码校验失败提示 ──
    if (qrErrorMessage != null) {
        AlertDialog(
            onDismissRequest = { qrErrorMessage = null },
            title = { Text("无法添加设备") },
            text = { Text(qrErrorMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { qrErrorMessage = null }) {
                    Text("知道了")
                }
            },
        )
    }
}

// ═══════════════════════════════════════════════════════════
// 空状态
// ═══════════════════════════════════════════════════════════

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.WaterDrop,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "还没有设备",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "扫描饮水机上的二维码添加设备",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScanClick) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("扫描二维码")
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 设备卡片
// ═══════════════════════════════════════════════════════════

@Composable
private fun DeviceCard(
    device: Device,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isFavorite)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 设备图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WaterDrop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            // 设备信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.customName.ifBlank { device.name.ifBlank { "饮水机设备" } },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (device.qrContent.isNotBlank())
                        device.qrContent.take(40) + if (device.qrContent.length > 40) "..." else ""
                    else device.id.take(16) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (device.lastUsedAt > 0) {
                    Text(
                        text = "上次使用: ${formatTime(device.lastUsedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }

            // 重命名按钮
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "重命名",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // 收藏按钮
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (device.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (device.isFavorite) "取消收藏" else "收藏",
                    tint = if (device.isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除设备",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 工具
// ═══════════════════════════════════════════════════════════

private fun formatTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        else -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

// ═══════════════════════════════════════════════════════════
// 帮助与反馈
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── 标题 ──
            Text(
                "帮助与反馈",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            // ── 使用说明 ──
            Text(
                "使用方法",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            val steps = listOf(
                "1. 首次使用需通过校园统一认证登录",
                "2. 点击右下角 ⊕ 按钮扫描饮水机二维码",
                "3. 扫描后设备自动保存到设备列表",
                "4. 点击设备卡片即可进入开阀控制页面",
                "5. 长按或点击 ✏️ 可为设备自定义名称",
                "6. 点击 ⭐ 可收藏常用设备",
                "7. 添加桌面 Widget 可一键直达开阀页",
            )
            steps.forEach { step ->
                Text(
                    step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(20.dp))

            // ── 联系开发者 ──
            Text(
                "联系开发者",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            Image(
                painter = painterResource(R.drawable.wechat_qr),
                contentDescription = "微信二维码",
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "微信扫码添加开发者",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            // ── 意见反馈 ──
            Text(
                "意见反馈",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "邮箱：1079648697@qq.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "如有问题或建议，请发送邮件至上方邮箱",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 重命名对话框
// ═══════════════════════════════════════════════════════════

@Composable
private fun RenameDialog(
    currentName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text("修改设备名称") },
        text = {
            Column {
                Text(
                    "为设备设置一个便于辨识的名称",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentName,
                    onValueChange = onNameChange,
                    label = { Text("设备名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = currentName.isNotBlank(),
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}
