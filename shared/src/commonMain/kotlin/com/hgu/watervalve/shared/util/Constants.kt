package com.hgu.watervalve.shared.util

object Constants {
    const val BASE_URL: String = "https://ykt.hgu.edu.cn/"
    const val CAS_LOGIN_URL: String =
        "https://cas.hgu.edu.cn/cas/login?service=https://ykt.hgu.edu.cn/uwc_web_app/"
    const val CAS_SERVICE_URL: String = "https://ykt.hgu.edu.cn/uwc_web_app/"
    const val UIS_BASE_URL: String = BASE_URL
    const val UWC_API_BASE: String = "${BASE_URL}uwc_web_app/"
    const val UWC_SPA_BASE: String = "${BASE_URL}uwc_webapp/"

    const val UIS_CAS_LOGIN_PATH: String = "uias/authentication/index/cas/login"
    const val UIS_TOKEN_PATH: String = "uias/authentication/index/token-h5"
    const val LOGIN_BY_TOKEN_PATH: String = "uwc_web_app/miniapps/loginByToken"
    const val QUERY_CUSTOM_PATH: String = "uwc_web_app/public/queryCustom"
    const val GET_SYS_INFO_PATH: String = "uwc_web_app/public/getSysInfo"

    const val H5_OPEN_VALVE_URL: String = "https://ykt.hgu.edu.cn/uwc_webapp/#/openValve"
    const val H5_DEVICE_LIST_URL: String = "https://ykt.hgu.edu.cn/uwc_webapp/#/deviceList"
    const val H5CALL_SCHEME: String = "com.hzsun.h5call://"

    const val SYNC_BASE_URL: String = "https://hcei.pythonanywhere.com/"
    const val GITHUB_RELEASE_API: String = "https://api.github.com/repos/hcei/WaterValve/releases/latest"
    const val GITEE_RELEASE_API: String = "https://gitee.com/api/v5/repos/hcei/WaterValve/releases/latest"
    const val PROXY_RELEASE_API: String = "${SYNC_BASE_URL}api/release/latest"

    const val CHROME_IOS_UA: String =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0.0.0 Mobile/15E148 Safari/604.1"
    const val UIS_AUTHORIZATION: String = "Basic d2ViQXBwOndlYkFwcA=="

    const val KEYCHAIN_KEY_UWC_TOKEN: String = "uwc_token"
    const val KEYCHAIN_KEY_UIS_JWT: String = "uis_jwt"
    const val KEYCHAIN_KEY_USER_ID: String = "user_id"
    const val KEYCHAIN_KEY_SESSION_COOKIE: String = "session_cookie"

    const val UD_KEY_NICKNAME: String = "nickname"
    const val UD_KEY_USER_ACC_NUM: String = "user_acc_num"
    const val UD_KEY_USER_EP_ID: String = "user_ep_id"
    const val UD_KEY_USER_PER_CODE: String = "user_per_code"
    const val UD_KEY_IS_BANNED: String = "is_banned"
    const val UD_KEY_LAST_REFRESH_TIME: String = "last_refresh_time"

    const val H5_LOCAL_STORAGE_UWC_TOKEN: String = "uwcToken"
    const val H5_LOCAL_STORAGE_UIS_TOKEN: String = "uisToken"
    const val H5_LOCAL_STORAGE_UIAS_TOKEN: String = "uiastoken"
    const val H5_LOCAL_STORAGE_WX_MARK: String = "wxMark"
    const val H5_LOCAL_STORAGE_IS_SDK: String = "isSdk"

    const val H5_ACTION_OPEN_SCAN: String = "openScan"
    const val H5_ACTION_GET_ALL_BLE_ID: String = "getAllBleId"
    const val H5_ACTION_CLOSE_WIN: String = "closeWin"
    const val H5_ACTION_SET_NATIVE_HEAD_COLOR: String = "setNativeHeadColor"
    const val H5_CALLBACK_SEND_SCAN_INFO: String = "sendScanInfo"
}
