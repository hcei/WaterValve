package com.hgu.watervalve.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.hgu.watervalve.util.Constants

/**
 * 登录页：WebView 承载 CAS 登录 UI + 原生层认证。
 *
 * ## 流程
 * 1. WebView 加载 CAS 登录页（Chrome UA）
 * 2. 用户在 WebView 中输入学号/密码/短信验证码
 * 3. CAS 回调 → URL 变更为 `?ticket=ST-xxxxx`
 * 4. 原生层拦截 ticket → [LoginViewModel.onTicketReceived]
 * 5. AuthRepository 完成三步认证
 * 6. 成功 → `onLoginSuccess` 回调，导航到主页
 *
 * @param onLoginSuccess 认证成功后回调（由 AppNavigation 处理导航）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // 认证成功后触发导航
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("校园认证") },
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
            // WebView CAS 登录
            CasWebView(
                modifier = Modifier.fillMaxSize(),
                onTicketIntercepted = { ticket ->
                    viewModel.onTicketReceived(ticket)
                },
            )

            // 认证中遮罩
            if (uiState is LoginUiState.Authenticating) {
                AuthenticatingOverlay()
            }
        }
    }

    // 错误弹窗
    if (uiState is LoginUiState.Error) {
        val error = uiState as LoginUiState.Error
        AlertDialog(
            onDismissRequest = { viewModel.reset() },
            title = { Text("认证失败") },
            text = {
                Column {
                    Text(error.message)
                    if (error.stage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "失败阶段: ${error.stage}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.reset() }) {
                    Text("重试")
                }
            },
        )
    }
}

/**
 * 加载 CAS 登录 URL 的 WebView 组件。
 *
 * - 设置 Chrome Android UA（避免微信 OAuth 死循环）
 * - 拦截 URL 中的 `?ticket=ST-` 参数
 * - 安全加固：禁用文件/内容访问
 * - 使用 `post` 延迟加载确保 WebView 已完成首次布局，避免 CAS 页面 JS 读到 0x0 尺寸
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CasWebView(
    modifier: Modifier,
    onTicketIntercepted: (String) -> Unit,
) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.WHITE)
                // 显式设置 LayoutParams 确保 WebView 填充父容器
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )

                @SuppressLint("SetJavaScriptEnabled")
                with(settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    userAgentString = Constants.USER_AGENT
                    allowFileAccess = false
                    allowContentAccess = false
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    displayZoomControls = false
                    layoutAlgorithm = android.webkit.WebSettings.LayoutAlgorithm.NORMAL
                }

                webViewClient = CasWebViewClient(onTicketIntercepted)
                webChromeClient = WebChromeClient()

                // 等待 WebView 完成首次 layout（宽高 > 0）后再加载，避免页面读到 0x0 尺寸
                addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: android.view.View?, l: Int, t: Int, r: Int, b: Int,
                        ol: Int, ot: Int, or: Int, ob: Int,
                    ) {
                        if (width > 0 && height > 0) {
                            removeOnLayoutChangeListener(this)
                            loadUrl(Constants.CAS_LOGIN_URL)
                        }
                    }
                })
            }
        },
        modifier = modifier,
    )
}

/**
 * CAS 登录专用 WebViewClient：拦截 ticket 回调。
 *
 * CAS 登录成功后 URL 变更为：
 * `https://ykt.hgu.edu.cn/uwc_web_app/?ticket=ST-xxxxx`
 *
 * 拦截该 URL，提取 ticket 参数并传递给认证流程。
 */
private class CasWebViewClient(
    private val onTicketIntercepted: (String) -> Unit,
) : WebViewClient() {

    private var ticketConsumed = false

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        return tryInterceptTicket(url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        return tryInterceptTicket(url ?: "")
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        tryInterceptTicket(url ?: "")
    }

    private fun tryInterceptTicket(url: String): Boolean {
        if (ticketConsumed) return false

        // 检测 URL 中是否包含 ?ticket=ST- 参数
        val ticketParam = url.substringAfter("?ticket=", "")
            .substringBefore("&")
            .substringBefore("#")

        if (ticketParam.startsWith("ST-")) {
            ticketConsumed = true
            onTicketIntercepted(ticketParam)
            return true // 阻止 WebView 继续加载 SPA（避免 SPA 错误处理 ticket）
        }
        return false
    }
}

/**
 * 认证中的加载遮罩。
 */
@Composable
private fun AuthenticatingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "正在验证身份...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "第 1/3 步：CAS 认证中",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
