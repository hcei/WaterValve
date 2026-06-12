# 河滴答 iOS 端 — 详细设计文档

> **版本：** v1.0  
> **状态：** 待评审  
> **关联需求：** [REQUIREMENTS.md](../REQUIREMENTS.md) v1.0  
> **关联概要设计：** [high-level-design.md](high-level-design.md) v1.0  
> **设计原则：** 稳定优先、与 Android 一致、化繁为简

---

## 1. 文档概述

本文档是概要设计的下一阶段，对每个模块的**公开接口、数据结构、状态机、模块间交互、横切关注点**进行完整定义。文档达到以下层次后冻结：

- 每个模块的公开 API（类/方法签名、属性、类型）
- 数据库表结构（完整 DDL）
- 状态机与状态转换条件
- DI 注入点与 Mock 边界
- 不包含：具体实现代码、UI 布局尺寸/颜色/字体

阅读本文档后，开发者可以直接开始编码，无需再做接口决策。

---

## 2. 目录结构（详细）

```
WaterValve/
├── shared/                                  ← KMP 共享模块（新建）
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/hgu/watervalve/shared/
│       │   ├── domain/model/
│       │   │   ├── Device.kt
│       │   │   ├── WaterRecord.kt
│       │   │   ├── UserInfo.kt
│       │   │   └── AppRelease.kt
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── Device.sq              ← SQLDelight 表定义
│       │   │   │   └── WaterRecord.sq
│       │   │   ├── remote/
│       │   │   │   ├── api/
│       │   │   │   │   ├── UwcApi.kt          ← UWC 认证接口
│       │   │   │   │   ├── SyncApi.kt         ← 设备同步接口
│       │   │   │   │   └── ReleaseApi.kt      ← 更新检查接口
│       │   │   │   └── crypto/
│       │   │   │       └── UwcCrypto.kt       ← TripleDES / MD5 / HMAC
│       │   │   └── repository/
│       │   │       ├── AuthRepository.kt
│       │   │       └── DeviceRepository.kt
│       │   └── util/
│       │       └── Constants.kt               ← URL / UA / 密钥
│       ├── androidMain/                       ← Android 平台实现（沿用现有）
│       └── iosMain/                           ← iOS 平台实现（新建）
│           └── kotlin/com/hgu/watervalve/shared/
│               └── platform/
│                   ├── KeychainWrapper.kt     ← Keychain 访问
│                   └── DatabaseDriverFactory.kt ← SQLDelight iOS Driver
│
├── ios/                                       ← iOS 端（新建）
│   ├── WaterValve.xcodeproj
│   ├── WaterValve/
│   │   ├── App.swift                          ← @main 入口
│   │   ├── AppState.swift                     ← 全局状态持有
│   │   ├── DI/
│   │   │   └── AppContainer.swift             ← 依赖容器
│   │   ├── ViewModels/
│   │   │   ├── AuthViewModel.swift
│   │   │   ├── DeviceListViewModel.swift
│   │   │   ├── QRScannerViewModel.swift
│   │   │   ├── ValveViewModel.swift
│   │   │   ├── RecordViewModel.swift
│   │   │   └── UpdateViewModel.swift
│   │   ├── Views/
│   │   │   ├── Login/
│   │   │   │   ├── LoginView.swift
│   │   │   │   └── LoginProgressView.swift
│   │   │   ├── Home/
│   │   │   │   ├── HomeView.swift
│   │   │   │   ├── DeviceRowView.swift
│   │   │   │   └── AddDeviceSheet.swift
│   │   │   ├── QRScanner/
│   │   │   │   └── QRScannerView.swift
│   │   │   ├── Valve/
│   │   │   │   └── ValveView.swift
│   │   │   ├── Record/
│   │   │   │   └── RecordView.swift
│   │   │   ├── WebView/
│   │   │   │   └── WebViewContainer.swift
│   │   │   └── Common/
│   │   │       ├── BannedAlertView.swift
│   │   │       └── UpdateAlertView.swift
│   │   ├── Navigation/
│   │   │   └── AppNavigation.swift
│   │   ├── Background/
│   │   │   └── BackgroundTaskManager.swift
│   │   ├── Update/
│   │   │   └── UpdateChecker.swift
│   │   └── Utils/
│   │       └── PermissionManager.swift
│   └── BuildPhases/
│       └── build-shared.sh                   ← Gradle 构建脚本
│
├── app/                                       ← Android 端（已有，不变）
├── sync_server/                               ← Flask 后端（已有，不变）
└── .github/workflows/
    └── ios-build.yml                          ← CI（新建）
```

---

## 3. KMP Shared 层 — 模块接口

### 3.1 数据模型 (Domain Models)

所有数据模型为 Kotlin `data class`，通过 Kotlin/Native 自动导出为 Swift `class`。

```kotlin
// Device.kt
data class Device(
    val id: String,          // QR 内容 MD5（32位十六进制字符串），主键
    val name: String,        // 用户自定义名称，默认使用 id 前 8 位
    val qrUrl: String,       // 原始 QR 码内容（完整 URL）
    val starred: Boolean,    // 星标状态，true = 置顶
    val createdAt: Long      // 创建时间 Unix timestamp (ms)
)
```

```kotlin
// WaterRecord.kt
data class WaterRecord(
    val id: Long,            // 自增主键
    val deviceName: String,  // 设备名称（冗余存储，设备删除后记录仍可见）
    val timestamp: Long      // 开阀时间 Unix timestamp (ms)
)
```

```kotlin
// UserInfo.kt
data class UserInfo(
    val userId: String,      // 用户唯一标识
    val nickname: String     // 用户昵称
)
```

```kotlin
// AppRelease.kt
data class AppRelease(
    val tagName: String,     // 版本标签，如 "v1.1.1"
    val body: String,        // Release Note 正文，可能包含 [FORCED] / [MIN_VER:x.x.x]
    val downloadUrl: String  // IPA 下载 URL
)
```

### 3.2 加密模块 (Crypto)

纯 Kotlin 实现，无平台依赖。与 Android 端算法完全一致。

```kotlin
// UwcCrypto.kt
object UwcCrypto {
    /**
     * TripleDES-CBC-Pkcs7 加密
     * @param plaintext 明文
     * @return Base64 编码的密文
     */
    fun encrypt(plaintext: String): String

    /**
     * TripleDES-CBC-Pkcs7 解密
     * @param ciphertext Base64 编码的密文
     * @return 明文字符串
     */
    fun decrypt(ciphertext: String): String

    /**
     * MD5 摘要
     * @param input 输入字符串
     * @return 32 位十六进制小写字符串
     */
    fun md5(input: String): String

    /**
     * HMAC-SHA512 签名
     * @param input 待签名内容
     * @param key 签名密钥
     * @return 签名字符串
     */
    fun hmacSha512(input: String, key: String): String
}
```

**内部常量（不对外暴露）：**
- TDES 密钥：`684523174589651002354157`
- TDES IV：`00000000`
- UIS HMAC 密钥：`hzsun.com.uwc的sign验签加密key`

