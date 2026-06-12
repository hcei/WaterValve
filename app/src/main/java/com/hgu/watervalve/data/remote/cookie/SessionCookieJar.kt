package com.hgu.watervalve.data.remote.cookie

import com.hgu.watervalve.data.local.datastore.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp [CookieJar] 实现，用于认证链路中的 SESSION Cookie 管理。
 *
 * ## 工作方式
 * - [saveFromResponse]：从响应中提取 SESSION Cookie，写入内存缓存 + 异步持久化到 DataStore
 * - [loadForRequest]：从内存缓存读取 SESSION Cookie，注入到请求
 *
 * ## 认证链中的角色
 * ```
 * casLogin  → 响应 Set-Cookie: SESSION=xxx  → saveFromResponse 缓存+持久化
 * getUisToken → 请求自动携带 Cookie: SESSION=xxx ← loadForRequest 注入
 * ```
 *
 * 只管理 `ykt.hgu.edu.cn` 域名下的 SESSION Cookie。
 * WebView 的 Cookie 同步由 Phase 4 的 UwcWebView 负责。
 */
@Singleton
class SessionCookieJar @Inject constructor(
    private val sessionManager: SessionManager,
) : CookieJar {

    private val sessionCookieName = "SESSION"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 内存缓存，避免每次 loadForRequest 都走 DataStore 的 suspend 读取 */
    @Volatile
    private var cachedCookieValue: String? = null

    init {
        // 启动时从 DataStore 恢复缓存
        scope.launch {
            cachedCookieValue = sessionManager.sessionCookie.first()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            if (cookie.name.equals(sessionCookieName, ignoreCase = true)) {
                val rawValue = cookie.value
                cachedCookieValue = rawValue
                scope.launch {
                    sessionManager.saveSessionCookie(rawValue)
                }
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!url.host.contains("ykt.hgu.edu.cn")) return emptyList()

        val value = cachedCookieValue ?: return emptyList()

        return listOf(
            Cookie.Builder()
                .name(sessionCookieName)
                .value(value)
                .domain(url.host)
                .path("/")
                .build()
        )
    }

    /** 供外部手动设置 Cookie 值 */
    fun setCookie(value: String) {
        cachedCookieValue = value
        scope.launch {
            sessionManager.saveSessionCookie(value)
        }
    }

    fun clearCookie() {
        cachedCookieValue = null
    }
}
