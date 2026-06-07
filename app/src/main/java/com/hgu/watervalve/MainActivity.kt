package com.hgu.watervalve

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.ui.navigation.AppNavigation
import com.hgu.watervalve.ui.theme.WaterValveTheme
import com.hgu.watervalve.widget.ValveWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否从 Widget deep-link 启动
        val launchDeviceId = intent?.getStringExtra(ValveWidget.EXTRA_DEVICE_ID)
        val launchQrContent = intent?.getStringExtra(ValveWidget.EXTRA_QR_CONTENT) ?: ""

        // 检查是否已有有效 Token（用于跳过登录）
        val hasToken = runBlocking {
            !sessionManager.uwcToken.firstOrNull().isNullOrBlank()
        }

        enableEdgeToEdge()
        setContent {
            WaterValveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(
                        hasToken = hasToken,
                        deepLinkDeviceId = launchDeviceId,
                        deepLinkQrContent = launchQrContent,
                        sessionManager = sessionManager,
                    )
                }
            }
        }
    }

    /**
     * Widget 点击复用已有 Activity 时（singleTop 启动模式），
     * 通过 onNewIntent 接收新的 deep-link。
     */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val launchDeviceId = intent.getStringExtra(ValveWidget.EXTRA_DEVICE_ID)
        val launchQrContent = intent.getStringExtra(ValveWidget.EXTRA_QR_CONTENT) ?: ""

        val hasToken = runBlocking {
            !sessionManager.uwcToken.firstOrNull().isNullOrBlank()
        }

        setContent {
            WaterValveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(
                        hasToken = hasToken,
                        deepLinkDeviceId = launchDeviceId,
                        deepLinkQrContent = launchQrContent,
                        sessionManager = sessionManager,
                    )
                }
            }
        }
    }
}