### 3.3 常量 (Constants)

```kotlin
// Constants.kt
object Constants {
    // CAS / UIS / UWC URL 前缀
    val CAS_LOGIN_URL: String       // CAS 登录页 URL
    val UIS_BASE_URL: String        // UIS 认证服务基址
    val UWC_API_BASE: String        // UWC 后端 API 基址（注意：uwc_web_app，有下划线）
    val UWC_SPA_BASE: String        // UWC SPA 前端基址（注意：uwc_webapp，无下划线）

    // 后端同步
    val SYNC_BASE_URL: String       // "https://hcei.pythonanywhere.com"

    // 更新检查
    val GITHUB_RELEASE_API: String  // GitHub Releases API URL
    val GITEE_RELEASE_API: String   // Gitee Releases API URL（镜像）

    // UA
    val CHROME_IOS_UA: String       // 普通 Chrome iOS User-Agent 字符串

    // Keychain keys
    const val KEYCHAIN_KEY_UWC_TOKEN: String
    const val KEYCHAIN_KEY_UIS_JWT: String
    const val KEYCHAIN_KEY_USER_ID: String

    // UserDefaults keys
    const val UD_KEY_IS_BANNED: String
    const val UD_KEY_LAST_REFRESH_TIME: String
}
```

### 3.4 远程 API (Remote API)

全部使用 Ktor Client 实现。每个 API 类接收 `HttpClient` 作为构造函数参数（可注入 mock）。

```kotlin
// UwcApi.kt — UWC 认证相关接口
class UwcApi(private val client: HttpClient) {
    /**
     * 用 UIS JWT 换取 UWC Token
     * POST /uwc_web_app/miniapps/loginByToken
     * Body: paramStr = TripleDES({ uiastoken: UIS_JWT })
     * @return LoginByTokenResponse（含 UWC Token + 用户信息）
     */
    suspend fun loginByToken(paramStr: String): LoginByTokenResponse
}

data class LoginByTokenResponse(
    val token: String,
    val userId: String,
    val nickname: String
)
```

```kotlin
// SyncApi.kt — 设备同步接口
class SyncApi(private val client: HttpClient) {
    /**
     * 拉取设备列表
     * GET /api/devices/{userId}
     * 返回 403 时抛 BannedException
     */
    suspend fun getDevices(userId: String): List<DeviceDTO>

    /**
     * 全量推送设备列表
     * POST /api/devices/{userId}
     * Body: JSON array of DeviceDTO
     * 返回 403 时抛 BannedException
     */
    suspend fun pushDevices(userId: String, devices: List<DeviceDTO>)
}

// 后端传输对象，与 Device 数据类分离
data class DeviceDTO(
    val id: String,
    val name: String,
    val qrUrl: String
    // 注意：starred、createdAt 为纯本地字段，不与云端同步
)

class BannedException : Exception()
```

```kotlin
// ReleaseApi.kt — 更新检查接口
class ReleaseApi(private val client: HttpClient) {
    /**
     * 从 GitHub Releases 获取最新版本
     * GET /repos/{owner}/{repo}/releases/latest
     * 失败时抛 NetworkException
     */
    suspend fun getGitHubLatest(): AppRelease

    /**
     * 从 Gitee 镜像获取最新版本（备选 1）
     */
    suspend fun getGiteeLatest(): AppRelease

    /**
     * 从 PythonAnywhere 代理获取最新版本（备选 2）
     * GET /api/release/latest
     */
    suspend fun getProxyLatest(): AppRelease
}
```

### 3.5 本地数据库 (Local DB)

SQLDelight 表定义。iOS 平台使用 `SqlDelightDriverFactory`（在 iosMain 中实现）创建 `SqlDriver`。

```sql
-- Device.sq

CREATE TABLE Device (
    id        TEXT    NOT NULL PRIMARY KEY,
    name      TEXT    NOT NULL,
    qrUrl     TEXT    NOT NULL,
    starred   INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL
);

selectAll:
SELECT * FROM Device ORDER BY starred DESC, createdAt DESC;

selectById:
SELECT * FROM Device WHERE id = ?;

insertOrReplace:
INSERT OR REPLACE INTO Device(id, name, qrUrl, starred, createdAt)
VALUES (?, ?, ?, ?, ?);

updateName:
UPDATE Device SET name = ? WHERE id = ?;

updateStarred:
UPDATE Device SET starred = ? WHERE id = ?;

deleteById:
DELETE FROM Device WHERE id = ?;

deleteAll:
DELETE FROM Device;

countAll:
SELECT COUNT(*) FROM Device;
```

```sql
-- WaterRecord.sq

CREATE TABLE WaterRecord (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    deviceName TEXT    NOT NULL,
    timestamp  INTEGER NOT NULL
);

selectAll:
SELECT * FROM WaterRecord ORDER BY timestamp DESC;

insert:
INSERT INTO WaterRecord(deviceName, timestamp) VALUES (?, ?);

deleteById:
DELETE FROM WaterRecord WHERE id = ?;

deleteAll:
DELETE FROM WaterRecord;
```

### 3.6 AuthRepository

认证业务逻辑的核心。管理完整登录链、Token 生命周期、用户封禁状态。

```kotlin
// AuthRepository.kt
class AuthRepository(
    private val uwcApi: UwcApi,
    private val keychain: KeychainWrapper,      // iosMain 平台实现
    private val userDefaults: UserDefaultsWrapper // iosMain 平台实现
) {
    // ── 状态暴露 ──

    /** 登录流程状态流 */
    val loginState: StateFlow<LoginState>

    /** 用户封禁状态 */
    val isBanned: StateFlow<Boolean>

    // ── CAS 登录 ──

    /**
     * 返回 CAS 登录页 URL 和所需 UA，调用方用 WKWebView 加载
     * @return CasLoginConfig(url, userAgent)
     */
    fun startCasLogin(): CasLoginConfig

    /**
     * WKWebView 拦截到 CAS ticket 后调用，执行完整 Token 交换链
     * 内部流程：
     *   1. GET /uias/authentication/index/cas/login?ticket=ST-xxx → Set-Cookie: SESSION
     *   2. POST /uias/authentication/index/token-h5 → 获取 UIS JWT
     *   3. POST /uwc_web_app/miniapps/loginByToken → 获取 UWC Token
     *   4. 令牌存入 Keychain
     *   5. loginState 更新为 Success
     *
     * @param ticket CAS 回调中的 ST-xxxxx
     * @return LoginResult
     */
    suspend fun exchangeCasTicket(ticket: String): LoginResult

    // ── Token 管理 ──

    /**
     * 用存储的 UIS JWT 刷新 UWC Token（后台任务调用）
     * 内部调用 uwcApi.loginByToken()，写入新 Token 到 Keychain
     * @return true 表示刷新成功
     */
    suspend fun refreshUwcToken(): Boolean

    /** 当前是否有有效的 UWC Token（非空即视为有效） */
    fun hasValidToken(): Boolean

    /** 获取当前 UWC Token，供 Valve Module 注入 SPA */
    fun getUwcToken(): String?

    /** 获取当前 userId */
    fun getUserId(): String?

    // ── 封禁处理 ──

    /** 标记用户被封禁，持久化到 UserDefaults */
    fun markBanned()

    // ── 登出 ──

    /** 清除所有 Token 和用户数据 */
    fun clearAuth()
}

// ── 辅助类型 ──

data class CasLoginConfig(
    val url: String,
    val userAgent: String
)

sealed class LoginState {
    object Idle : LoginState()
    data class Loading(val step: Int, val message: String) : LoginState()
    // step 1: CAS WebView 加载中
    // step 2: 正在交换 UIS JWT
    // step 3: 正在交换 UWC Token
    object Success : LoginState()
    data class Failed(val error: LoginError) : LoginState()
}

enum class LoginError {
    Network,
    InvalidCredentials,
    Banned,
    Unknown
}

sealed class LoginResult {
    data class Success(val userInfo: UserInfo) : LoginResult()
    data class Failed(val error: LoginError) : LoginResult()
}
```

