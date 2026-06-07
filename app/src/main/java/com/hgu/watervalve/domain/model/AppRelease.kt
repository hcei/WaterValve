package com.hgu.watervalve.domain.model

/**
 * 应用更新 Release 信息（由 GitHub API 响应解析得到）。
 *
 * @param tagName         GitHub tag 名，e.g. "v1.0.2"
 * @param versionName     纯版本号，e.g. "1.0.2"（去 v 前缀）
 * @param name            Release 标题
 * @param body            Release 正文（changelog + 标记）
 * @param apkAssetUrl     GitHub asset 直链（主下载源）
 * @param apkSize         APK 文件大小（bytes）
 * @param isPrerelease    是否为 GitHub 预发布
 * @param isForced        是否强制更新（body 含 [FORCED] 标记）
 * @param minToleratedVersion  最低容忍版本号（body 含 [MIN_VER:x.x.x]），默认 "0"
 * @param publishedAt     ISO 发布时间
 */
data class AppRelease(
    val tagName: String,
    val versionName: String,
    val name: String,
    val body: String,
    val apkAssetUrl: String,
    val apkSize: Long,
    val isPrerelease: Boolean,
    val isForced: Boolean,
    val minToleratedVersion: String,
    val publishedAt: String,
)

// ═══════════════════════════════════════════════════════════
// GitHub REST API 响应映射（只取所需字段）
// ═══════════════════════════════════════════════════════════

/** GitHub Releases API 响应 */
data class GitHubReleaseResponse(
    val tag_name: String?,
    val name: String?,
    val body: String?,
    val prerelease: Boolean?,
    val published_at: String?,
    val assets: List<GitHubAsset>?,
)

data class GitHubAsset(
    val name: String?,
    val browser_download_url: String?,
    val size: Long?,
    val content_type: String?,
)
