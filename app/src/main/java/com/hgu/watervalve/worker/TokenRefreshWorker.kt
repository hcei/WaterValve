package com.hgu.watervalve.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hgu.watervalve.data.repository.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * WorkManager Worker：定期刷新 UWC Token。
 *
 * ## 触发时机
 * - 应用启动时（OneTimeWorkRequest + ExistingWorkPolicy.KEEP）
 * - 每 12 小时周期执行
 *
 * ## 策略
 * - 读取持久化的 UIS JWT（约 2 年有效）
 * - 调用 loginByToken → 获取新 UWC Token（约 1 天有效）
 * - 新的 UWC Token 写回 DataStore
 * - UWC Token 过期时可通过此 Worker 在后台静默刷新
 */
@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val TAG = "TokenRefreshWorker"
        const val WORK_NAME = "uwc_token_refresh"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "开始刷新 UWC Token...")

        return try {
            val result = authRepository.refreshUwcToken()
            result.fold(
                onSuccess = { token ->
                    Log.i(TAG, "UWC Token 刷新成功 (${token.take(16)}...)")
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "UWC Token 刷新失败: ${error.message}", error)
                    // UIS Token 可能也过期了，标记为重试
                    Result.retry()
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "刷新异常: ${e.message}", e)
            Result.retry()
        }
    }
}