### 3.7 DeviceRepository

设备 CRUD 与云端同步。本地优先（Offline-first），云端异步同步。

```kotlin
// DeviceRepository.kt
class DeviceRepository(
    private val syncApi: SyncApi,
    private val deviceDb: DeviceDatabase,       // SQLDelight 生成的数据库接口
    private val recordDb: WaterRecordDatabase,
    private val authRepository: AuthRepository  // 获取 userId
) {
    // ── 设备 ──

    /** 设备列表流（自动反映本地数据库变化） */
    val devices: StateFlow<List<Device>>

    /**
     * 添加设备（扫码后调用）
     * 1. MD5(qrUrl) → deviceId
     * 2. 写入 SQLDelight
     * 3. 全量推送到云端
     */
    suspend fun addDevice(qrUrl: String): Result<Device>

    suspend fun renameDevice(deviceId: String, name: String): Result<Unit>
    suspend fun starDevice(deviceId: String, starred: Boolean): Result<Unit>

    /**
     * 删除设备
     * 1. 删除 SQLDelight 记录
     * 2. 全量推送到云端
     */
    suspend fun deleteDevice(deviceId: String): Result<Unit>

    // ── 云端同步 ──

    /**
     * 从云端拉取设备列表，覆盖本地
     * 登录成功后调用。云端无 starred/createdAt，本地保留或设默认值
     */
    suspend fun pullFromCloud(): Result<Unit>

    /**
     * 全量推送本地设备列表到云端
     * 每次本地变更后自动调用
     */
    suspend fun pushToCloud(): Result<Unit>

    // ── 开阀记录 ──

    /** 开阀记录流（时间倒序） */
    val records: StateFlow<List<WaterRecord>>

    suspend fun addRecord(deviceName: String): Result<Unit>
    suspend fun deleteRecord(id: Long): Result<Unit>
    suspend fun deleteAllRecords(): Result<Unit>
}
```

### 3.8 iOS 平台适配器 (iosMain)

KMP 的 `iosMain` 提供两个平台实现，通过 expect/actual 机制或接口注入方式暴露给 commonMain。

```kotlin
// KeychainWrapper.kt (iosMain)
// 封装 iOS Keychain Services API (SecItemAdd/SecItemCopyMatching/SecItemDelete)
class KeychainWrapper {
    fun set(key: String, value: String): Boolean
    fun get(key: String): String?
    fun delete(key: String): Boolean
    fun clear(): Boolean
}

// UserDefaultsWrapper.kt (iosMain)
// 封装 NSUserDefaults 读写
class UserDefaultsWrapper {
    fun setBool(key: String, value: Boolean)
    fun getBool(key: String): Boolean
    fun setLong(key: String, value: Long)
    fun getLong(key: String): Long
}
```

---

## 4. iOS Native 层 — 模块接口

### 4.1 App Entry（应用入口）

```swift
// App.swift
@main
struct WaterValveApp: App {
    @StateObject private var appState = AppState()
    private let container = AppContainer()  // DI 容器

    var body: some Scene {
        WindowGroup {
            AppNavigation(appState: appState, container: container)
                .onAppear {
                    // 注册后台任务
                    container.backgroundTaskManager.registerTasks()
                    // 检查更新
                    container.updateChecker.checkForUpdate()
                }
        }
    }
}

// AppState.swift
class AppState: ObservableObject {
    @Published var isLoggedIn: Bool = false
    @Published var isBanned: Bool = false
    @Published var showUpdateAlert: Bool = false
    @Published var updateInfo: UpdateInfo? = nil
}
```

**状态路由逻辑：**

```
App 启动
  ├─ isBanned == true        → 显示封禁弹窗（不可关闭，全屏覆盖）
  ├─ isLoggedIn == false     → 显示 LoginView
  └─ isLoggedIn == true      → 显示 HomeView（NavigationStack 根页面）
```

### 4.2 WebView 基础设施 (WebViewContainer)

可复用的 WKWebView 封装，所有需要加载网页的模块共用。

```swift
// WebViewContainer.swift
struct WebViewConfig {
    let url: URL
    let userAgent: String
    let jsBridgeName: String?       // JS → Native 消息名称，nil 表示不需要桥接
    let jsBridgeHandler: ((String) -> Void)?  // 收到 JS 消息的回调
    let onNavigationComplete: (() -> Void)?   // 页面加载完成回调
}

struct WebViewContainer: UIViewRepresentable {
    let config: WebViewConfig

    // 供外部调用的方法
    func injectJavaScript(_ script: String)   // 向 WebView 注入 JS
    func reload()
}

// 内部使用:
// - 单一共享 WKProcessPool（static let shared = WKProcessPool()）
// - WKWebsiteDataStore.default() 持久化 Cookie
// - configuration.userContentController 注册 JS Bridge
```

**设计要点：**
- 所有 WebViewContainer 实例共享同一个 `WKProcessPool`，确保 CAS Session Cookie 跨页面复用
- JS Bridge 通过 `WKScriptMessageHandler` 实现：JS 侧 `webkit.messageHandlers.<name>.postMessage(...)` → Swift 侧回调
- User-Agent 通过 `WKWebViewConfiguration.applicationNameForUserAgent` 追加 Chrome iOS UA 标识

### 4.3 Navigation（路由）

```swift
// AppNavigation.swift
struct AppNavigation: View {
    @ObservedObject var appState: AppState
    let container: AppContainer

    var body: some View {
        NavigationStack {
            Group {
                if appState.isBanned {
                    BannedAlertView(appState: appState, container: container)
                } else if !appState.isLoggedIn {
                    LoginView(viewModel: container.makeAuthViewModel(appState: appState))
                } else {
                    HomeView(viewModel: container.makeDeviceListViewModel(appState: appState))
                }
            }
        }
        .overlay {
            if appState.showUpdateAlert {
                UpdateAlertView(appState: appState)
            }
        }
    }
}
```

**路由表：**

