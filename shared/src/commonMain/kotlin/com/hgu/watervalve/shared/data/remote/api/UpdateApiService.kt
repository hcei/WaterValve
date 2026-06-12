package com.hgu.watervalve.shared.data.remote.api

open class UpdateApiService(
    private val releaseApi: ReleaseApi? = null,
) {
    open suspend fun getLatestRelease(): ReleasePayload {
        return requireReleaseApi().getGitHubLatest()
    }

    open suspend fun getLatestReleaseFromGitee(): ReleasePayload {
        return requireReleaseApi().getGiteeLatest()
    }

    open suspend fun getLatestReleaseFromProxy(): ReleasePayload {
        return requireReleaseApi().getProxyLatest()
    }

    private fun requireReleaseApi(): ReleaseApi {
        return requireNotNull(releaseApi) { "ReleaseApi is required for the default UpdateApiService implementation." }
    }
}
