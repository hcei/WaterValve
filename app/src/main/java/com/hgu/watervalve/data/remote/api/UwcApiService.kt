package com.hgu.watervalve.data.remote.api

import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

interface UwcApiService {

    /** CAS ticket → UIS Session (设置 SESSION Cookie) */
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

    /** SESSION Cookie → UIS JWT Token */
    @POST("uias/authentication/index/token-h5")
    suspend fun getUisToken(): Response<Map<String, Any>>

    /** UIS Token → UWC Token */
    @FormUrlEncoded
    @POST("uwc_web_app/miniapps/loginByToken")
    suspend fun loginByToken(
        @Header("token") uisToken: String,
        @Header("timestamp") timestamp: String,
        @Header("nonceStr") nonce: String,
        @Field("paramStr") paramStr: String,
    ): Response<Map<String, Any>>

    /** 获取设备收藏列表 */
    @FormUrlEncoded
    @POST("uwc_web_app/public/queryCustom")
    suspend fun queryCustom(
        @Header("token") uwcToken: String,
        @Header("timestamp") timestamp: String,
        @Header("nonceStr") nonce: String,
        @Field("paramStr") paramStr: String,
    ): Response<Map<String, Any>>

    /** 获取用水系统配置 */
    @FormUrlEncoded
    @POST("uwc_web_app/public/getSysInfo")
    suspend fun getSysInfo(
        @Header("token") uwcToken: String,
        @Header("timestamp") timestamp: String,
        @Header("nonceStr") nonce: String,
        @Field("paramStr") paramStr: String,
    ): Response<Map<String, Any>>
}