| 当前页 | 导航目标 | 触发条件 | 导航方式 |
|--------|----------|----------|----------|
| LoginView | HomeView | 登录成功 | `appState.isLoggedIn = true` 自动切换 |
| HomeView | QRScannerView | 点击"添加设备"→"扫码" | `NavigationLink` push |
| HomeView | AddDeviceSheet | 点击"添加设备"→"手动输入" | `.sheet` modal |
| HomeView | ValveView(device) | 点击设备行 | `NavigationLink` push |
| HomeView | RecordView | 点击"开阀记录" | `NavigationLink` push |
| 任意页 | BannedAlertView | `appState.isBanned = true` | overlay 覆盖 |
| 任意页 | UpdateAlertView | `appState.showUpdateAlert = true` | overlay 覆盖 |

### 4.4 Login Module（登录）

```swift
// LoginView.swift
struct LoginView: View {
    @StateObject var viewModel: AuthViewModel

    // 状态枚举（驱动 UI）
    enum UIState {
        case idle                    // 初始：显示"登录"按钮
        case loading(step: Int, message: String)  // 进度提示
        case webView(url: URL, ua: String)        // 展示 WKWebView
        case error(message: String) // 错误提示 + 重试按钮
    }
}

// ── AuthViewModel.swift ──
class AuthViewModel: ObservableObject {
    @Published var uiState: LoginView.UIState = .idle

    private let authRepository: AuthRepository
    private weak var appState: AppState?

    // 用户点击"登录"
    func startLogin() {
        let config = authRepository.startCasLogin()
        uiState = .webView(url: URL(string: config.url)!, ua: config.userAgent)
    }

    // WebView 拦截到 CAS ticket 后调用
    func handleCasTicket(_ ticket: String) {
        uiState = .loading(step: 2, message: "正在验证身份...")
        Task {
            let result = await authRepository.exchangeCasTicket(ticket: ticket)
            await MainActor.run {
                switch result {
                case .success(_):
                    uiState = .idle
                    appState?.isLoggedIn = true
                case .failed(let error):
                    uiState = .error(message: error.localizedDescription)
                }
            }
        }
    }

    // 依赖注入
    init(authRepository: AuthRepository, appState: AppState)
}

// ── LoginProgressView.swift ──
// 显示 3 阶段进度文案：
//   Step 1: "正在加载登录页面"
//   Step 2: "正在验证身份"
//   Step 3: "正在获取访问令牌"
```

**CAS WebView 拦截逻辑（在 LoginView 内部 WebViewContainer 的导航回调中实现）：**

```
WebView 页面加载 URL 变化
  → 匹配 "ticket=ST-" 模式
  → 停止 WebView 加载
  → 提取 ticket 值
  → 调用 viewModel.handleCasTicket(ticket)
```

### 4.5 Home Module（设备列表）

```swift
// HomeView.swift
struct HomeView: View {
    @StateObject var viewModel: DeviceListViewModel

    enum UIState {
        case loading
        case loaded(devices: [DeviceItem])
        case empty
        case error(message: String)
    }
}

// ── DeviceListViewModel.swift ──
class DeviceListViewModel: ObservableObject {
    @Published var uiState: HomeView.UIState = .loading
    @Published var showDeleteConfirmation: Device? = nil  // 删除确认弹窗

    private let deviceRepository: DeviceRepository
    private weak var appState: AppState?

    // 生命周期
    func onAppear() {
        // 订阅 deviceRepository.devices 流 → 更新 uiState
    }

    // 操作
    func addDevice(qrUrl: String)      // 扫码或手动输入后调用
    func renameDevice(id: String, name: String)
    func toggleStar(id: String)
    func confirmDeleteDevice(id: String)  // → 设置 showDeleteConfirmation
    func deleteDevice(id: String)         // 确认后调用
    func refreshFromCloud()              // 下拉刷新

    init(deviceRepository: DeviceRepository, appState: AppState)
}

// ── DeviceRowView.swift ──
// 单行设备视图：图标 + 名称 + 星标按钮 + 滑动删除
// 点击 → NavigationLink 到 ValveView

// ── AddDeviceSheet.swift ──
// 半屏弹窗："扫码添加"按钮 + "手动输入"输入框 + 确认按钮
// 手动输入：粘贴 QR URL → 调用 viewModel.addDevice(url)
```

**云端同步触发时机（在 DeviceRepository 内部自动执行）：**

| 操作 | 触发同步 |
|------|----------|
| addDevice | pushToCloud() |
| renameDevice | pushToCloud() |
| deleteDevice | pushToCloud() |
| onAppear（登录后首次） | pullFromCloud() |

### 4.6 QR Scanner（扫码）

```swift
// QRScannerView.swift
struct QRScannerView: View {
    @StateObject var viewModel: QRScannerViewModel
    let onDetected: (String) -> Void  // 识别成功回调（传递 QR URL）

    enum UIState {
        case scanning
        case detected(url: String)
        case permissionDenied
    }
}

// ── QRScannerViewModel.swift ──
class QRScannerViewModel: ObservableObject {
    @Published var uiState: QRScannerView.UIState = .scanning
    @Published var isTorchOn: Bool = false

    func toggleTorch()
    func startScanning()
    func stopScanning()
    // 内部使用 AVFoundation + Vision：
    //   AVCaptureSession → 摄像头画面
    //   VNDetectBarcodesRequest → QR 码识别（symbologies: [.qr]）
    //   识别到第一个 → uiState = .detected → 回调 onDetected
}
```

### 4.7 Valve Module（一键开阀）

```swift
// ValveView.swift
struct ValveView: View {
    @StateObject var viewModel: ValveViewModel

    enum UIState {
        case loading            // 正在获取 Token / 加载 SPA
        case loaded             // SPA 正常显示
        case error(message: String)
    }
}

// ── ValveViewModel.swift ──
class ValveViewModel: ObservableObject {
    @Published var uiState: ValveView.UIState = .loading

    private let authRepository: AuthRepository
    private let deviceRepository: DeviceRepository
    private let device: Device

    // SPA URL 构造
    var spaUrl: URL? {
        // UWC_SPA_BASE + 设备相关路径
    }

    // Token 注入 JS 脚本
    var tokenInjectionScript: String? {
        guard let token = authRepository.getUwcToken() else { return nil }
        // 在 SPA 页面加载完成后注入：
        // window.localStorage.setItem('uwc_token', '<token>');
        // 或 SPA 约定的注入方式
    }

    // SPA → Native 消息处理（JS Bridge 回调）
    func handleBridgeMessage(_ message: String) {
        // 解析 JSON: { "event": "valveOpened", "deviceName": "..." }
        // 调用 deviceRepository.addRecord(deviceName:)
    }

    init(authRepository: AuthRepository,
         deviceRepository: DeviceRepository,
         device: Device)
}
```

**JS Bridge 协议详见第 7.2 节。**

### 4.8 Record Module（开阀记录）

