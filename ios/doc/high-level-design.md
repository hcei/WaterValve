# 河滴答 iOS 端 — 概要设计文档

> **版本：** v1.0  
> **状态：** 待评审  
> **关联需求：** REQUIREMENTS.md v1.0  
> **关联项目：** 河滴答@一键开阀器 (Android)

---

## 1. 文档概述

本文档基于需求文档 [REQUIREMENTS.md](../REQUIREMENTS.md) 进行模块划分，识别模块职责、接口、依赖关系及关键数据流。本文档**不涉及**接口实现细节、具体算法和 UI 布局——这些属于详细设计阶段。

---

## 2. 系统架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                       iOS App (SwiftUI)                      │
│  ┌────────┐ ┌──────────┐ ┌────────┐ ┌────────┐ ┌─────────┐ │
│  │ Login  │ │   Home   │ │ Valve  │ │ Record │ │ Update  │ │
│  │ Module │ │  Module  │ │ Module │ │ Module │ │ Module  │ │
│  └───┬────┘ └────┬─────┘ └───┬────┘ └───┬────┘ └────┬────┘ │
│      │           │           │          │           │        │
│  ┌───┴───────────┴───────────┴──────────┴───────────┴────┐  │
│  │                 Shared Bridge (KMP)                     │  │
│  │  ┌──────────────────┐  ┌──────────────────────────────┐│  │
│  │  │  Repositories    │  │  Platform Adapters (iosMain) ││  │
│  │  │  AuthRepository  │  │  SqlDelightDriver            ││  │
│  │  │  DeviceRepository│  │  KeychainWrapper             ││  │
│  │  └────────┬─────────┘  └──────────────────────────────┘│  │
│  │           │                                             │  │
│  │  ┌────────┴─────────┐  ┌──────────────────────────────┐│  │
│  │  │   Remote (Ktor)  │  │   Local (SQLDelight)         ││  │
│  │  │   Crypto (TDES)  │  │   Device / Record DB         ││  │
│  │  └────────┬─────────┘  └──────────────────────────────┘│  │
│  └───────────┼────────────────────────────────────────────┘  │
└──────────────┼──────────────────────────────────────────────┘
               │ HTTPS
┌──────────────┴──────────────────────────────────────────────┐
│                    Flask Backend (不变)                      │
│  sync_server/main.py                                        │
│  /api/devices  /api/stats  /api/admin  /api/release         │
└─────────────────────────────────────────────────────────────┘
```

**三层结构：**
1. **iOS 原生层 (SwiftUI)** — UI 展示、用户交互、平台能力（摄像头/后台任务/WebView）
2. **KMP Shared 层** — 网络、加密、数据库、业务逻辑（与 Android 共享代码）
3. **Flask 后端 (不变)** — 设备同步、统计、管理、更新代理

---

## 3. 模块划分

### 3.1 iOS 原生层模块

| 模块 | 职责 | 依赖 |
|------|------|------|
| **App Entry** | `@main` 入口，全局状态持有（`isBanned`、鉴权状态），App 生命周期管理 | 所有 UI 模块 |
| **Navigation** | `NavigationStack` 路由分发，页面导航 | 无（基础设施） |
| **Login Module** | CAS SSO 登录全流程，WKWebView 加载 CAS 页面，Cookie 管理，Token 交换 | Shared: `AuthRepository` |
| **Home Module** | 设备列表展示（排序/星标/重命名/删除），QR 码扫描入口 | Shared: `DeviceRepository` |
| **QR Scanner** | AVFoundation 摄像头采集 + Vision QR 识别，手电筒控制 | Home Module (回调) |
| **Valve Module** | 加载 UWC SPA 页面，注入 Token，JS Bridge 通信 | Shared: `AuthRepository` |
| **Record Module** | 开阀记录列表（时间倒序），单条/全部清除 | Shared: `DeviceRepository` |
| **WebView** | 可复用 WKWebView 封装，Cookie/UA 注入，JS Bridge 基础设施 | 无（基础设施） |
| **Background** | BGTaskScheduler 注册与回调，Token 刷新调度 | Shared: `AuthRepository` |
| **Update Module** | GitHub Release 版本检查，强制更新判断(`[FORCED]`/`[MIN_VER]`)，多源保底 | 无（直接 HTTP） |

### 3.2 KMP Shared 层模块

| 模块 | 所在路径 | 职责 |
|------|----------|------|
| **Domain Models** | `shared/commonMain/domain/model/` | `Device`、`WaterRecord`、`AppRelease`、`UserInfo` 等数据类 |
| **AuthRepository** | `shared/commonMain/data/repository/` | CAS ticket → UIS JWT → UWC Token 完整认证链，Token 刷新，用户状态管理 |
| **DeviceRepository** | `shared/commonMain/data/repository/` | 设备 CRUD（本地 + 云端双向同步），开阀记录管理 |
| **Remote API** | `shared/commonMain/data/remote/api/` | Ktor HTTP 客户端，所有后端接口定义 |
| **Crypto** | `shared/commonMain/data/remote/crypto/` | TripleDES-CBC 加密/解密，MD5 摘要，HMAC-SHA512 签名 |
| **Local DB** | `shared/commonMain/data/local/` | SQLDelight 表定义、查询 |
| **Constants** | `shared/commonMain/util/` | URL、UA、密钥等常量 |
| **Platform Adapters** | `shared/iosMain/` | iOS 专用 SQLDelight Driver、Keychain 访问等平台实现 |

### 3.3 Flask 后端（不变）

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/devices/{userId}` | GET | 拉取设备列表（封禁用户 403） |
| `/api/devices/{userId}` | POST | 全量替换设备列表（封禁用户 403） |
| `/api/stats` | GET | 统计信息 |
| `/api/admin/ban` | POST | 封禁用户 |
| `/api/admin/unban` | POST | 解封用户 |
| `/api/admin/banned` | GET | 封禁列表 |
| `/api/release/latest` | GET | 更新元数据代理 |
| `/api/release/apk?tag=xxx` | GET | APK 下载代理（iOS 不调用） |

