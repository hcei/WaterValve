package com.hgu.watervalve.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.google.gson.Gson
import com.hgu.watervalve.data.remote.api.UpdateApiService
import com.hgu.watervalve.domain.model.AppRelease
import com.hgu.watervalve.domain.model.GitHubReleaseResponse
import com.hgu.watervalve.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用更新仓库：版本检查 + 多源 APK 下载。
 *
 * ## 检查链路
 * GitHub API → 失败 → PythonAnywhere 代理
 *
 * ## 下载链路（依次尝试）
 * 1) GitHub asset 直链
 * 2) Gitee 镜像
 * 3) PythonAnywhere 代理
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val updateApiService: UpdateApiService,
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "UpdateRepo"
        private const val FORCED_MARKER = "[FORCED]"
        private const val MIN_VER_MARKER = "[MIN_VER:"
    }

    // ═══════════════════════════════════════════════════════════
    // 版本检查
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取最新 Release 信息。
     *
     * 优先从 GitHub API 获取，失败时回退到 PythonAnywhere 代理。
     *
     * @return [AppRelease] 或 null（两个源都不可用时）
     */
    suspend fun fetchLatestRelease(): AppRelease? = withContext(Dispatchers.IO) {
        // ① 尝试 GitHub API
        try {
            val response = updateApiService.getLatestRelease()
            val release = parseResponse(response)
            if (release != null) {
                Log.d(TAG, "GitHub API: 获取到最新版本 ${release.versionName}")
                return@withContext release
            }
        } catch (e: Exception) {
            Log.w(TAG, "GitHub API 失败: ${e.message}")
        }

        // ② 回退 PythonAnywhere 代理
        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder()
                .url(Constants.PROXY_RELEASE_API)
                .header("Accept", "application/json")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return@withContext null
                val gson = Gson()
                val githubResp = gson.fromJson(body, GitHubReleaseResponse::class.java)
                val release = parseResponse(githubResp)
                if (release != null) {
                    Log.d(TAG, "代理 API: 获取到最新版本 ${release.versionName}")
                    return@withContext release
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "代理 API 失败: ${e.message}")
        }

        Log.e(TAG, "所有更新源均不可用")
        null
    }

    /**
     * 比较两个版本号（语义化版本）。
     *
     * 支持格式: "1.0" / "1.0.1" / "v1.0.1"
     *
     * @return true 如果 [latest] 比 [current] 更新
     */
    fun isNewerVersion(current: String, latest: String): Boolean {
        val cur = parseSemVer(current) ?: return false
        val lat = parseSemVer(latest) ?: return false
        for (i in 0 until maxOf(cur.size, lat.size)) {
            val c = cur.getOrElse(i) { 0 }
            val l = lat.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false // 相同版本
    }

    /** 语义化版本解析：去 v 前缀 → 按 . 分割 → 转整数列表 */
    private fun parseSemVer(version: String): List<Int>? {
        val cleaned = version.trimStart('v', 'V').trim()
        if (cleaned.isEmpty()) return null
        return try {
            cleaned.split(".").map { it.toInt() }
        } catch (_: NumberFormatException) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // APK 下载（多源）
    // ═══════════════════════════════════════════════════════════

    /**
     * 下载进度。
     *
     * @param bytesRead    已下载字节数
     * @param totalBytes   总字节数（可能为 -1 表示未知）
     * @param isComplete   是否下载完毕
     */
    data class DownloadProgress(
        val bytesRead: Long,
        val totalBytes: Long,
        val isComplete: Boolean = false,
    ) {
        val fraction: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
    }

    /** 生成下载目标文件 */
    fun getDestFile(tag: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.cacheDir
        dir.mkdirs()
        return File(dir, "update_${tag}.apk")
    }

    /**
     * 多源下载 APK，逐个尝试直到成功。
     *
     * @return [Flow] 发出下载进度，完成时 [DownloadProgress.isComplete]=true
     * @throws AllMirrorsFailedException 所有源均失败
     */
    fun downloadApk(tag: String, assetUrl: String): Flow<DownloadProgress> = callbackFlow {
        val destFile = getDestFile(tag)

        val urls = buildDownloadUrls(tag, assetUrl)
        val failures = mutableListOf<Pair<String, String>>()
        var succeeded = false

        for ((label, url) in urls) {
            if (succeeded) break
            Log.d(TAG, "尝试下载: $label → $url")
            try {
                downloadSingle(url, destFile) { progress ->
                    trySend(progress)
                }
                succeeded = true
                Log.i(TAG, "下载成功: $label")
            } catch (e: Exception) {
                Log.w(TAG, "下载失败 ($label): ${e.message}")
                failures.add(label to (e.message ?: "未知错误"))
                // 删除可能的不完整文件
                destFile.delete()
            }
        }

        if (succeeded) {
            trySend(DownloadProgress(destFile.length(), destFile.length(), isComplete = true))
            close()
        } else {
            close(
                AllMirrorsFailedException(
                    "所有下载源均失败（已尝试 ${urls.size} 个）",
                    failures
                )
            )
        }
    }

    /** 构建下载源列表：GitHub → Gitee → PythonAnywhere */
    private fun buildDownloadUrls(tag: String, assetUrl: String): List<Pair<String, String>> {
        return listOf(
            "GitHub" to assetUrl,
            "Gitee" to "${Constants.GITEE_RELEASE_BASE}/$tag/app-debug.apk",
            "代理" to "${Constants.PROXY_APK_DOWNLOAD}?tag=$tag",
        )
    }

    /**
     * 从单个 URL 下载 APK 到目标文件，通过 [onProgress] 回调进度。
     */
    private suspend fun downloadSingle(
        url: String,
        destFile: File,
        onProgress: (DownloadProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}")
        }

        val body = response.body ?: throw IOException("响应体为空")
        val contentLength = body.contentLength()
        val source = body.source()
        val sink = destFile.sink().buffer()

        var bytesRead = 0L
        val buffer = Buffer()

        try {
            while (true) {
                val read = source.read(buffer, 8192)
                if (read == -1L) break
                sink.write(buffer, read)
                bytesRead += read
                onProgress(DownloadProgress(bytesRead, contentLength))
            }
        } finally {
            sink.flush()
            sink.close()
            body.close()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Release 解析
    // ═══════════════════════════════════════════════════════════

    /**
     * 将 GitHub API 响应转换为 [AppRelease]。
     *
     * 同时解析 body 中的 [FORCED] / [MIN_VER] 标记。
     */
    private fun parseResponse(response: GitHubReleaseResponse): AppRelease? {
        val tag = response.tag_name ?: return null
        val body = response.body ?: ""
        val versionName = tag.trimStart('v', 'V')

        // 取第一个 APK asset
        val asset = response.assets
            ?.firstOrNull { it.content_type == "application/vnd.android.package-archive" }
            ?: response.assets?.firstOrNull()
            ?: return null

        val apkUrl = asset.browser_download_url ?: return null
        val apkSize = asset.size ?: 0L
        val isPrerelease = response.prerelease ?: false
        val isForced = body.contains(FORCED_MARKER, ignoreCase = true)
        val minToleratedVersion = extractMinVer(body)

        return AppRelease(
            tagName = tag,
            versionName = versionName,
            name = response.name ?: tag,
            body = body,
            apkAssetUrl = apkUrl,
            apkSize = apkSize,
            isPrerelease = isPrerelease,
            isForced = isForced,
            minToleratedVersion = minToleratedVersion,
            publishedAt = response.published_at ?: "",
        )
    }

    /** 从 body 中提取 [MIN_VER:x.x.x] */
    private fun extractMinVer(body: String): String {
        val markerIndex = body.indexOf(MIN_VER_MARKER, ignoreCase = true)
        if (markerIndex < 0) return "0"
        val start = markerIndex + MIN_VER_MARKER.length
        val end = body.indexOf(']', start)
        if (end < 0) return "0"
        return body.substring(start, end).trim()
    }
}

// ═══════════════════════════════════════════════════════════
// 自定义异常
// ═══════════════════════════════════════════════════════════

/** 所有下载源均失败 */
class AllMirrorsFailedException(
    message: String,
    val failures: List<Pair<String, String>>,
) : Exception(message) {
    fun details(): String = failures.joinToString("\n") { "• ${it.first}: ${it.second}" }
}