```swift
// RecordView.swift
struct RecordView: View {
    @StateObject var viewModel: RecordViewModel

    enum UIState {
        case loading
        case loaded(records: [RecordItem])
        case empty
    }
}

// ── RecordViewModel.swift ──
class RecordViewModel: ObservableObject {
    @Published var uiState: RecordView.UIState = .loading

    private let deviceRepository: DeviceRepository

    func onAppear() {
        // 订阅 deviceRepository.records 流
    }

    func deleteRecord(id: Long) {
        Task { await deviceRepository.deleteRecord(id: id) }
    }

    func deleteAllRecords() {
        // 弹出确认 → 确认后调用 deviceRepository.deleteAllRecords()
        Task { await deviceRepository.deleteAllRecords() }
    }

    init(deviceRepository: DeviceRepository)
}
```

### 4.9 Background Module（后台 Token 刷新）

```swift
// BackgroundTaskManager.swift
class BackgroundTaskManager {
    private let authRepository: AuthRepository

    /// 注册后台任务标识符。在 App 启动时调用。
    /// BGTaskScheduler 注册是幂等的（多次注册同一 ID 不会报错），因此每次启动都调用。
    func registerTasks() {
        // BGTaskScheduler.shared.register(
        //     forTaskWithIdentifier: "com.hgu.watervalve.tokenRefresh",
        //     using: nil
        // ) { task in
        //     self.handleTokenRefresh(task: task as! BGAppRefreshTask)
        // }
    }

    /// 调度下一次刷新（在注册任务后和每次刷新完成后调用）
    func scheduleNextRefresh() {
        // let request = BGAppRefreshTaskRequest(identifier: "com.hgu.watervalve.tokenRefresh")
        // request.earliestBeginDate = Date(timeIntervalSinceNow: 12 * 3600)
        // try BGTaskScheduler.shared.submit(request)
    }

    /// 执行 Token 刷新
    private func handleTokenRefresh(task: BGAppRefreshTask) {
        // task.expirationHandler = { /* 标记超时 */ }
        // 调用 authRepository.refreshUwcToken()
        // 完成后 task.setTaskCompleted(success: ...)
        // 再次 scheduleNextRefresh()
    }

    init(authRepository: AuthRepository)
}
```

**注册策略选择：每次启动都注册。** BGTaskScheduler 的 `register` 是幂等的，重复注册不会导致问题。这比"首次启动注册 + UserDefaults 标记"更简单可靠——如果用户清除 UserDefaults 不会导致任务丢失。

### 4.10 Update Module（更新检查）

```swift
// UpdateChecker.swift
class UpdateChecker {
    private let releaseApi: ReleaseApi  // KMP shared
    private let currentVersion: String  // Bundle.main 读取

    /// 检查更新，返回结果类型
    func checkForUpdate() async -> UpdateCheckResult

    /// 版本号比较
    /// 本地 < 远程 → 有更新
    /// 本地 >= 远程 → 无更新
    func compareVersions(local: String, remote: String) -> ComparisonResult

    // 多源保底：详见第 7.3 节

    init(releaseApi: ReleaseApi)
}

enum UpdateCheckResult {
    case noUpdate
    case updateAvailable(AppRelease)   // 普通更新
    case forcedUpdate(AppRelease)      // 强制更新（body 含 [FORCED]）
}

// ── UpdateViewModel.swift ──
class UpdateViewModel: ObservableObject {
    @Published var checkResult: UpdateCheckResult = .noUpdate

    private let updateChecker: UpdateChecker

    func checkForUpdate() {
        Task {
            let result = await updateChecker.checkForUpdate()
            await MainActor.run { checkResult = result }
        }
    }

    /// 用户点击"更新"→ 打开 AltStore URL Scheme 或 GitHub Release 页面
    func openUpdatePage(release: AppRelease) {
        // 尝试 AltStore URL Scheme，失败则 Safari 打开 GitHub Release 页面
    }

    init(updateChecker: UpdateChecker)
}

// ── UpdateAlertView.swift ──
// 普通更新：可关闭弹窗，显示 Release Note + "更新"/"稍后" 按钮
// 强制更新：不可关闭弹窗，显示 Release Note + "更新"/"退出应用" 按钮
```

---

## 5. 依赖注入与 Mock 边界

### 5.1 DI 容器

使用手动构造函数注入。`AppContainer` 在 App 启动时创建，持有所有长生命周期依赖。

```swift
// AppContainer.swift
class AppContainer {
    // ── KMP 层依赖 ──
    let keychainWrapper: KeychainWrapper
    let userDefaultsWrapper: UserDefaultsWrapper
    let httpClient: HttpClient                // Ktor HttpClient
    let sqlDriver: SqlDriver                  // SQLDelight iOS Driver

    // ── Repositories ──
    let uwcApi: UwcApi
    let syncApi: SyncApi
    let releaseApi: ReleaseApi
    let authRepository: AuthRepository
    let deviceRepository: DeviceRepository

    // ── iOS 层依赖 ──
    let backgroundTaskManager: BackgroundTaskManager
    let updateChecker: UpdateChecker

    init() {
        // 1. 创建平台适配器
        keychainWrapper = KeychainWrapper()
        userDefaultsWrapper = UserDefaultsWrapper()
        sqlDriver = SqlDelightDriverFactory().createDriver()

        // 2. 创建 HTTP 客户端（共用 Ktor HttpClient 实例）
        httpClient = HttpClient { /* 配置超时、UA 等 */ }

        // 3. 创建 API 层
        uwcApi = UwcApi(client: httpClient)
        syncApi = SyncApi(client: httpClient)
        releaseApi = ReleaseApi(client: httpClient)

        // 4. 创建 Repository（注入具体实现）
        authRepository = AuthRepository(
            uwcApi: uwcApi,
            keychain: keychainWrapper,
            userDefaults: userDefaultsWrapper
        )
        deviceRepository = DeviceRepository(
            syncApi: syncApi,
            deviceDb: /* SQLDelight 生成的 DB */,
            recordDb: /* SQLDelight 生成的 DB */,
            authRepository: authRepository
        )

        // 5. 创建 iOS 层服务
        backgroundTaskManager = BackgroundTaskManager(authRepository: authRepository)
        updateChecker = UpdateChecker(releaseApi: releaseApi)
    }
}

// ── ViewModel 工厂方法 ──
extension AppContainer {
    func makeAuthViewModel(appState: AppState) -> AuthViewModel {
        AuthViewModel(authRepository: authRepository, appState: appState)
    }

    func makeDeviceListViewModel(appState: AppState) -> DeviceListViewModel {
        DeviceListViewModel(deviceRepository: deviceRepository, appState: appState)
    }

    func makeQRScannerViewModel() -> QRScannerViewModel {
        QRScannerViewModel()
    }

    func makeValveViewModel(device: Device) -> ValveViewModel {
        ValveViewModel(
            authRepository: authRepository,
            deviceRepository: deviceRepository,
            device: device
        )
    }

    func makeRecordViewModel() -> RecordViewModel {
        RecordViewModel(deviceRepository: deviceRepository)
    }

    func makeUpdateViewModel() -> UpdateViewModel {
        UpdateViewModel(updateChecker: updateChecker)
    }
}
```