---

## 4. 模块关系与数据流

### 4.1 模块依赖图

```
                    iOS Native Layer
                    ================
                         │
          ┌──────────────┼──────────────┐
          │              │              │
     Login Module    Home Module    Valve Module   Record Module   Update Module
          │              │              │              │               │
          │         ┌────┴────┐         │              │               │
          │     QR Scanner     │         │              │               │
          │                    │         │              │               │
          └──────────┬─────────┴─────────┴──────────────┘               │
                     │                                                  │
                     │      Shared Layer (KMP)                          │
                     │      ====================                        │
                     ▼                                                  │
              ┌──────────────┐                                          │
              │ AuthRepository│◄──────────────────── Background Module  │
              └──────┬───────┘                                          │
                     │                                                  │
              ┌──────┴───────┐                                          │
              │DeviceRepository│                                         │
              └──────┬───────┘                                          │
                     │                                                  │
          ┌──────────┼──────────┐                                       │
          │          │          │                                       │
     ┌────┴────┐ ┌───┴────┐     │                                       │
     │  Ktor   │ │SQLDelight│    │                                       │
     │ (Remote)│ │ (Local) │    │                                       │
     └────┬────┘ └─────────┘    │                                       │
          │                     │                                       │
          │               GitHub Release API ◄─── Update Module ────────┘
          │
     ┌────┴────┐
     │  Flask  │
     │ Backend │
     └─────────┘
```

### 4.2 核心数据流

#### F1 — 登录流程

```
Login Module                    AuthRepository                  Flask/CAS/UIS/UWC
───────────                    ───────────────                  ─────────────────
    │                                │                               │
    │── 加载 CAS WebView ──────────►│                               │
    │                                │                               │
    │◄── WKWebView 展示 CAS 页面 ───│                               │
    │                                │                               │
    │── CAS 登录完成 (ST-xxx) ──────►│                               │
    │                                │── GET /uias/.../cas/login ────►│
    │                                │◄── Set-Cookie: SESSION ───────│
    │                                │── POST /uias/.../token-h5 ────►│
    │                                │◄── UIS JWT ───────────────────│
    │                                │── POST /uwc_web_app/.../login ►│
    │                                │   (paramStr = TDES(UIS_JWT))  │
    │                                │◄── UWC Token + UserInfo ──────│
    │                                │                               │
    │◄── 登录成功 ──────────────────│                               │
    │                                │                               │
    │── 拉取云端设备 ───────────────►│── GET /api/devices/{userId} ──►│
    │                                │◄── deviceList JSON ───────────│
    │                                │                               │
    │                                │── 写入 SQLDelight ────────────►│ (本地)
    │◄── deviceList ────────────────│                               │
```

#### F2 — 扫码添加设备

