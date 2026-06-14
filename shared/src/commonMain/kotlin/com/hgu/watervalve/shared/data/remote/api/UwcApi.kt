package com.hgu.watervalve.shared.data.remote.api

import com.hgu.watervalve.shared.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.shared.domain.model.UserInfo
import com.hgu.watervalve.shared.util.Constants
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class UwcApi(
    private val client: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exchangeCasTicket(
        ticket: String,
        sign: String,
        nonce: String,
        timestamp: String,
    ): CasSessionExchange {
        val response = client.get("${Constants.UIS_BASE_URL}${Constants.UIS_CAS_LOGIN_PATH}") {
            url {
                parameters.append("ticket", ticket)
                parameters.append("service", Constants.CAS_SERVICE_URL)
            }
            header(HttpHeaders.Authorization, Constants.UIS_AUTHORIZATION)
            header("Sign", sign)
            header("nonce", nonce)
            header("timestamp", timestamp)
            header("charset", "UTF-8")
            header(HttpHeaders.UserAgent, Constants.CHROME_IOS_UA)
        }
        if (!response.status.isSuccess()) {
            throw ApiContractException("CAS 认证失败: HTTP ${response.status.value}")
        }

        val cookie = response.headers.getAll(HttpHeaders.SetCookie)
            ?.flatMap { header -> header.split(";") }
            ?.map(String::trim)
            ?.firstOrNull { it.startsWith("SESSION=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }
            ?: throw ApiContractException("CAS 认证未返回 SESSION Cookie")

        return CasSessionExchange(sessionCookie = cookie)
    }

    suspend fun getUisToken(sessionCookie: String): String {
        val response = client.post("${Constants.UIS_BASE_URL}${Constants.UIS_TOKEN_PATH}") {
            header(HttpHeaders.Cookie, "SESSION=$sessionCookie")
            header(HttpHeaders.UserAgent, Constants.CHROME_IOS_UA)
        }
        if (!response.status.isSuccess()) {
            throw ApiContractException("获取 UIS Token 失败: HTTP ${response.status.value}")
        }

        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val value = root["data"]?.jsonObject?.get("value")?.jsonPrimitive?.content.orEmpty()
        if (value.isBlank()) {
            throw ApiContractException("UIS 响应中未找到 Token")
        }
        return value
    }

    suspend fun loginByToken(
        headerToken: String,
        paramStr: String,
        timestamp: String,
        nonce: String,
    ): LoginByTokenPayload {
        val response = client.post("${Constants.UIS_BASE_URL}${Constants.LOGIN_BY_TOKEN_PATH}") {
            header("token", headerToken)
            header("timestamp", timestamp)
            header("nonceStr", nonce)
            header(HttpHeaders.UserAgent, Constants.CHROME_IOS_UA)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("paramStr", paramStr)
                    }
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw ApiContractException("换取 UWC Token 失败: HTTP ${response.status.value}")
        }

        val envelope = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val resultMap = envelope["resultMap"]?.jsonPrimitive?.content.orEmpty()
        if (resultMap.isBlank()) {
            throw ApiContractException("UWC 响应中缺少 resultMap")
        }

        val decrypted = UwcCrypto.decryptResponse(resultMap)
        val data = UwcCrypto.parseDataField(decrypted)
        val token = data["token"]?.toString().orEmpty()
        if (token.isBlank()) {
            throw ApiContractException("解密后未找到 UWC Token")
        }

        return LoginByTokenPayload(
            token = token,
            userInfo = UserInfo(
                userId = stringValue(data["userId"]),
                nickname = stringValue(data["nickName"] ?: data["nickname"]),
            ),
            accNum = stringValue(data["accNum"] ?: data["accountNum"]),
            epId = stringValue(data["epId"]),
            perCode = stringValue(data["perCode"]),
            rawData = data,
        )
    }

    suspend fun queryCustom(
        uwcToken: String,
        epId: String,
        timestamp: String,
        nonce: String,
    ): QueryCustomPayload {
        return QueryCustomPayload(
            rawData = postEncrypted(
                path = Constants.QUERY_CUSTOM_PATH,
                uwcToken = uwcToken,
                timestamp = timestamp,
                nonce = nonce,
                payload = mapOf("epId" to epId),
            )
        )
    }

    suspend fun getSysInfo(
        uwcToken: String,
        timestamp: String,
        nonce: String,
    ): SysInfoPayload {
        return SysInfoPayload(
            rawData = postEncrypted(
                path = Constants.GET_SYS_INFO_PATH,
                uwcToken = uwcToken,
                timestamp = timestamp,
                nonce = nonce,
                payload = emptyMap(),
            )
        )
    }

    private suspend fun postEncrypted(
        path: String,
        uwcToken: String,
        timestamp: String,
        nonce: String,
        payload: Map<String, Any?>,
    ): Map<String, Any?> {
        val response = client.post("${Constants.UIS_BASE_URL}$path") {
            header("token", uwcToken)
            header("timestamp", timestamp)
            header("nonceStr", nonce)
            header(HttpHeaders.UserAgent, Constants.CHROME_IOS_UA)
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("paramStr", UwcCrypto.buildParamStr(payload))
                    }
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw ApiContractException("UWC 请求失败: HTTP ${response.status.value}")
        }

        val envelope = json.parseToJsonElement(response.bodyAsText()).jsonObject
        val resultMap = envelope["resultMap"]?.jsonPrimitive?.content.orEmpty()
        if (resultMap.isBlank()) {
            throw ApiContractException("UWC 响应中缺少 resultMap")
        }
        return UwcCrypto.parseDataField(UwcCrypto.decryptResponse(resultMap))
    }

    private fun stringValue(value: Any?): String {
        return when (value) {
            null -> ""
            is Number -> {
                val doubleValue = value.toDouble()
                if (doubleValue % 1.0 == 0.0) {
                    doubleValue.toLong().toString()
                } else {
                    value.toString()
                }
            }
            else -> value.toString()
        }
    }
}