### 5.2 Mock 边界

每个模块的依赖通过构造函数注入。测试时可替换为 mock。

| 被测试模块 | 需要 mock 的依赖 | mock 实现方式 |
|------------|------------------|--------------|
| AuthViewModel | AuthRepository | 传入 fake AuthRepository（in-memory Keychain + mock UwcApi） |
| DeviceListViewModel | DeviceRepository | 传入 fake DeviceRepository（in-memory SQLDelight driver + mock SyncApi） |
| ValveViewModel | AuthRepository, DeviceRepository | 同上 |
| RecordViewModel | DeviceRepository | 同上 |
| QRScannerViewModel | 无外部依赖 | 直接测试 |
| UpdateViewModel | UpdateChecker | 传入 mock ReleaseApi 返回预设 AppRelease |
| AuthRepository | UwcApi, KeychainWrapper | mock UwcApi（返回预设响应），fake KeychainWrapper（in-memory map） |
| DeviceRepository | SyncApi, SQLDelight | mock SyncApi，in-memory SQLDelight driver |
| BackgroundTaskManager | AuthRepository | 同 AuthRepository mock |

**Mock 实现方式：**

- **Kotlin 层**（UwcApi / SyncApi / ReleaseApi）：定义 Kotlin `interface` 或使用 `class` + constructor 注入。测试时传入 mock 实现。
- **SQLDelight**：使用 `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)` 创建内存数据库。
- **Keychain / UserDefaults**：在 iosMain 提供 fake 实现（in-memory Dictionary），通过 expect/actual 或接口注入选定。

---

## 6. 状态管理

### 6.1 状态层级

```
AppState (全局)
  ├── isLoggedIn: Bool          ← AuthRepository.loginState → Success 时置 true
  ├── isBanned: Bool            ← AuthRepository.isBanned 流同步
  └── showUpdateAlert: Bool     ← UpdateViewModel 触发

AuthViewModel (登录页)
  └── uiState: UIState          ← 管理登录流程步骤

DeviceListViewModel (首页)
  └── uiState: UIState          ← DeviceRepository.devices 流驱动

QRScannerViewModel (扫码)
  └── uiState: UIState          ← AVFoundation 回调驱动

ValveViewModel (开阀)
  └── uiState: UIState          ← WebView 加载状态驱动

RecordViewModel (记录)
  └── uiState: UIState          ← DeviceRepository.records 流驱动

UpdateViewModel (更新)
  └── checkResult: UpdateCheckResult  ← UpdateChecker 结果
```

### 6.2 状态同步机制

| 跨层方向 | 机制 |
|----------|------|
| Kotlin StateFlow → SwiftUI @Published | ViewModel 中通过 `Collector` 订阅 StateFlow，在回调中更新 @Published |
| SwiftUI @Published → Kotlin Repository | 直接调用 Repository 的 suspend 函数 |
| Modal 事件（JS Bridge / 封禁弹窗） | 回调闭包 → 更新 AppState 或 ViewModel |
| WebView 加载状态 | WKWebView `navigationDelegate` 回调 → @Published |

### 6.3 错误处理层级

```
第 1 层 — 网络异常（无连接 / 超时）
  → Repository.Result.failure(NetworkError)
  → ViewModel 捕获 → uiState = .error("网络不可用，请检查连接")
  → UI 显示错误提示 + 重试按钮

第 2 层 — 业务异常（403 封禁 / Token 过期）
  → Repository 捕获 → 更新内部状态流
  → 403: markBanned() → AppState.isBanned = true → 全局封禁弹窗
  → Token 过期: 自动尝试刷新，失败则清除 Token → AppState.isLoggedIn = false

第 3 层 — 未知异常
  → uiState = .error("发生未知错误")
  → 记录日志，不影响 App 稳定性
```

---

## 7. 横切关注点

### 7.1 Keychain 封装方案

```
存储内容：
  ├── "uwc_token"   → UWC Token 字符串
  ├── "uis_jwt"     → UIS JWT 字符串（有效期约 2 年）
  └── "user_id"     → 当前用户 ID

使用的 Keychain API：
  - SecItemAdd()    写入
  - SecItemCopyMatching()  读取
  - SecItemDelete() 删除
  - kSecClass = kSecClassGenericPassword
  - kSecAttrService = "com.hgu.watervalve"
  - kSecAttrAccount = key 名称

KeychainWrapper 接口: 见 3.8 节
```

**为什么用 Keychain 而不是 UserDefaults：**
- Keychain 数据加密存储，系统级安全
- App 卸载后 Keychain 数据是否保留由 `kSecAttrAccessible` 控制——本 App 选择 `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`（不备份到 iCloud，卸载时清除）

### 7.2 JS Bridge 协议

**通道建立方式：**
- Native 侧：`WKUserContentController.add(self, name: "valveBridge")`
- SPA 侧：`window.webkit.messageHandlers.valveBridge.postMessage(jsonString)`

**消息格式（JSON 字符串）：**

```json
// SPA → Native：开阀成功通知
{
    "event": "valveOpened",
    "deviceName": "饮水机-01",
    "timestamp": 1701234567890
}

// SPA → Native：错误通知（可选）
{
    "event": "error",
    "message": "开阀失败：设备离线"
}
```

**Native → SPA：Token 注入（在 WebView 页面加载完成后执行）**

```javascript
// 方式 1：直接注入 JavaScript
window.__valveBridge = {
    token: "<UWC_TOKEN>",
    userId: "<USER_ID>"
};

// 方式 2：使用 localStorage（若 SPA 约定从此读取）
localStorage.setItem("uwc_token", "<UWC_TOKEN>");
```

**SPA 侧约定（由 Android 端已有实现确定，iOS 保持一致）：**
- Token 注入方式：与 Android 端 JS Bridge 代码一致（读取 Android 端 WebView 中 `evaluateJavascript` 注入的内容，复用相同 key）
- 消息格式：与 Android 一致

> **实现时需参考 Android 端 `app/src/main/java/com/hgu/watervalve/` 中 Valve WebView 的 JS Bridge 实现，确保 iOS 端与 Android 端使用相同的消息 key 和格式。**

### 7.3 WKWebView Cookie 持久化

**问题背景：** iOS WKWebView 在独立进程中运行，其 Cookie 存储（`WKWebsiteDataStore`）与 `HTTPCookieStorage.shared` 不自动同步。

**本 App 的影响分析：**

| 阶段 | Cookie 使用者 | 影响 |
|------|-------------|------|
| CAS 登录 | WKWebView 加载 CAS 页面 | CAS Session Cookie 存在 WKWebView 的 DataStore 中 |
| Token 交换 | Ktor（原生 HTTP） | SESSION Cookie 存在 HTTPCookieStorage 中 |
| UWC SPA 加载 | WKWebView 加载 SPA | 不需要 Ktor 阶段的 Cookie |

