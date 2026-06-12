package com.hgu.watervalve.shared.data.remote.api

import com.hgu.watervalve.shared.util.Constants
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncApi(
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getDevices(userId: String): List<RemoteDeviceDto> {
        val response = client.get("${Constants.SYNC_BASE_URL}api/devices/$userId")
        if (response.status.value == 403) throw BannedException()
        if (!response.status.isSuccess()) {
            throw ApiContractException("拉取设备失败: HTTP ${response.status.value}")
        }
        return json.decodeFromString(response.bodyAsText())
    }

    suspend fun pushDevices(userId: String, devices: List<RemoteDeviceDto>) {
        val response = client.post("${Constants.SYNC_BASE_URL}api/devices/$userId") {
            setBody(
                TextContent(
                    text = json.encodeToString(SyncDevicesRequest(devices)),
                    contentType = ContentType.Application.Json,
                )
            )
        }
        if (response.status.value == 403) throw BannedException()
        if (!response.status.isSuccess()) {
            throw ApiContractException("推送设备失败: HTTP ${response.status.value}")
        }
    }
}
