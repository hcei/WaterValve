package com.hgu.watervalve.shared.data.remote.api

import com.hgu.watervalve.shared.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.shared.util.Constants

class UwcApiService(
    private val uwcApi: UwcApi,
) {
    suspend fun casLogin(
        ticket: String,
        service: String = Constants.CAS_SERVICE_URL,
        auth: String = Constants.UIS_AUTHORIZATION,
        sign: String = UwcCrypto.signUis("service=$service&ticket=$ticket"),
        nonce: String = UwcCrypto.generateNonce(),
        timestamp: String = UwcCrypto.generateTimestamp().toString(),
        charset: String = "UTF-8",
    ) {
        uwcApi.exchangeCasTicket(ticket, sign, nonce, timestamp)
    }

    suspend fun getUisToken(sessionCookie: String): Map<String, Any?> {
        val token = uwcApi.getUisToken(sessionCookie)
        return mapOf("data" to mapOf("value" to token))
    }

    suspend fun loginByToken(
        token: String,
        timestamp: String = UwcCrypto.generateTimestamp().toString(),
        nonce: String = UwcCrypto.generateNonce(),
        paramStr: String,
    ): Map<String, Any?> {
        val payload = uwcApi.loginByToken(token, paramStr, timestamp, nonce)
        return payload.rawData + mapOf("token" to payload.token)
    }

    suspend fun queryCustom(
        uwcToken: String,
        timestamp: String = UwcCrypto.generateTimestamp().toString(),
        nonce: String = UwcCrypto.generateNonce(),
        paramStr: String,
    ): Map<String, Any?> {
        val epId = (UwcCrypto.decryptResponse(paramStr)["epId"] ?: "").toString()
        return uwcApi.queryCustom(uwcToken, epId, timestamp, nonce).rawData
    }

    suspend fun getSysInfo(
        uwcToken: String,
        timestamp: String = UwcCrypto.generateTimestamp().toString(),
        nonce: String = UwcCrypto.generateNonce(),
        paramStr: String = UwcCrypto.buildParamStr(emptyMap()),
    ): Map<String, Any?> {
        return uwcApi.getSysInfo(uwcToken, timestamp, nonce).rawData
    }

    suspend fun decryptResult(response: Map<String, Any?>): Map<String, Any?> {
        return response
    }
}