**结论：** 三阶段之间没有 Cookie 传递需求——CAS Session 只被 WKWebView 自己使用，Ktor 的 SESSION Cookie 只被 Ktor 使用，UWC SPA 通过注入 Token 认证不需要 Cookie。因此**无需做额外 Cookie 同步处理**。

**唯一需要注意的配置：**
- 所有 WKWebView 实例共享同一个 `WKProcessPool`（在 `WebViewContainer` 中定义为 `static let`）
- 这确保同一个 CAS 登录页在多次加载间可复用 Session Cookie（WKProcessPool 不重置则 Cookie 不丢失）

### 7.4 BGTaskScheduler 注册时机与策略

**注册时机：每次 App 启动时在 `applicationDidFinishLaunching`（SwiftUI 中为 `App.onAppear`）调用 `registerTasks()`。**

理由：
- `BGTaskScheduler.shared.register()` 是幂等的——重复注册已有标识符不会报错
- 不需要 UserDefaults 标记"是否已注册"——减少状态复杂度
- 即使系统清理了任务调度，下次启动自动重新注册

**调度策略：**
```
App 启动 → registerTasks() → scheduleNextRefresh()
                                        ↓
                              BGTaskScheduler 系统调度（约 12h 后触发）
                                        ↓
                              handleTokenRefresh()
                                → refreshUwcToken()
                                → scheduleNextRefresh()  ← 刷新后再次调度
```

**失败处理：**
- 如果 Token 刷新失败（网络不可达），静默失败，不通知用户
- 下次后台任务触发时（约 12h 后）自动重试
- 如果前台操作时发现 Token 失效，AuthRepository 会触发重新登录

### 7.5 更新检查多源保底

**降级顺序：**

```
checkForUpdate()
  │
  ├─ 1. GitHub Releases API (主)
  │     URL: https://api.github.com/repos/{owner}/{repo}/releases/latest
  │     超时: 10 秒
  │     → 成功 → 使用结果
  │     → 失败 ↓
  │
  ├─ 2. Gitee Releases API (备 1)
  │     URL: https://gitee.com/api/v5/repos/{owner}/{repo}/releases/latest
  │     超时: 10 秒
  │     → 成功 → 使用结果
  │     → 失败 ↓
  │
  └─ 3. PythonAnywhere 代理 (备 2)
        URL: https://hcei.pythonanywhere.com/api/release/latest
        超时: 10 秒
        → 成功 → 使用结果
        → 失败 → 静默跳过（不弹窗）
```

**总计最长等待：30 秒**（最坏情况 3 个源都超时）。任一源成功即返回。

**Release Note 解析：**

| 标记 | 含义 | 行为 |
|------|------|------|
| `[FORCED]` | 强制更新 | 弹窗不可关闭，按钮：["更新", "退出应用"] |
| `[MIN_VER:x.x.x]` | 最低容忍版本 | 本地版本 < MIN_VER → 等同于强制更新 |
| 无标记 | 普通更新 | 弹窗可关闭，按钮：["更新", "稍后"] |

**版本号比较：** 字符串按 `major.minor.patch` 语义比较，去掉前缀 `v`。

---

## 8. 数据库表结构（完整 DDL）

### 8.1 Device 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | TEXT | PRIMARY KEY | QR URL 的 MD5（32 位十六进制） |
| `name` | TEXT | NOT NULL | 用户自定义名称，默认取 id 前 8 位 |
| `qrUrl` | TEXT | NOT NULL | 原始 QR 码 URL 内容 |
| `starred` | INTEGER | NOT NULL, DEFAULT 0 | 0 = 未星标，1 = 星标 |
| `createdAt` | INTEGER | NOT NULL | Unix timestamp (ms) |

**索引：** 无需额外索引（主键已覆盖按 id 查询；`selectAll` 按 starred DESC + createdAt DESC 排序，数据量极小无需索引）。

### 8.2 WaterRecord 表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | 自增主键 |
| `deviceName` | TEXT | NOT NULL | 设备名称（冗余存储，设备删除后仍保留记录） |
| `timestamp` | INTEGER | NOT NULL | 开阀时间 Unix timestamp (ms) |

**设计说明：**
- `deviceName` 冗余存储而非外键关联 Device 表，因为用户可能删除设备但希望保留开阀记录
- 仅 `timestamp` 字段经常作为排序和筛选条件，但数据量极小（单用户场景）无需索引

---

## 9. CI/CD 配置

### 9.1 YAML 完整内容

```yaml
# .github/workflows/ios-build.yml
name: iOS Build

on:
  push:
    branches: [master]
    paths:
      - 'shared/**'
      - 'ios/**'
      - '.github/workflows/ios-build.yml'
  workflow_dispatch:

jobs:
  build:
    runs-on: macos-15
    timeout-minutes: 30

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Xcode
        uses: maxim-lobanov/setup-xcode@v1
        with:
          xcode-version: '16.0'

      - name: Cache Gradle
        uses: actions/cache@v4
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ runner.os }}-${{ hashFiles('shared/**/*.gradle.kts', 'shared/**/gradle-wrapper.properties') }}
          restore-keys: gradle-${{ runner.os }}-

      - name: Build KMP Shared Framework
        run: |
          cd shared
          ./gradlew :shared:linkDebugFrameworkIosArm64
        # 产物: shared/build/bin/iosArm64/debugFramework/Shared.framework

      - name: Build iOS IPA
        run: |
          cd ios
          xcodebuild \
            -project WaterValve.xcodeproj \
            -scheme WaterValve \
            -configuration Release \
            -archivePath build/WaterValve.xcarchive \
            archive \
            CODE_SIGN_IDENTITY="" \
            CODE_SIGNING_REQUIRED=NO \
            CODE_SIGNING_ALLOWED=NO
          # 从 archive 导出 unsigned IPA
          mkdir -p build/ipa
          cp -R build/WaterValve.xcarchive/Products/Applications/WaterValve.app build/ipa/
          cd build/ipa && zip -r ../WaterValve.ipa WaterValve.app/

      - name: Upload IPA Artifact
        uses: actions/upload-artifact@v4
        with:
          name: WaterValve-iOS
          path: ios/build/WaterValve.ipa
          retention-days: 30

      - name: Create GitHub Release (on tag push)
        if: startsWith(github.ref, 'refs/tags/v')
        uses: softprops/action-gh-release@v2
        with:
          files: ios/build/WaterValve.ipa
          generate_release_notes: true
```

### 9.2 构建配置说明

| 配置项 | 选择 | 说明 |
|--------|------|------|
| macOS 版本 | `macos-15` | GitHub Actions 最新 macOS runner（2025 年可用） |
| Xcode 版本 | `16.0` | 支持 Swift 5.9+ / SwiftUI / iOS 16 SDK |
| Java 版本 | Temurin 21 | KMP Gradle 构建需要 JDK 17+ |
| 签名 | 无签名（`CODE_SIGN_IDENTITY=""`） | IPA 由用户侧 AltStore 签名 |
| 产物保留 | 30 天 | GitHub Actions Artifacts 默认保留期 |
| 触发条件 | master 分支 push + 手动触发 | 不在每次 PR 构建以节省 CI 分钟数 |

