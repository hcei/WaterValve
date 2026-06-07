package com.hgu.watervalve.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hgu.watervalve.BuildConfig
import com.hgu.watervalve.MainActivity
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.repository.UpdateRepository
import kotlinx.coroutines.flow.first
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager Worker：每 48 小时检查应用更新。
 *
 * - 开关关闭 → 静默跳过
 * - 有新版本（可选/强制）→ 发送通知
 * - 无新版本 → 静默成功
 */
@HiltWorker
class UpdateCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: UpdateRepository,
    private val sessionManager: SessionManager,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "UpdateCheckWorker"
        const val WORK_NAME = "update_check"
        const val CHANNEL_ID = "update_channel"
        const val NOTIFICATION_ID = 2001
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "开始后台更新检查...")

        try {
            // 开关关闭 → 跳过
            val autoCheck = sessionManager.autoCheckUpdate.first()
            if (!autoCheck) {
                Log.d(TAG, "自动检测更新已关闭，跳过")
                return Result.success()
            }

            val release = repository.fetchLatestRelease()
            if (release == null) {
                Log.d(TAG, "无可用更新源，跳过")
                return Result.success()
            }

            // 版本比较
            if (!repository.isNewerVersion(BuildConfig.VERSION_NAME, release.versionName)) {
                Log.d(TAG, "已是最新版本")
                sessionManager.setLastUpdateCheckTime(System.currentTimeMillis())
                return Result.success()
            }

            // 预发布且非强制 → 静默
            if (release.isPrerelease && !release.isForced) {
                Log.d(TAG, "预发布版本，静默跳过")
                return Result.success()
            }

            sessionManager.setLastUpdateCheckTime(System.currentTimeMillis())

            // 发送通知
            val title = if (release.isForced) {
                "强制更新：v${release.versionName}"
            } else {
                "发现新版本 v${release.versionName}"
            }
            val body = release.name.ifBlank { "点击查看更新内容" }
            sendNotification(title, body, release.isForced)

            Log.i(TAG, "更新通知已发送: $title")
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "更新检查异常: ${e.message}", e)
            return Result.retry()
        }
    }

    private fun sendNotification(title: String, body: String, isForced: Boolean) {
        val context = applicationContext

        // 创建通知渠道（Android 8+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "应用更新",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "应用版本更新通知"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        // 点击通知打开 MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // 检查通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "缺少通知权限，跳过发送通知")
                return
            }
        }

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "发送通知失败: ${e.message}")
        }
    }
}
