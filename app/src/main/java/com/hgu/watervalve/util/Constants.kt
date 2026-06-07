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

    // --- SPA 路由（uwc_webapp 无下划线，是 SPA 前端） ---
    const val H5_OPEN_VALVE = "https://ykt.hgu.edu.cn/uwc_webapp/#/openValve"
    const val H5_DEVICE_LIST = "https://ykt.hgu.edu.cn/uwc_webapp/#/deviceList"

    // --- H5CALL 协议前缀 ---
    const val H5CALL_SCHEME = "com.hzsun.h5call://"

    // --- 设备同步服务器 ---
    // PythonAnywhere: https://hcei.pythonanywhere.com/
    const val SYNC_SERVER_URL = "https://hcei.pythonanywhere.com/"

    // --- 应用更新 ---
    const val GITHUB_REPO_OWNER = "hcei"
    const val GITHUB_REPO_NAME = "WaterValve"
    const val GITHUB_API_URL = "https://api.github.com/"
    // 国内镜像（Gitee，需手动同步仓库）
    const val GITEE_RELEASE_BASE = "https://gitee.com/hcei/WaterValve/releases/download"
    // PythonAnywhere 代理（元数据 + APK 下载）
    const val PROXY_RELEASE_API = "${SYNC_SERVER_URL}api/release/latest"
    const val PROXY_APK_DOWNLOAD = "${SYNC_SERVER_URL}api/release/apk"

    // 更新检查冷却时间（1 小时）
    const val UPDATE_CHECK_COOLDOWN_MS = 3_600_000L
}