```
QR Scanner                      Home Module              DeviceRepository
─────────                      ───────────              ────────────────
    │                               │                          │
    │── QR 识别结果(URL) ──────────►│                          │
    │                               │── MD5(URL) = deviceId ─►│
    │                               │                          │── 写入 SQLDelight
    │                               │                          │── POST 全量推送到云端
    │                               │◄── 成功 ────────────────│
    │◄── 更新列表 ─────────────────│                          │
```

#### F3 — 设备管理（重命名/星标/删除）

```
Home Module                   DeviceRepository              Flask
───────────                   ────────────────              ─────
    │                               │                         │
    │── 重命名(id, newName) ───────►│                         │
    │                               │── 更新 SQLDelight ─────►│ (本地)
    │                               │── POST 全量推送到云端 ──►│
    │                               │◄── 200 OK ──────────────│
    │◄── 成功 ─────────────────────│                         │
    │                               │                         │
    │── 删除(id) ──────────────────►│                         │
    │                               │── 删除 SQLDelight ─────►│ (本地)
    │                               │── POST 全量推送到云端 ──►│
    │                               │◄── 200 OK ──────────────│
    │◄── 成功 ─────────────────────│                         │

注：云端同步采用「最后写入胜出」，本地变更为准，全量推送。
    星标/排序等纯本地属性不同步到云端。
```

#### F4 — 一键开阀

```
Valve Module                  AuthRepository            UWC SPA (WkWebView)
───────────                   ───────────────           ───────────────────
    │                               │                          │
    │── 获取 UWC Token ────────────►│                          │
    │◄── token ────────────────────│                          │
    │                               │                          │
    │── 加载 SPA URL ────────────────────────────────────────►│
    │── JS Bridge 注入 token ────────────────────────────────►│
    │                               │                          │
    │◄── SPA 展示开阀界面 ────────────────────────────────────│
    │                               │                          │
    │   (用户点击开阀按钮)           │                          │
    │◄── JS → Native: 开阀成功通知 ──────────────────────────│
    │                               │                          │
    │── 写入开阀记录 ────────────────────────────────────────►│ (DeviceRepository)
```

#### F5 — 开阀记录

```
Record Module                 DeviceRepository           SQLDelight
─────────────                 ────────────────           ──────────
    │                               │                       │
    │── 查询列表 ───────────────────►│── SELECT * ORDER BY   │
    │                               │   time DESC ──────────►│
    │◄── records ──────────────────│                       │
    │                               │                       │
    │── 清除单条(id) ──────────────►│── DELETE WHERE id     │
    │── 清除全部 ──────────────────►│── DELETE ALL ─────────►│
```

#### F7 — 后台 Token 刷新

```
Background Module             AuthRepository              UWC Server
────────────────             ───────────────              ──────────
    │                               │                         │
    │── BGTaskScheduler 触发 ──────►│                         │
    │   (系统调度，约12h)            │                         │
    │                               │── UIS JWT (从 Keychain)  │
    │                               │── POST /uwc_web_app/    │
    │                               │   /miniapps/loginByToken►│
    │                               │◄── 新 UWC Token ────────│
    │                               │── 写入 Keychain ────────│
    │◄── 刷新完成 ─────────────────│                         │
```

#### F8 — 用户封禁处理

```
任一模块调用                  AuthRepository              Flask
DeviceRepository.xxx          (isBanned 标志)
───────────────               ───────────────              ────
    │── syncDevices() ──────►│── GET /api/devices/ ────────►│
    │                         │◄── HTTP 403 ────────────────│
    │                         │── isBanned = true           │
    │                         │── 持久化到 UserDefaults     │
    │◄── BannedException ────│                             │
    │                         │                             │
    │── UI 弹出封禁弹窗 ─────→│                             │
    │   (不可关闭)             │                             │
    │   [退出应用] [联系开发者] │                             │
```

#### F9 — 更新检查

```
Update Module                 GitHub / Gitee / PythonAnywhere
─────────────                 ───────────────────────────────
    │ (App 启动时)                         │
    │── GET /repos/.../releases/latest ───►│ (主)
    │◄── tag_name, body ──────────────────│
    │                                      │
    │   (若 GitHub 不可达)                  │
    │── GET Gitee API ────────────────────►│ (备1)
    │                                      │
    │   (若 Gitee 不可达)                   │
    │── GET /api/release/latest ──────────►│ (备2 — PythonAnywhere)
    │                                      │
    │── 解析 [FORCED] / [MIN_VER]          │
    │── 比较版本号                          │
    │── 弹窗提示 / 强制更新弹窗              │
    │── 用户点"更新" → 打开 AltStore        │
```

