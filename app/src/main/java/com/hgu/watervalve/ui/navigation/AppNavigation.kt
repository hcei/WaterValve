package com.hgu.watervalve.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.ui.home.HomeScreen
import com.hgu.watervalve.ui.login.LoginScreen
import com.hgu.watervalve.ui.record.RecordScreen
import com.hgu.watervalve.ui.valve.ValveScreen
import kotlinx.serialization.Serializable

/**
 * Navigation3 路由定义。
 */
@Serializable
object LoginRoute : NavKey, java.io.Serializable

@Serializable
object HomeRoute : NavKey, java.io.Serializable

@Serializable
data class ValveRoute(val deviceId: String, val qrContent: String = "") : NavKey, java.io.Serializable

@Serializable
object RecordRoute : NavKey, java.io.Serializable

/**
 * App 导航图。
 *
 * ## 路由
 * ```
 * LoginRoute ──(认证成功)──▶ HomeRoute
 * HomeRoute  ──(点击设备)──▶ ValveRoute(deviceId)
 * HomeRoute  ──(退出登录)──▶ LoginRoute
 * ```
 *
 * ## Widget Deep-Link
 * 当 [deepLinkDeviceId] 非空时：
 * - [hasToken]=true  → 跳过登录，直接打开 ValveRoute
 * - [hasToken]=false → 正常登录后自动跳转 ValveRoute
 *
 * @param hasToken 是否已有有效 UWC Token
 * @param deepLinkDeviceId Widget 传入的目标设备 ID
 * @param deepLinkQrContent Widget 传入的 QR 内容
 */
@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    hasToken: Boolean = false,
    deepLinkDeviceId: String? = null,
    deepLinkQrContent: String = "",
    sessionManager: SessionManager? = null,
) {
    // 根据 Token 状态选择初始路由
    val initialRoute = when {
        hasToken && !deepLinkDeviceId.isNullOrBlank() -> ValveRoute(deepLinkDeviceId, deepLinkQrContent)
        hasToken -> HomeRoute
        else -> LoginRoute
    }

    val navBackStack = rememberNavBackStack(initialRoute)

    // 检查是否首次使用（用于自动弹出帮助页）
    val hasSeenOnboarding by (sessionManager?.hasSeenOnboarding?.collectAsState(initial = true) ?: remember { mutableStateOf(true) })
    val showHelpInitially = !hasSeenOnboarding

    // 如果从 deep-link 进入但未登录，保存目标直到登录完成
    val pendingDeepLink = rememberSaveable { mutableStateOf(
        if (!hasToken && !deepLinkDeviceId.isNullOrBlank())
            Pair(deepLinkDeviceId, deepLinkQrContent)
        else null
    ) }

    // 手动构建 entryProvider：路由 key → NavEntry 的映射
    val entryProvider: (NavKey) -> NavEntry<NavKey> = { key ->
        @Suppress("UNCHECKED_CAST")
        when (key) {
            is LoginRoute -> NavEntry(
                key = key,
                contentKey = key,
                metadata = emptyMap(),
            ) { route ->
                LoginScreen(
                    onLoginSuccess = {
                        navBackStack.clear()
                        // 如果有待处理的 deep-link，直接跳转开阀页
                        val pending = pendingDeepLink.value
                        if (pending != null) {
                            pendingDeepLink.value = null
                            navBackStack.add(ValveRoute(pending.first, pending.second))
                        } else {
                            navBackStack.add(HomeRoute)
                        }
                    },
                )
            }
            is HomeRoute -> NavEntry(
                key = key,
                contentKey = key,
                metadata = emptyMap(),
            ) { route ->
                HomeScreen(
                    onDeviceClick = { deviceId, qrContent ->
                        navBackStack.add(ValveRoute(deviceId, qrContent))
                    },
                    onRecordsClick = {
                        navBackStack.add(RecordRoute)
                    },
                    onLogout = {
                        navBackStack.clear()
                        navBackStack.add(LoginRoute)
                    },
                    showHelpInitially = showHelpInitially,
                )
            }
            is ValveRoute -> NavEntry(
                key = key,
                contentKey = key,
                metadata = emptyMap(),
            ) { route ->
                val valveRoute = route as ValveRoute
                ValveScreen(
                    deviceId = valveRoute.deviceId,
                    qrContent = valveRoute.qrContent,
                    onBack = {
                        navBackStack.removeLastOrNull()
                    },
                )
            }
            is RecordRoute -> NavEntry(
                key = key,
                contentKey = key,
                metadata = emptyMap(),
            ) { _ ->
                RecordScreen(
                    onBack = {
                        navBackStack.removeLastOrNull()
                    },
                )
            }
            else -> error("未注册的路由: ${key::class.simpleName}")
        } as NavEntry<NavKey>
    }

    NavDisplay(
        backStack = navBackStack,
        modifier = modifier,
        entryProvider = entryProvider,
        sceneStrategy = SinglePaneSceneStrategy(),
    )
}
