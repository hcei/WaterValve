package com.hgu.watervalve.shared

import com.hgu.watervalve.shared.data.remote.api.UwcApi
import com.hgu.watervalve.shared.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.shared.util.Constants.LOGIN_BY_TOKEN_PATH
import com.hgu.watervalve.shared.util.Constants.UIS_CAS_LOGIN_PATH
import com.hgu.watervalve.shared.util.Constants.UIS_TOKEN_PATH
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal object AuthRepositoryTestFixtures {
    fun createApi(expectedJwt: String = "uis-jwt-456"): UwcApi {
        return UwcApi(
            HttpClient(
                MockEngine { request ->
                    when {
                        request.method == HttpMethod.Get && request.url.encodedPath.endsWith(UIS_CAS_LOGIN_PATH) -> {
                            respond(
                                content = "",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.SetCookie, "SESSION=session-cookie-789; Path=/; HttpOnly"),
                            )
                        }

                        request.method == HttpMethod.Post && request.url.encodedPath.endsWith(UIS_TOKEN_PATH) -> {
                            respond(
                                content = """{"data":{"value":"$expectedJwt"}}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        request.method == HttpMethod.Post && request.url.encodedPath.endsWith(LOGIN_BY_TOKEN_PATH) -> {
                            require(request.headers["token"] == expectedJwt)
                            respond(
                                content = """{"resultMap":"${encryptedLoginByTokenResultMap()}"}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json"),
                            )
                        }

                        else -> error("Unexpected request: ${request.method.value} ${request.url}")
                    }
                }
            )
        )
    }

    private fun encryptedLoginByTokenResultMap(): String {
        return UwcCrypto.encrypt(
            """{"code":"1","msg":"SUCCESS","data":"{\"token\":\"uwc-token-123\",\"userId\":\"u-1001\",\"nickName\":\"Alice\",\"accNum\":\"acc-77\",\"epId\":\"ep-9\",\"perCode\":\"pc-1\"}"}"""
        )
    }
}
