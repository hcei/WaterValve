package com.hgu.watervalve.ui.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.hgu.watervalve.util.Constants
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * SPA WebView 组件。
 * 通过 shouldInterceptRequest 在 HTML 中注入 WeChat Mock + Token，
 * 确保 Mock 在 SPA 的 app.js 之前执行。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun UwcWebView(
    url: String = Constants.SPA_URL,
    uwcToken: String = "",
    uisToken: String = "",
    sessionCookie: String = "",
    userInfo: Map<String, String> = emptyMap(),
    onPageStarted: ((String) -> Unit)? = null,
    onPageFinished: ((String) -> Unit)? = null,
    onPageError: ((Int, String, String?) -> Unit)? = null,
    onH5Call: ((H5CallResult) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mockJs = UwcJsBridge.buildTokenInjectionJs(uwcToken, uisToken, userInfo)

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.WHITE)
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

                webViewClient = UwcWebViewClient(
                    mockJs = mockJs,
                    onH5Call = onH5Call,
                    onPageStarted = onPageStarted,
                    onPageFinished = onPageFinished,
                    onPageError = onPageError,
                )

                webChromeClient = WebChromeClient()

                addOnLayoutChangeListener(object : android.view.View.OnLayoutChangeListener {
                    override fun onLayoutChange(
                        v: android.view.View?, l: Int, t: Int, r: Int, b: Int,
                        ol: Int, ot: Int, or: Int, ob: Int,
                    ) {
                        if (width > 0 && height > 0) {
                            removeOnLayoutChangeListener(this)
                            // 清除 Service Worker 缓存，强制网络请求以便 shouldInterceptRequest 注入
                            clearServiceWorkerCache()
                            injectCookie(sessionCookie)
                            loadUrl(url)
                        }
                    }
                })
            }
        },
        modifier = modifier,
    )
}

/** 清除 Service Worker 缓存，强制走网络请求以触发 shouldInterceptRequest */
private fun WebView.clearServiceWorkerCache() {
    try {
        clearHistory()
        clearCache(true)
        android.webkit.WebStorage.getInstance().deleteAllData()
    } catch (e: Exception) {
        android.util.Log.w("UwcWebView", "Clear SW cache failed: ${e.message}")
    }
}

private fun injectCookie(sessionCookie: String) {
    if (sessionCookie.isBlank()) return
    try {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setCookie(Constants.SPA_URL, "SESSION=$sessionCookie; domain=.hgu.edu.cn; path=/; secure")
        cm.setCookie("https://cas.hgu.edu.cn/", "SESSION=$sessionCookie; domain=.hgu.edu.cn; path=/; secure")
        cm.flush()
    } catch (e: Exception) {
        android.util.Log.w("UwcWebView", "Cookie injection failed: ${e.message}")
    }
}

/**
 * WebViewClient：通过 shouldInterceptRequest 在 HTML 中注入 Mock 脚本。
 */
private class UwcWebViewClient(
    private val mockJs: String,
    private val onH5Call: ((H5CallResult) -> Unit)?,
    private val onPageStarted: ((String) -> Unit)?,
    private val onPageFinished: ((String) -> Unit)?,
    private val onPageError: ((Int, String, String?) -> Unit)?,
) : WebViewClient() {

    /**
     * 拦截主 HTML 文档请求，注入 Mock 脚本。
     * 对 uwc_webapp 的 HTML 响应在 </head> 前插入 <script>mockJs</script>。
     */
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val reqUrl = request?.url?.toString() ?: return null

        // 仅拦截 SPA 主页面（不含 JS/CSS/图片等子资源）
        if (!reqUrl.matches(Regex("https://ykt\\.hgu\\.edu\\.cn/uwc_webapp/?($|\\?.*)"))) {
            return null
        }
        if (mockJs.isBlank()) return null

        return try {
            val conn = URL(reqUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", Constants.USER_AGENT)
            // 携带 WebView 的 SESSION Cookie
            val cookie = CookieManager.getInstance().getCookie(reqUrl)
            if (cookie != null) conn.setRequestProperty("Cookie", cookie)

            val html = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // 在 </head> 前注入 <script>
            val mockScript = "<script>$mockJs</script>"
            val modified = html.replaceFirst("</head>", "$mockScript</head>")

            val mimeType = conn.contentType ?: "text/html"
            val encoding = mimeType.substringAfter("charset=", "UTF-8")
            WebResourceResponse(
                "text/html",
                encoding,
                ByteArrayInputStream(modified.toByteArray(Charsets.UTF_8))
            )
        } catch (e: Exception) {
            android.util.Log.w("UwcWebView", "HTML injection failed: ${e.message}")
            null
        }
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?,
        request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url?.toString() ?: return false
        if (url.startsWith(Constants.H5CALL_SCHEME)) {
            UwcJsBridge.parseUrl(url)?.let { onH5Call?.invoke(it) }
            return true
        }
        return if (url.startsWith("http://") || url.startsWith("https://")) false else true
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        val urlStr = url ?: return false
        if (urlStr.startsWith(Constants.H5CALL_SCHEME)) {
            UwcJsBridge.parseUrl(urlStr)?.let { onH5Call?.invoke(it) }
            return true
        }
        return if (urlStr.startsWith("http://") || urlStr.startsWith("https://")) false else true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { onPageStarted?.invoke(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let { onPageFinished?.invoke(it) }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use newer onReceivedError")
    override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
        onPageError?.invoke(errorCode, description ?: "Unknown error", failingUrl)
    }
}
