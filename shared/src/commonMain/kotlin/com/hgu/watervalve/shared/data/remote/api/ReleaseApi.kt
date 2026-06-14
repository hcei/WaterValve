package com.hgu.watervalve.shared.data.remote.api

import com.hgu.watervalve.shared.domain.model.AppRelease
import com.hgu.watervalve.shared.util.Constants
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReleaseApi(
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getGitHubLatest(): ReleasePayload = fetchRelease(Constants.GITHUB_RELEASE_API)

    suspend fun getGiteeLatest(): ReleasePayload = fetchRelease(Constants.GITEE_RELEASE_API)

    suspend fun getProxyLatest(): ReleasePayload = fetchRelease(Constants.PROXY_RELEASE_API)

    private suspend fun fetchRelease(url: String): ReleasePayload {
        val response = client.get(url)
        if (!response.status.isSuccess()) {
            throw ApiContractException("获取版本信息失败: HTTP ${response.status.value}")
        }

        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val tagName = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
        if (tagName.isBlank()) {
            throw ApiContractException("Release 响应缺少 tag_name")
        }

        val assets = root["assets"]?.jsonArray ?: JsonArray(emptyList())
        val selectedAsset = selectAsset(assets)
        val releasePageUrl = root["html_url"]?.jsonPrimitive?.content.orEmpty()
        val downloadUrl = selectedAsset?.get("browser_download_url")?.jsonPrimitive?.content
            ?: releasePageUrl

        return ReleasePayload(
            release = AppRelease(
                tagName = tagName,
                body = root["body"]?.jsonPrimitive?.content.orEmpty(),
                downloadUrl = downloadUrl,
            ),
            releasePageUrl = releasePageUrl,
        )
    }

    private fun selectAsset(assets: JsonArray): JsonObject? {
        return assets.mapNotNull { it as? JsonObject }.firstOrNull { asset ->
            asset["browser_download_url"]?.jsonPrimitive?.content?.endsWith(".ipa", ignoreCase = true) == true
        } ?: assets.mapNotNull { it as? JsonObject }.firstOrNull()
    }
}
