import Foundation

enum AppConstants {
    static let appDisplayName = "\u{5C0F}\u{6CB3}\u{6EF4}\u{7B54}"
    static let supportEmail = "1079648697@qq.com"

    static let casLoginURL = URL(string: "https://cas.hgu.edu.cn/cas/login?service=https://ykt.hgu.edu.cn/uwc_web_app/")!
    static let casServiceURL = URL(string: "https://ykt.hgu.edu.cn/uwc_web_app/")!
    static let casTicketExchangeURL = URL(string: "https://ykt.hgu.edu.cn/uias/authentication/index/cas/login")!
    static let uisBaseURL = URL(string: "https://ykt.hgu.edu.cn/")!
    static let uisTokenURL = URL(string: "https://ykt.hgu.edu.cn/uias/authentication/index/token-h5")!
    static let loginByTokenURL = URL(string: "https://ykt.hgu.edu.cn/uwc_web_app/miniapps/loginByToken")!
    static let queryCustomURL = URL(string: "https://ykt.hgu.edu.cn/uwc_web_app/public/queryCustom")!
    static let sysInfoURL = URL(string: "https://ykt.hgu.edu.cn/uwc_web_app/public/getSysInfo")!
    static let uwcApiBaseURL = URL(string: "https://ykt.hgu.edu.cn/uwc_web_app/")!
    static let uwcSpaBaseURL = URL(string: "https://ykt.hgu.edu.cn/uwc_webapp/")!
    static let h5CallSchemePrefix = "com.hzsun.h5call://"
    static let valveBridgeMessageHandlerName = "valveBridge"

    static let syncBaseURL = URL(string: "https://hcei.pythonanywhere.com")!
    static let githubLatestReleaseURL = URL(string: "https://api.github.com/repos/hcei/WaterValve/releases/latest")!
    static let giteeLatestReleaseURL = URL(string: "https://gitee.com/api/v5/repos/hcei/WaterValve/releases/latest")!
    static let proxyLatestReleaseURL = URL(string: "https://hcei.pythonanywhere.com/api/release/latest")!

    static let chromeIOSUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/131.0.0.0 Mobile/15E148 Safari/604.1"
    static let uisAuthorization = "Basic d2ViQXBwOndlYkFwcA=="
    static let uisSignKey = "hzsun.com.uwc\u{7684}sign\u{9A8C}\u{7B7E}\u{52A0}\u{5BC6}key"
    static let merchantKey = "hzsun.com.uwc\u{7684}sign\u{9A8C}\u{7B7E}\u{52A0}\u{5BC6}key"

    static let uwcTokenKey = "uwc_token"
    static let uisTokenKey = "uis_jwt"
    static let userIdKey = "user_id"
    static let nicknameKey = "nickname"
    static let sessionCookieKey = "session_cookie"
    static let userAccNumKey = "user_acc_num"
    static let userEpIdKey = "user_ep_id"
    static let userPerCodeKey = "user_per_code"
    static let isBannedKey = "is_banned"
    static let lastRefreshKey = "last_refresh_time"
    static let devicesKey = "devices"
    static let recordsKey = "records"

    static let backgroundRefreshIdentifier = "com.hgu.watervalve.tokenRefresh"
}
