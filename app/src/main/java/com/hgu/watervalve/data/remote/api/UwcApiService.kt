package com.hgu.watervalve.data.remote.api

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * UWC / UIS API 接口定义。
 *
 * ## 认证链路
 * 1. [casLogin]  — CAS ticket → SESSION Cookie（UIS 认证）
 * 2. [getUisToken] — SESSION Cookie → UIS JWT
 * 3. [loginByToken] — UIS JWT → UWC Token（首次）/ 刷新 UWC Token（后续）
 *
 * ## 加密
 * 所有 UWC 接口（loginByToken/queryCustom/getSysInfo）的 `paramStr` 字段
 * 通过 [com.hgu.watervalve.data.remote.crypto.UwcCrypto.buildParamStr] 构建。
 * 响应 `resultMap` 通过 [UwcCrypto.decryptResponse] 解密。
 *
 * ## 已知参数（来自抓包验证）
 * - loginByToken: 加密体 = `{"uiastoken":"<UIS_JWT>"}`
 * - queryCustom:   加密体 = `{"epId":"1"}`
 * - getSysInfo:    加密体 = `{}`（空参数）
 */
interface UwcApiService {

    /**
     * CAS ticket → UIS Session。
     *
     * 首次调用使用 `Authorization: Basic d2ViQXBwOndlYkFwcA==`（webApp:webApp）。
     * 响应设置 SESSION Cookie。
     *
     * ⚠️ UIS Sign 算法待 Phase 3 确定（128 hex = 512-bit，疑似 HMAC-SHA512）。
     */
    @FormUrlEncoded
    @POST("uias/authentication/index/cas/login")
    suspend fun casLogin(
        @Field("ticket") ticket: String,
        @Header("Authorization") auth: String,
        @Header("Sign") sign: String,
        @Header("nonce") nonce: String,
        @Header("timestamp") timestamp: String,
        @Header("charset") charset: String = "UTF-8",
    ): Response<Unit>

    /**
     * SESSION Cookie → UIS JWT Token。
     *
     * 需要在 OkHttp 拦截器或 CookieJar 中携带 SESSION Cookie。
     * 响应 `data.value` 即为 UIS JWT（约 2 年有效）。
     */
    @POST("uias/authentication/index/token-h5")
    suspend fun getUisToken(): Response<Map<String, Any>>

    /**
     * UIS JWT → UWC Token。
     *
     * - 首次调用：`token` header = UIS JWT，加密体含 `{"uiastoken":"<UIS_JWT>"}`
     * - 后续刷新：`token` header = UWC Token（已有的），加密体同上
     *
     * 响应解密后 `data` 字段为二次 JSON，含 `token`(UWC Token)、`accNum`、`epId` 等。
     */
    @FormUrlEncoded
    @POST("uwc_web_app/miniapps/loginByToken")
    suspend fun loginByToken(
        @Header("token") token: String,
        @Header("timestamp") timestamp: String,
        @Header("nonceStr") nonce: String,
        @Field("paramStr") paramStr: String,
    ): Response<Map<String, Any>>

    /**
     * 获取设备收藏列表（queryCustom = 代码名 `i()`）。
     *
     * 加密体参数：`{"epId":"<epId>"}`（epId 来自 loginByToken 响应）。
     * 响应 data 含 `allowUseWaterImmediately`、`allowRandomCode` 等配置。
     */
    @FormUrlEncoded
    @POST("uwc_web_app/public/queryCustom")
    suspend fun queryCustom(
        @Header("token") uwcToken: String,
        @Header("timestamp") timestamp: String,
        @Header("nonceStr") nonce: String,
        @Field("paramStr") paramStr: String,
    ): Response<Map<String, Any>>

    /**
     * 获取用水系统配置（getSysInfo = 代码名 `d()`）。
     *
     * 加密体参数：`{}`（空参数）。
     * 响应 data 含 `appId`、`appSecret`、`uiaEnabled` 等企业微信配置。
     */
    @FormUrlEncoded
    @POST("uwc_web_app/public/getSysInfo")
    suspend fun getSysInfo(
        @Header("token") uwcToken: String,
        @Header("timestamp") timestamp: String,
        @Header("nonceStr") nonce: String,
        @Field("paramStr") paramStr: String,
    ): Response<Map<String, Any>>
}