---

## 5. 共享层接口定义

> 以下为 Kotlin 类/方法签名概览，Swift 通过 Kotlin/Native 自动桥接调用。

### 5.1 AuthRepository

```kotlin
// Swift 调用示例: AuthRepository.shared.loginProgress → published state
class AuthRepository {
    // 登录状态流（Swift 侧观察）
    val loginState: StateFlow<LoginState>  // Idle | Loading(step) | Success | Failed

    // 触发 CAS 登录（返回值给 Swift 判断是否启动 WebView）
    fun startCasLogin(): String  // 返回 CAS 登录 URL

    // CAS ticket → UIS JWT → UWC Token（WebView 拦截到 ticket 后调用）
    suspend fun exchangeCasTicket(ticket: String): LoginResult

    // 用 UIS JWT 刷新 UWC Token（后台任务调用）
    suspend fun refreshUwcToken(): Boolean

    // 当前是否有有效 Token
    fun hasValidToken(): Boolean

    // 获取当前 UWC Token（供 Valve Module 注入 SPA）
    fun getUwcToken(): String?

    // 用户封禁处理
    val isBanned: Boolean
    fun markBanned()
}
```

### 5.2 DeviceRepository

```kotlin
class DeviceRepository {
    // 设备列表（Swift 侧观察，自动反映本地数据库变化）
    val devices: Flow<List<Device>>

    // 设备操作
    suspend fun addDevice(qrUrl: String): Device          // MD5 + 本地写入 + 云端同步
    suspend fun renameDevice(deviceId: String, name: String)
    suspend fun starDevice(deviceId: String, starred: Boolean)
    suspend fun deleteDevice(deviceId: String)

    // 云端同步
    suspend fun pullFromCloud()      // 登录后拉取
    suspend fun pushToCloud()        // 本地变更后推送

    // 开阀记录
    val records: Flow<List<WaterRecord>>
    suspend fun addRecord(deviceName: String)
    suspend fun deleteRecord(id: Long)
    suspend fun deleteAllRecords()
}
```

### 5.3 数据模型

```kotlin
data class Device(
    val id: String,          // QR 内容的 MD5
    val name: String,        // 用户自定义名
    val qrUrl: String,       // 原始 QR 内容
    val starred: Boolean,    // 星标（仅本地）
    val createdAt: Long      // Unix timestamp ms
)

data class WaterRecord(
    val id: Long,
    val deviceName: String,
    val timestamp: Long      // Unix timestamp ms
)

data class UserInfo(
    val userId: String,
    val nickname: String
)

data class AppRelease(
    val tagName: String,     // e.g. "v1.1.1"
    val body: String,        // Release note, 含 [FORCED]/[MIN_VER]
    val downloadUrl: String
)
```

---

## 6. iOS 原生层模块间通信

| 方向 | 方式 | 说明 |
|------|------|------|
| 任意模块 → Shared Repository | 直接调用 Kotlin/Native 导出接口 | 同步/异步由 Kotlin `suspend` → Swift `async` 自动转换 |
| Shared State → UI | `StateFlow` 通过 `Combine` bridge → SwiftUI `@Published` | ViewModel 层封装 |
| QR Scanner → Home Module | Swift 闭包回调 | 扫码成功后回传 URL 字符串 |
| Valve SPA → Valve Module | WKWebView `userContentController` 消息 | JS `postMessage` → Swift WKScriptMessageHandler |
| Valve Module → DeviceRepository | 直接调用 | 记录开阀时间 |
| Background → AuthRepository | 直接调用 | `refreshUwcToken()` |
| Navigation 跨模块跳转 | `NavigationStack` + `NavigationPath` | Swift 原生路由 |

---

## 7. 关键技术决策

| # | 决策 | 选择 | 理由 |
|---|------|------|------|
| 1 | KMP shared → Swift 导出 | Kotlin/Native 自动生成 | 零额外代码，符合化繁为简原则 |
| 2 | SwiftUI 架构 | MVVM | 与 Android ViewModel 模式一致 |
| 3 | DI 方案 | 手动构造函数注入 | 依赖项少（2 个 Repository），无需框架 |
| 4 | 数据同步冲突 | 最后写入胜出 | 单用户场景，API 全量替换，无需复杂合并 |
| 5 | 离线策略 | Offline-first | 始终读写 SQLDelight，云端异步同步 |
| 6 | shared Framework 集成 Xcode | Gradle script in Build Phase | 零额外依赖，CI 原生支持 |
| 7 | Token 安全存储 | iOS Keychain | 系统级安全，与 Android KeyStore 对等 |

