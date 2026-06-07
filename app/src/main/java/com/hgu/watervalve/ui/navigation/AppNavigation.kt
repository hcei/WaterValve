package com.hgu.watervalve.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import com.hgu.watervalve.ui.home.HomeScreen
import com.hgu.watervalve.ui.login.LoginScreen
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

/**
 * App 导航图。
 *
 * 路由：
 * ```
 * LoginRoute ──(认证成功)──▶ HomeRoute
 * HomeRoute  ──(点击设备)──▶ ValveRoute(deviceId)
 * ```
 */
@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val navBackStack = rememberNavBackStack(LoginRoute)

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
                        navBackStack.add(HomeRoute)
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
                    onLogout = {
                        navBackStack.clear()
                        navBackStack.add(LoginRoute)
                    },
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
