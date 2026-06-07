package com.hgu.watervalve.util

object Constants {
    // --- 服务地址 ---
    const val BASE_URL = "https://ykt.hgu.edu.cn/"
    const val CAS_LOGIN_URL = "https://cas.hgu.edu.cn/cas/login" +
        "?service=https://ykt.hgu.edu.cn/uwc_web_app/"
    // CAS 回调地址（用于 service 参数）
    const val CAS_SERVICE_URL = "https://ykt.hgu.edu.cn/uwc_web_app/"
    // ★ SPA 前端地址（uwc_webapp 无下划线，Vue.js 应用入口）
    const val SPA_URL = "https://ykt.hgu.edu.cn/uwc_webapp/"

    // --- Chrome Android UA (绝对不能使用微信UA) ---
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36" +
        " (KHTML, like Gecko) Chrome/131.0.6778.135 Mobile Safari/537.36"

    // --- TripleDES 密钥 ---
    const val DES_KEY = "684523174589651002354157"
    const val DES_IV = "00000000"

    // --- UWC Sign ---
    const val MERCHANT_KEY = "hzsun.com.uwc的sign验签加密key"

    // --- UIS 认证头 ---
    const val UIS_AUTHORIZATION = "Basic d2ViQXBwOndlYkFwcA=="

    // --- UIS Sign 密钥（HMAC-SHA512）---
    // ⚠️ Phase 3: 真实密钥待真机抓包验证，当前为占位值
    const val UIS_SIGN_KEY = "hzsun.com.uwc的sign验签加密key"

    // --- SPA 路由 ---
    const val H5_OPEN_VALVE = "https://ykt.hgu.edu.cn/uwc_web_app/#/openValve"
    const val H5_DEVICE_LIST = "https://ykt.hgu.edu.cn/uwc_web_app/#/deviceList"

    // --- H5CALL 协议前缀 ---
    const val H5CALL_SCHEME = "com.hzsun.h5call://"
}
