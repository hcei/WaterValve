package com.hgu.watervalve.ui.webview

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hgu.watervalve.util.Constants
import java.net.URLDecoder

/**
 * H5CALL 协议桥接：解析 `com.hzsun.h5call://` URL，提取 action 和参数。
 *
 * ## 协议格式
 * ```
 * com.hzsun.h5call://?paramjson=<URL 编码的 JSON>
 * ```
 *
 * JSON 结构：
 * ```
 * { "action": "openScan", "data": { ... }, "callback": "sendScanInfo" }
 * ```
 *
 * ## 已知 Action（H5 → 原生）
 * | Action | 说明 |
 * |--------|------|
 * | openScan | 开启 BLE 扫描 |
 * | getAllBleId | 获取所有 BLE 设备 ID |
 * | closeWin | 关闭窗口 |
 * | setNativeHeadColor | 设置原生标题栏颜色 |
 *
 * ## 已知 Callback（原生 → H5）
 * | Callback | 携带数据 |
 * |----------|---------|
 * | sendScanInfo | BLE 扫描结果 |
 * | sendWaterResult | 开水结果 |
 * | sendDeviceStatus | 设备状态 |
 * | sendValveClose | 关阀通知 |
 * | connectStateCallback | BLE 连接状态 |
 * | sendBleResult | BLE 操作结果 |
 * | sendAllBleId | 所有 BLE 设备 ID |
 * | TranscationFlowCallback | 交易流水回调 |
 *
 * ## 用法
 * ```kotlin
 * val result = UwcJsBridge.parseUrl("com.hzsun.h5call://?paramjson=...")
 * if (result != null) {
 *     println("Action: ${result.action}, Callback: ${result.callback}")
 * }
 * ```
 */
object UwcJsBridge {

    private val gson = Gson()

