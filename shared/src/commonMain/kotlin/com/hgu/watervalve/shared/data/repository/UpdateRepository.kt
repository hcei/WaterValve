package com.hgu.watervalve.shared.data.repository

import com.hgu.watervalve.shared.data.remote.api.ReleasePayload
import com.hgu.watervalve.shared.data.remote.api.UpdateApiService
import com.hgu.watervalve.shared.domain.model.AppRelease

class UpdateRepository(
    private val updateApiService: UpdateApiService,
) {
    suspend fun fetchLatestRelease(): AppRelease? {
        val primary = runCatching { updateApiService.getLatestRelease() }.getOrNull()
        val gitee = runCatching { updateApiService.getLatestReleaseFromGitee() }.getOrNull()
        val fallback = runCatching { updateApiService.getLatestReleaseFromProxy() }.getOrNull()
        return primary?.toAppRelease()
            ?: gitee?.toAppRelease()
            ?: fallback?.toAppRelease()
    }

    fun isNewerVersion(current: String, latest: String): Boolean {
        val currentParts = parseSemVer(current) ?: return false
        val latestParts = parseSemVer(latest) ?: return false
        val size = maxOf(currentParts.size, latestParts.size)
        for (index in 0 until size) {
            val cur = currentParts.getOrElse(index) { 0 }
            val lat = latestParts.getOrElse(index) { 0 }
            if (lat > cur) return true
            if (lat < cur) return false
        }
        return false
    }

    fun extractMinSupportedVersion(body: String): String? {
        val marker = "[MIN_VER:"
        val start = body.indexOf(marker, ignoreCase = true)
        if (start < 0) return null
        val valueStart = start + marker.length
        val end = body.indexOf(']', valueStart)
        if (end < 0) return null
        return body.substring(valueStart, end).trim().ifBlank { null }
    }

    private fun parseSemVer(version: String): List<Int>? {
        val cleaned = version.trim().removePrefix("v").removePrefix("V")
        if (cleaned.isBlank()) return null
        return cleaned.split('.').map { it.toIntOrNull() ?: return null }
    }

    private fun ReleasePayload.toAppRelease(): AppRelease {
        val bodyText = release.body
        return release.copy(
            isForced = bodyText.contains("[FORCED]", ignoreCase = true),
            minToleratedVersion = extractMinSupportedVersion(bodyText),
        )
    }
}
