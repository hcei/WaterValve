package com.hgu.watervalve.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hgu.watervalve.data.local.datastore.SessionManager
import com.hgu.watervalve.data.remote.api.UwcApiService
import com.hgu.watervalve.data.remote.cookie.SessionCookieJar
import com.hgu.watervalve.data.remote.crypto.UwcCrypto
import com.hgu.watervalve.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 认证仓库：CAS ticket → UIS Token → UWC Token 完整链路。
 *
 * ## 认证三步
 * ```
 * ① casLogin(ticket)   → SESSION Cookie（OkHttp CookieJar 自动管理）
 * ② getUisToken()       → UIS JWT（约 2 年有效）
 * ③ loginByToken(uisJwt)→ UWC Token（约 1 天有效）
 * ```
 *
 * ## 错误处理
 * 每一步失败都会返回 [AuthResult.Error]，包含阶段标识和异常信息。
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: UwcApiService,
    private val sessionManager: SessionManager,
    private val cookieJar: SessionCookieJar,
) {

    private val gson = Gson()

    /**
     * 执行完整认证链。
     *
     * @param ticket CAS 回调返回的 ST-xxxxx 格式 ticket
     * @return [AuthResult.Success] 含 UWC Token 和用户数据，或 [AuthResult.Error]
     */
    suspend fun authenticate(ticket: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            // ── 第 1 步：CAS ticket → UIS SESSION ──
            val service = Constants.SPA_URL // CAS 回调地址
            val nonce = UwcCrypto.generateNonce()
            val timestamp = UwcCrypto.generateTimestamp().toString()
            val signData = "service=$service&ticket=$ticket"
            val sign = UwcCrypto.signUis(signData)

            val casResponse = api.casLogin(
                ticket = ticket,
                service = service,
                auth = Constants.UIS_AUTHORIZATION,
                sign = sign,
                nonce = nonce,
                timestamp = timestamp,
            )

            if (!casResponse.isSuccessful) {
                return@withContext AuthResult.Error(
                    stage = AuthStage.CAS_LOGIN,
                    message = "CAS 认证失败 (HTTP ${casResponse.code()})",
                )
            }

            // SESSION Cookie 由 SessionCookieJar 自动从响应头捕获
            // 等待一小段时间确保 Cookie 已写入内存缓存
            kotlinx.coroutines.delay(100)

            // ── 第 2 步：SESSION → UIS JWT ──
            val uisResponse = api.getUisToken()

            if (!uisResponse.isSuccessful) {
                return@withContext AuthResult.Error(
                    stage = AuthStage.GET_UIS_TOKEN,
                    message = "获取 UIS Token 失败 (HTTP ${uisResponse.code()})",
                )
            }

            val uisBody = uisResponse.body()
            val uisData = uisBody?.get("data")
            val uisJwt: String = when (uisData) {
                is Map<*, *> -> uisData["value"] as? String ?: ""
                else -> ""
            }

            if (uisJwt.isBlank()) {
                return@withContext AuthResult.Error(
                    stage = AuthStage.GET_UIS_TOKEN,
                    message = "UIS 响应中未找到 Token",
                )
            }

            sessionManager.saveUisToken(uisJwt)

            // ── 第 3 步：UIS JWT → UWC Token ──
            val paramStr = UwcCrypto.buildParamStr(
                mapOf("uiastoken" to uisJwt)
            )

            val uwcResponse = api.loginByToken(
                token = uisJwt,
                timestamp = UwcCrypto.generateTimestamp().toString(),
                nonce = UwcCrypto.generateNonce(),
                paramStr = paramStr,
            )

            if (!uwcResponse.isSuccessful) {
                return@withContext AuthResult.Error(
                    stage = AuthStage.LOGIN_BY_TOKEN,
                    message = "换取 UWC Token 失败 (HTTP ${uwcResponse.code()})",
                )
            }

            val uwcBody = uwcResponse.body()
            val resultMap = uwcBody?.get("resultMap") as? String
            if (resultMap.isNullOrBlank()) {
                return@withContext AuthResult.Error(
                    stage = AuthStage.LOGIN_BY_TOKEN,
                    message = "UWC 响应中未找到 resultMap",
                )
            }

            // 解密 + 解析 data 字段
            val decrypted = UwcCrypto.decryptResponse(resultMap)
            val data = UwcCrypto.parseDataField(decrypted)

            val uwcToken = data["token"] as? String ?: ""
            if (uwcToken.isBlank()) {
                return@withContext AuthResult.Error(
                    stage = AuthStage.LOGIN_BY_TOKEN,
                    message = "解密后未找到 UWC Token",
                )
            }

            sessionManager.saveUwcToken(uwcToken)

            // 提取用户信息
            val accNum = data["accNum"] as? String ?: ""
            val epId = data["epId"] as? String ?: ""
            val userId = data["userId"] as? String ?: ""
            val perCode = data["perCode"] as? String ?: ""

            AuthResult.Success(
                uisToken = uisJwt,
                uwcToken = uwcToken,
                accNum = accNum,
                epId = epId,
                userId = userId,
                perCode = perCode,
            )
        } catch (e: Exception) {
            AuthResult.Error(
                stage = AuthStage.UNKNOWN,
                message = e.message ?: "认证过程发生未知错误",
                cause = e,
            )
        }
    }

    /** 清除所有认证状态（退出登录） */
    suspend fun logout() {
        cookieJar.clearCookie()
        sessionManager.clearAll()
    }
}

/** 认证结果 */
sealed class AuthResult {
    data class Success(
        val uisToken: String,
        val uwcToken: String,
        val accNum: String,
        val epId: String,
        val userId: String,
        val perCode: String,
    ) : AuthResult()

    data class Error(
        val stage: AuthStage,
        val message: String,
        val cause: Throwable? = null,
    ) : AuthResult()
}

/** 认证失败阶段 */
enum class AuthStage {
    CAS_LOGIN,
    GET_UIS_TOKEN,
    LOGIN_BY_TOKEN,
    UNKNOWN,
}
