package com.hgu.watervalve.shared

import com.hgu.watervalve.shared.data.remote.api.ReleasePayload
import com.hgu.watervalve.shared.data.remote.api.UpdateApiService
import com.hgu.watervalve.shared.data.repository.UpdateRepository
import com.hgu.watervalve.shared.domain.model.AppRelease
import com.hgu.watervalve.shared.util.Constants
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedSmokeTest {
    @Test
    fun syncBaseUrlUsesHttps() {
        assertTrue(Constants.SYNC_BASE_URL.startsWith("https://"))
    }

    @Test
    fun updateRepositoryDetectsNewerSemanticVersion() {
        val repository = UpdateRepository(FakeUpdateApiService())

        assertTrue(repository.isNewerVersion(current = "1.0.0", latest = "v1.0.1"))
        assertFalse(repository.isNewerVersion(current = "1.2.0", latest = "v1.1.9"))
        assertFalse(repository.isNewerVersion(current = "1.2.3", latest = "1.2.3"))
    }

    @Test
    fun updateRepositoryExtractsMinSupportedVersion() {
        val repository = UpdateRepository(FakeUpdateApiService())

        assertEquals("1.1.0", repository.extractMinSupportedVersion("notes [MIN_VER:1.1.0] more"))
        assertNull(repository.extractMinSupportedVersion("notes without marker"))
    }

    @Test
    fun updateRepositoryFallsBackAcrossReleaseSources() {
        val repository = UpdateRepository(
            FakeUpdateApiService(
                githubFailure = true,
                giteeRelease = ReleasePayload(
                    release = AppRelease(
                        tagName = "v1.2.0",
                        body = "[FORCED]\n[MIN_VER:1.1.0]\nImportant",
                        downloadUrl = "https://example.com/app.ipa",
                    ),
                    releasePageUrl = "https://example.com/release/v1.2.0",
                ),
            ),
        )

        var release: AppRelease? = null
        runBlocking {
            release = repository.fetchLatestRelease()
        }

        assertEquals("v1.2.0", release?.tagName)
        assertTrue(release?.isForced == true)
        assertEquals("1.1.0", release?.minToleratedVersion)
    }
}

private class FakeUpdateApiService(
    private val githubFailure: Boolean = false,
    private val githubRelease: ReleasePayload = defaultRelease("v1.0.0"),
    private val giteeFailure: Boolean = false,
    private val giteeRelease: ReleasePayload = defaultRelease("v1.0.1"),
    private val proxyFailure: Boolean = false,
    private val proxyRelease: ReleasePayload = defaultRelease("v1.0.2"),
) : UpdateApiService() {
    override suspend fun getLatestRelease(): ReleasePayload {
        if (githubFailure) error("github down")
        return githubRelease
    }

    override suspend fun getLatestReleaseFromGitee(): ReleasePayload {
        if (giteeFailure) error("gitee down")
        return giteeRelease
    }

    override suspend fun getLatestReleaseFromProxy(): ReleasePayload {
        if (proxyFailure) error("proxy down")
        return proxyRelease
    }
}

private fun defaultRelease(tag: String): ReleasePayload {
    return ReleasePayload(
        release = AppRelease(
            tagName = tag,
            body = "Notes",
            downloadUrl = "https://example.com/$tag.ipa",
        ),
        releasePageUrl = "https://example.com/release/$tag",
    )
}
