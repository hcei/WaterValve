package com.hgu.watervalve.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hgu.watervalve.data.camera.QrCodeScanner
import com.hgu.watervalve.data.camera.QrScanResult

/**
 * 全屏 QR 码扫描界面。
 *
 * @param onScanned 扫码成功回调
 * @param onBack 返回回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(
    onScanned: (QrScanResult) -> Unit,
    onBack: () -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned by remember { mutableStateOf(false) }
    var scanner by remember { mutableStateOf<QrCodeScanner?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    // 相机权限
    val camPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) onBack()
    }

    DisposableEffect(lifecycleOwner) {
        camPermissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose {
            scanner?.stop()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描二维码") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { torchOn = !torchOn }) {
                        Icon(
                            imageVector = if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "手电筒",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // CameraX PreviewView
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        scanner = QrCodeScanner(
                            previewView = previewView,
                            lifecycleOwner = lifecycleOwner,
                            onScanned = { result ->
                                if (!scanned) {
                                    scanned = true
                                    onScanned(result)
                                }
                            },
                        )
                        previewView.post {
                            scanner?.start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 扫码框覆盖层
            if (!scanned) {
                ScanOverlay(
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // 底部提示
            Text(
                text = if (scanned) "扫码成功！" else "将二维码放入框内，自动识别",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

/**
 * 扫码框覆盖层（半透明背景 + 透明取景框）。
 */
@Composable
private fun ScanOverlay(modifier: Modifier = Modifier) {
    val boxSize = 250.dp
    Box(modifier = modifier) {
        // 半透明背景（用边框模拟取景框）
        Box(
            modifier = Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Transparent)
        )
        // 四角标记
        val cornerColor = MaterialTheme.colorScheme.primary
        val cornerLen = 32.dp
        val cornerW = 3.dp

        // 实际的取景框边框可通过多个 Box 实现，此处用简化方案
        Row {
            repeat(4) {
                Spacer(Modifier.width(boxSize / 4))
            }
        }
    }
}
