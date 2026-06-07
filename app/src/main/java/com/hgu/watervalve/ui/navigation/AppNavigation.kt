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
import kotlinx.serialization.Serializable

/**
 * Navigation3 路由定义。
 */
@Serializable
object LoginRoute : NavKey

@Serializable
object HomeRoute : NavKey

/**
 * App 导航图。
 *
 * 路由：
 * ```
 * LoginRoute ──(认证成功)──▶ HomeRoute
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
                        navBackStack.add(HomeRoute)
                    },
                )
            }
            is HomeRoute -> NavEntry(
                key = key,
                contentKey = key,
                metadata = emptyMap(),
            ) { route ->
                HomeScreen()
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
