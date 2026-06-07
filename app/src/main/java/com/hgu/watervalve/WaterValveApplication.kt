package com.hgu.watervalve

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hgu.watervalve.worker.TokenRefreshWorker
import com.hgu.watervalve.worker.UpdateCheckWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class WaterValveApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        scheduleTokenRefresh()
        scheduleUpdateCheck()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * 调度 UWC Token 周期刷新任务。
     *
     * - 每 12 小时执行一次
     * - 使用 KEEP 策略避免重复调度
     */
    private fun scheduleTokenRefresh() {
        val periodicWork = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
            repeatInterval = 12,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .addTag(TokenRefreshWorker.TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TokenRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork,
        )

        Log.i("WaterValveApp", "Token 刷新任务已调度（每 12h）")
    }

    /**
     * 调度应用更新周期检查任务。
     *
     * - 每 48 小时执行一次
     * - 使用 KEEP 策略避免重复调度
     */
    private fun scheduleUpdateCheck() {
        val periodicWork = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            repeatInterval = 48,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
        )
            .addTag(UpdateCheckWorker.TAG)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork,
        )

        Log.i("WaterValveApp", "更新检查任务已调度（每 48h）")
    }
}
