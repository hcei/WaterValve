package com.hgu.watervalve.data.remote.api

import com.hgu.watervalve.domain.model.GitHubReleaseResponse
import retrofit2.http.GET

/**
 * GitHub Releases API（用于应用更新检查）。
 *
 * 查询指定仓库的最新 Release 信息。
 */
interface UpdateApiService {

    /**
     * 获取最新 Release。
     *
     * @return GitHub Releases API 响应
     */
    @GET("repos/hcei/WaterValve/releases/latest")
    suspend fun getLatestRelease(): GitHubReleaseResponse
}