### 9.3 Gradle Task

**构建 KMP Shared Framework 的 Gradle 命令：**

```bash
cd shared
./gradlew :shared:linkDebugFrameworkIosArm64
```

**产物路径：** `shared/build/bin/iosArm64/debugFramework/Shared.framework`

**Xcode 集成方式：** 在 Xcode Build Phase 中添加 Run Script Phase，调用 `BuildPhases/build-shared.sh`：

```bash
#!/bin/bash
# BuildPhases/build-shared.sh
cd "$SRCROOT/../shared"
./gradlew :shared:linkDebugFrameworkIosArm64
```

---

## 10. 错误处理策略

### 10.1 错误分类

| 错误类别 | 发生场景 | 处理策略 |
|----------|----------|----------|
| **网络不可用** | 所有 HTTP 请求 | Result.failure → UI 显示"网络不可用"+ 重试；Token 刷新静默失败 |
| **HTTP 403** | 设备同步请求 | markBanned() → 全局封禁弹窗（不可关闭） |
| **HTTP 4xx/5xx（非 403）** | 设备同步 / Token 交换 | Result.failure → UI 显示具体错误 + 重试 |
| **Token 过期** | UWC API 调用 | 自动尝试 refreshUwcToken() → 刷新失败则 clearAuth() → 回到登录页 |
| **签名验证失败** | UWC 解密响应 | 记录日志 → UI 显示"认证失败，请重新登录" → clearAuth() |
| **QR 码格式不符** | 扫码结果非设备 URL | 不震动/不提示，继续扫描 |
| **摄像头权限拒绝** | QR Scanner 启动 | QRScannerView 显示"请在设置中允许相机权限" |
| **后台任务被系统取消** | BGTaskScheduler 超时 | task.setTaskCompleted(success: false) → 等待下次调度 |

### 10.2 日志策略

- iOS 端使用 `os_log`（系统级日志，不写入文件）
- 错误级别事件：网络失败、403 封禁、Token 刷新失败
- 信息级别事件：登录成功、设备同步成功、开阀记录写入
- 日志不包含敏感数据（Token、密码、完整 QR URL）

---

## 11. 模块独立性与可测试性总结

### 11.1 模块依赖矩阵

```
                        AuthRepo   DeviceRepo   Keychain   UD      UwcApi   SyncApi  ReleaseApi  SQLDelight  Ktor
Login Module              ✓                                                                
Home Module                          ✓                                              
QR Scanner                                                                                 
Valve Module             ✓          ✓                                              
Record Module                       ✓                                              
Background Module        ✓                                                               
Update Module                                                               ✓      

AuthRepository                       ✓           ✓         ✓        ✓                         ✓
DeviceRepository          ✓                                           ✓                    ✓
```

- `✓` = 直接依赖
- 空白 = 无依赖
- **所有 iOS View 模块只依赖 1-2 个 Repository**，无跨模块依赖

### 11.2 每个模块的独立测试方案

| 模块 | 测试方式 | Mock 对象 |
|------|----------|----------|
| **AuthRepository** | Kotlin 单元测试 | FakeKeychainWrapper + FakeUserDefaultsWrapper + MockUwcApi（返回预设 JSON） |
| **DeviceRepository** | Kotlin 单元测试 | In-memory SQLDelight driver + MockSyncApi + FakeAuthRepository |
| **AuthViewModel** | Swift 单元测试 | FakeAuthRepository（in-memory Kotlin 对象） |
| **DeviceListViewModel** | Swift 单元测试 | FakeDeviceRepository（含预设设备列表） |
| **QRScannerViewModel** | Swift 单元测试 | 无外部依赖，直接测试状态切换 |
| **ValveViewModel** | Swift 单元测试 | FakeAuthRepository + FakeDeviceRepository |
| **RecordViewModel** | Swift 单元测试 | FakeDeviceRepository |
| **UpdateViewModel** | Swift 单元测试 | FakeReleaseApi（返回预设 AppRelease） |
| **BackgroundTaskManager** | Swift 单元测试 | FakeAuthRepository |

### 11.3 集成测试链路

以下为端到端集成测试场景，不用 mock，使用 in-memory SQLDelight + 本地 Flask 测试服务器：

1. **登录集成测试：** CAS ticket → UIS JWT → UWC Token → 写入 Keychain → loginState = Success
2. **设备同步集成测试：** pullFromCloud() → 解析 JSON → 写入 SQLDelight → devices 流更新
3. **开阀记录集成测试：** addRecord() → 写入 SQLDelight → records 流更新 → deleteRecord() → 流更新

---

## 12. 模块与需求功能追溯矩阵

| 需求功能 | iOS Native 模块 | Shared Repository | Shared API | Local DB |
|----------|----------------|-------------------|------------|----------|
| F1 CAS SSO 登录 | Login + WebViewContainer | AuthRepository | UwcApi + Crypto | — |
| F2 QR 扫码 | QRScanner | DeviceRepository (addDevice) | SyncApi (pushToCloud) | Device |
| F3 多设备管理 | Home | DeviceRepository | SyncApi | Device |
| F4 一键开阀 | Valve + WebViewContainer | AuthRepository (Token) + DeviceRepository (Record) | — | WaterRecord |
| F5 开阀记录 | Record | DeviceRepository | — | WaterRecord |
| F6 云端同步 | Home (触发) | DeviceRepository | SyncApi | Device |
| F7 后台 Token 刷新 | Background | AuthRepository | UwcApi (loginByToken) | — |
| F8 用户封禁 | BannedAlertView (全局) | AuthRepository (isBanned) | 403 由 SyncApi 抛出 | — |
| F9 更新检查 | Update | — | ReleaseApi | — |

---

## 13. 待开发阶段解决的遗留项

以下事项在详细设计中暂不确定，需在开发阶段查看 Android 源码后最终确定：

| # | 事项 | 原因 | 解决时机 |
|---|------|------|----------|
| 1 | JS Bridge 具体消息 key 名称 | 需与 Android 端 `WebView` JS 注入代码保持一致 | 开发 Valve Module 前查看 Android 源码 |
| 2 | UWC SPA URL 路由参数（设备 id 如何传入） | 需确认 SPA 页面的 query param 格式 | 开发 Valve Module 前查看 Android 源码 |
| 3 | CAS 登录页完整 URL | REQUIREMENTS.md 仅描述流程未给出具体 URL | 开发 Login Module 前从 Android 源码获取 |
| 4 | UIS / UWC API 完整路径 | 可能有版本号或路径差异 | 开发 AuthRepository 前从 Android 源码获取 |
| 5 | GitHub Release 仓库的 `owner/repo` | 需确认实际仓库名 | 开发 Update Module 前 |

---

> **文档状态：** ✅ 待评审 — 所有模块的接口、状态、交互、横切关注点已定义完整。开发阶段需先解决第 13 节遗留事项，然后按模块逐个实现和测试。
