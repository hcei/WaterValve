package com.hgu.watervalve.ui.valve

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hgu.watervalve.data.camera.QrScanResult
import com.hgu.watervalve.ui.home.QrScanScreen
import com.hgu.watervalve.ui.webview.UwcWebView

/**
 * 开阀页：WebView 加载 SPA 开阀页 + QR 扫码桥接。
 *
 * SPA 可通过 h5call://openScan 请求原生 QR 扫码，
 * 结果通过 evaluateJavascript 回传。
 *
 * @param deviceId 目标设备 ID
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValveScreen(
    deviceId: String,
    qrContent: String = "",
    onBack: () -> Unit,
    viewModel: ValveViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val h5CallLog by viewModel.h5CallLog.collectAsState()
    var showQrScan by remember { mutableStateOf(false) }
    var pendingCallback by remember { mutableStateOf("sendScanInfo") }

    DisposableEffect(deviceId, qrContent) {
        viewModel.setDeviceId(deviceId, qrContent)
        onDispose { }
    }

    // 注册扫码请求回调
    DisposableEffect(viewModel) {
        viewModel.onQrScanRequested = {
            pendingCallback = "sendScanInfo"
            showQrScan = true
        }
        onDispose { viewModel.onQrScanRequested = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开阀控制") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // WebView 加载 SPA 开阀页
            UwcWebView(
                url = viewModel.spaUrl,
                uwcToken = viewModel.uwcToken,
                uisToken = viewModel.uisToken,
                sessionCookie = viewModel.sessionCookie,
                userInfo = viewModel.userInfo,
                onH5Call = { result -> viewModel.onH5Call(result) },
                modifier = Modifier.fillMaxSize(),
            )

            // 状态覆盖层
            when (uiState) {
                is ValveUiState.Loading -> StatusOverlay(
                    "正在加载...",
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
                is ValveUiState.Success -> StatusOverlay(
                    (uiState as ValveUiState.Success).message,
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    Color(0xFF4CAF50),
                )
                is ValveUiState.Error -> StatusOverlay(
                    (uiState as ValveUiState.Error).message,
                    Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    MaterialTheme.colorScheme.error,
                )
                else -> {}
            }

            // h5call 日志（调试）
            if (h5CallLog.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), MaterialTheme.shapes.small)
                        .padding(6.dp),
                ) {
                    h5CallLog.takeLast(6).forEach { line ->
                        Text(line,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    // ── QR 扫码覆盖层 ──
    if (showQrScan) {
        QrScanScreen(
            onScanned = { result ->
                viewModel.onQrScanCompleted(result.content, pendingCallback)
                showQrScan = false
            },
            onBack = { showQrScan = false },
        )
    }
}

@Composable
private fun StatusOverlay(
    message: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(color, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