---

## 8. 模块与需求功能追溯

| 需求功能 | iOS 模块 | Shared 模块 |
|----------|----------|-------------|
| F1 CAS SSO 登录 | Login Module + WebView | AuthRepository, Remote API, Crypto |
| F2 QR 扫码 | QR Scanner | DeviceRepository |
| F3 多设备管理 | Home Module | DeviceRepository, Local DB |
| F4 一键开阀 | Valve Module + WebView | AuthRepository (Token) |
| F5 开阀记录 | Record Module | DeviceRepository, Local DB |
| F6 云端同步 | Home Module (触发) | DeviceRepository, Remote API |
| F7 后台 Token 刷新 | Background Module | AuthRepository, Remote API |
| F8 用户封禁 | App Entry (全局弹窗) | AuthRepository |
| F9 更新检查 | Update Module | 无（直接 HTTP） |

---

## 9. 目录结构（概要）

```
WaterValve/
├── shared/                              ← KMP 共享模块（新建）
│   ├── src/
│   │   ├── commonMain/kotlin/com/hgu/watervalve/shared/
│   │   │   ├── domain/model/            ← Device, WaterRecord, UserInfo, AppRelease
│   │   │   ├── data/
│   │   │   │   ├── local/               ← SQLDelight .sq 文件
│   │   │   │   ├── remote/
│   │   │   │   │   ├── api/             ← Ktor API 定义
│   │   │   │   │   └── crypto/          ← TripleDES, MD5, HMAC-SHA512
│   │   │   │   └── repository/          ← AuthRepository, DeviceRepository
│   │   │   └── util/                    ← Constants, URL, UA
│   │   ├── androidMain/                 ← Android 平台实现（现有）
│   │   └── iosMain/                     ← iOS 平台实现（新建）
│   └── build.gradle.kts
│
├── ios/                                 ← iOS 端（新建）
│   ├── WaterValve.xcodeproj
│   ├── WaterValve/
│   │   ├── App.swift                    ← @main 入口，全局状态
│   │   ├── ViewModels/                  ← AuthViewModel, DeviceViewModel
│   │   ├── Views/
│   │   │   ├── Login/                   ← CASLoginView, LoginProgressView
│   │   │   ├── Home/                    ← DeviceListView, DeviceRowView
│   │   │   ├── Valve/                   ← ValveWebView
│   │   │   ├── Record/                  ← RecordListView
│   │   │   ├── WebView/                 ← WKWebViewRepresentable, JS Bridge
│   │   │   └── Common/                  ← BannedAlert, UpdateAlert
│   │   ├── Navigation/                  ← AppNavigation (NavigationStack)
│   │   ├── Background/                  ← BGTaskScheduler 注册
│   │   ├── Update/                      ← UpdateChecker
│   │   └── Utils/                       ← 平台工具（权限请求等）
│   └── BuildPhases/
│       └── build-shared.sh              ← Gradle 构建 shared framework 脚本
│
├── app/                                 ← Android 端（已有，不变）
├── sync_server/                         ← Flask 后端（已有，不变）
└── .github/workflows/
    └── ios-build.yml                    ← CI（新建 macOS runner）
```

---

## 10. 待详细设计阶段解决的问题

以下问题在本概要设计中记录但**推迟到详细设计阶段**解决：

1. **SQLDelight 表具体结构** — Device 表、Record 表的字段、索引、迁移策略
2. **JS Bridge 协议细节** — SPA 与 Native 的消息格式、生命周期
3. **Keychain 封装方案** — `iosMain` 中 Keychain adapter 的具体实现
4. **BGTaskScheduler 注册时机** — 首次启动注册 vs 每次启动注册
5. **WKWebView Cookie 持久化细节** — HTTPCookieStorage 与 WKWebView 进程隔离的兼容
6. **更新检查多源保底的降级顺序与超时策略**
7. **CI YAML 完整配置** — Xcode 版本、签名策略（无签名 IPA）、产物存放位置
8. **KMP shared Framework 的 Gradle task 具体参数**

---

> **文档状态：** ✅ 待评审 — 模块划分与关系已完成，可进入详细设计阶段。
