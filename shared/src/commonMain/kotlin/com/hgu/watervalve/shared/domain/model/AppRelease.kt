package com.hgu.watervalve.shared.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AppRelease(
    val tagName: String,
    val body: String,
    val downloadUrl: String,
    val versionName: String = tagName.removePrefix("v").removePrefix("V"),
    val name: String = tagName,
    val apkAssetUrl: String = downloadUrl,
    val apkSize: Long = 0L,
    val isPrerelease: Boolean = false,
    val isForced: Boolean = false,
    val minToleratedVersion: String? = null,
    val publishedAt: String = "",
)