    /**
     * 解析 h5call:// URL，提取 action、参数和回调名。
     *
     * @param url 完整的 h5call:// URL
     * @return 解析结果，若 URL 不匹配协议或解析失败则返回 null
     */
    @Suppress("UNCHECKED_CAST")
    fun parseUrl(url: String): H5CallResult? {
        if (!url.startsWith(Constants.H5CALL_SCHEME)) return null

        return try {
            val query = url.substringAfter("?", "")
            val params = parseQueryString(query)
            val paramJson = params["paramjson"] ?: return null
            val decoded = URLDecoder.decode(paramJson, "UTF-8")
            val type = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(decoded, type)

            H5CallResult(
                action = json["action"] as? String ?: "",
                callback = json["callback"] as? String ?: "",
                data = json["data"] as? Map<String, Any?> ?: emptyMap(),
                rawJson = decoded,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 构建回调 JavaScript 代码，将结果回传给 H5。
     *
     * H5 侧通过 `window[callbackName](data)` 或全局事件接收回调。
     *
     * @param callbackName H5 期望的回调函数名
     * @param data 回调数据（将序列化为 JSON）
     * @return 可在 WebView.evaluateJavascript 中执行的 JS 代码
     */
    fun buildCallbackJs(callbackName: String, data: Map<String, Any?>): String {
        val json = gson.toJson(data)
        return "if(typeof window['$callbackName']==='function')" +
            "window['$callbackName']($json);" +
            "else console.log('[UwcJsBridge] callback $callbackName not found');"
    }

    /**
     * 构建注入 localStorage 的 Token + WeChat Mock 脚本。
     *
     * SPA 使用的 localStorage key（从 app.15d241ad.js 分析得到）：
     * - uwcToken: UWC Token（API 请求时作为 token header）
     * - uisToken / uiastoken: UIS Token（loginByToken 加密体中的 uiastoken）
     * - uwcAccNum/uwcEpid/uwcUserId/uwcPerCode: 用户信息
     * - wxMark: 微信标记（设为 "1"）
     * - isSdk: SDK 标记（设为 "true"）
     *
     * 额外注入 WeChat JS-SDK Mock，让 SPA 跳过企业微信环境检测。
     * SPA 检查 window.wx 对象，若无则 q.wxMark 为空导致认证失败。
     *
     * @param uwcToken UWC Token
     * @param uisToken UIS Token（用于 loginByToken 刷新）
     * @param userInfo 用户信息
     */
    fun buildTokenInjectionJs(
        uwcToken: String,
        uisToken: String = "",
        userInfo: Map<String, String> = emptyMap(),
    ): String {
        val sb = StringBuilder()
        sb.append("(function(){try{")

        // Mock 企业微信 wx 对象（让 SPA 的 q.wxMark = "1"）
        sb.append("if(!window.wx||!window.wx.ready){")
        sb.append("window.wx={ready:function(cb){if(cb)cb()},")
        sb.append("config:function(){},error:function(){},")
        sb.append("checkJsApi:function(opts){if(opts.success)opts.success({checkResult:{}})},")
        sb.append("invoke:function(){}")
        sb.append("};")
        sb.append("console.log('[UwcJsBridge] wx mock installed');}")

        // localStorage 注入
        sb.append("localStorage.setItem('uwcToken','$uwcToken');")
        if (uisToken.isNotBlank()) {
            sb.append("localStorage.setItem('uisToken','$uisToken');")
            sb.append("localStorage.setItem('uiastoken','$uisToken');")
        }
        sb.append("localStorage.setItem('wxMark','1');")
        sb.append("localStorage.setItem('isSdk',JSON.stringify(true));")
        userInfo.forEach { (key, value) ->
            sb.append("localStorage.setItem('$key','$value');")
        }
        sb.append("console.log('[UwcJsBridge] Token injected successfully');")
        sb.append("}catch(e){console.error('[UwcJsBridge] Token injection failed: '+e.message);}")
        sb.append("})();")
        return sb.toString()
    }

    /** 解析 URL query string 为 Map */
    private fun parseQueryString(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        query.split("&").forEach { pair ->
            val eq = pair.indexOf("=")
            if (eq > 0) {
                result[pair.substring(0, eq)] = pair.substring(eq + 1)
            }
        }
        return result
    }
}

/**
 * h5call:// URL 解析结果。
 *
 * @param action 协议 action（如 "openScan"、"getAllBleId"）
 * @param callback H5 期望的回调函数名
 * @param data action 携带的参数
 * @param rawJson 原始 JSON 字符串（调试用）
 */
data class H5CallResult(
    val action: String,
    val callback: String,
    val data: Map<String, Any?>,
    val rawJson: String,
)

/**
 * h5call 协议中已知的 Action 枚举。
 */
enum class H5Action(val value: String) {
    OPEN_SCAN("openScan"),
    GET_ALL_BLE_ID("getAllBleId"),
    CLOSE_WIN("closeWin"),
    SET_NATIVE_HEAD_COLOR("setNativeHeadColor"),
    UNKNOWN("");

    companion object {
        fun fromString(s: String): H5Action =
            entries.find { it.value == s } ?: UNKNOWN
    }
}

/**
 * h5call 协议中已知的 Callback 枚举。
 */
enum class H5Callback(val value: String) {
    SEND_SCAN_INFO("sendScanInfo"),
    SEND_WATER_RESULT("sendWaterResult"),
    SEND_DEVICE_STATUS("sendDeviceStatus"),
    SEND_VALVE_CLOSE("sendValveClose"),
    CONNECT_STATE_CALLBACK("connectStateCallback"),
    SEND_BLE_RESULT("sendBleResult"),
    SEND_ALL_BLE_ID("sendAllBleId"),
    TRANSACTION_FLOW_CALLBACK("TranscationFlowCallback"),
    UNKNOWN("");

    companion object {
        fun fromString(s: String): H5Callback =
            entries.find { it.value == s } ?: UNKNOWN
    }
}
