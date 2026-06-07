package com.hgu.watervalve.data.remote.api

import com.hgu.watervalve.domain.model.Device
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * 设备列表云端同步 API 接口。
 *
 * 与 sync_server/main.py 后端通信。
 * Base URL 为 [com.hgu.watervalve.util.Constants.SYNC_SERVER_URL]。
 */
interface DeviceSyncApiService {

    /**
     * 获取指定用户的设备列表。
     * GET /api/devices/{userId}
     */
    @GET("api/devices/{userId}")
    suspend fun getDevices(@Path("userId") userId: String): List<Device>

    /**
     * 全量替换指定用户的设备列表。
     * POST /api/devices/{userId}
     *
     * @param userId 用户 ID
     * @param body 设备列表包装（{ "devices": [...] }）
     */
    @POST("api/devices/{userId}")
    suspend fun saveDevices(
        @Path("userId") userId: String,
        @Body body: SyncDevicesRequest,
    ): Map<String, Any>
}

/**
 * 设备同步请求体。
 */
data class SyncDevicesRequest(
    val devices: List<Device>,
)
