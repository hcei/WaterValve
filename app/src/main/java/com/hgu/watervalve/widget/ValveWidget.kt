package com.hgu.watervalve.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.room.Room
import com.hgu.watervalve.MainActivity
import com.hgu.watervalve.data.local.db.AppDatabase
import com.hgu.watervalve.domain.model.Device
import kotlinx.coroutines.flow.firstOrNull

/**
 * 一键开阀桌面 Widget（Glance）。
 *
 * 展示收藏设备列表，点击直接跳转 App 开阀页。
 */
class ValveWidget : GlanceAppWidget() {

    companion object {
        private const val TAG = "ValveWidget"
        const val EXTRA_DEVICE_ID = "widget_device_id"
        const val EXTRA_QR_CONTENT = "widget_qr_content"

        @Volatile
        private var widgetDb: AppDatabase? = null

        @Synchronized
        private fun getWidgetDatabase(context: Context): AppDatabase {
            return widgetDb ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "watervalve.db"
            )
                .fallbackToDestructiveMigration()
                .build()
                .also { widgetDb = it }
        }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val favorites = loadFavoriteDevices(context)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .appWidgetBackground()
                        .background(GlanceTheme.colors.surface)
                        .padding(12),
                ) {
                    // 标题
                    Text(
                        text = "河滴答@一键开阀器",
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onSurface,
                        ),
                    )

                    if (favorites.isEmpty()) {
                        Spacer(modifier = GlanceModifier.padding(8))
                        Text(
                            text = "暂无收藏设备",
                            style = TextStyle(color = GlanceTheme.colors.onSurface),
                        )
                    } else {
                        favorites.take(4).forEach { device ->
                            Spacer(modifier = GlanceModifier.padding(4))
                            Row(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .clickable(actionStartActivity(
                                        Intent(context, MainActivity::class.java).apply {
                                            putExtra(EXTRA_DEVICE_ID, device.id)
                                            putExtra(EXTRA_QR_CONTENT, device.qrContent)
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        }
                                    ))
                                    .background(GlanceTheme.colors.surface)
                                    .padding(8),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = device.customName.ifBlank { device.name.ifBlank { "饮水机设备" } },
                                    style = TextStyle(
                                        fontWeight = FontWeight.Medium,
                                        color = GlanceTheme.colors.onSurface,
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadFavoriteDevices(context: Context): List<Device> {
        return try {
            val db = getWidgetDatabase(context)
            kotlinx.coroutines.runBlocking {
                db.deviceDao().observeFavorites().firstOrNull() ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载设备失败: ${e.message}", e)
            emptyList()
        }
    }
}
