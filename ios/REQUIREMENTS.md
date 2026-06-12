# 河滴答 iOS 端需求文档

> **版本：** v1.0  
> **状态：** 待评审  
> **关联项目：** 河滴答@一键开阀器 (Android)  
> **iOS 最低支持：** iOS 16.0

---

## 1. 项目背景

将 Android 版「河滴答@一键开阀器」迁移至 iOS 平台，实现核心功能在 iOS 端完整复刻。Android 端已完成开发并通过测试（v1.1.1），本需求文档基于现有 Android 代码库的功能范围制定。

---

## 2. 核心决策

| 决策项 | 选择 | 说明 |
|--------|------|------|
| **开发方式** | Kotlin Multiplatform (KMP) | 共享加密/网络/数据逻辑层，iOS UI 用 SwiftUI |
| **iOS UI** | SwiftUI（原生） | 不使用 Compose Multiplatform 做 iOS UI |
| **最低版本** | iOS 16.0 | 内部使用，无需覆盖旧设备 |
| **发布渠道** | AltStore / SideStore | 无 Apple 开发者账号，不上架 App Store |
| **应用更新** | 检测 + 引导跳转更新页 | iOS 无法自动安装 IPA |
| **代码结构** | 单仓库 (Monorepo) | 在现有 `WaterValve/` 下新建 KMP 模块 |
| **CI/CD** | GitHub Actions macOS runner | 无 Mac 设备，云端构建 |
| **后端** | 保持现有 Flask 不变 | `sync_server/` 统一服务 Android + iOS |
| **跨平台数据同步** | 同 userId 同数据 | 一个账号在两端看到同一份设备列表 |
| **桌面 Widget** | ❌ iOS 端不开发 | 从需求中裁剪 |

---

## 3. 功能范围

### 3.1 功能总览

| # | 功能 | Android 实现 | iOS 实现 | 状态 |
|---|------|-------------|----------|------|
| F1 | CAS SSO WebView 登录 | WebView + CookieJar | WKWebView + HTTPCookieStorage | 迁移 |
| F2 | QR 码扫码添加设备 | CameraX + ML Kit | AVFoundation + Vision | 迁移 |
| F3 | 多设备管理（添加/重命名/星标/删除） | Room + Compose | SQLDelight + SwiftUI | 迁移 |
| F4 | 一键开阀（WebView SPA） | WebView + JS Bridge | WKWebView + JS Bridge | 迁移 |
| F5 | 开阀记录查看 | Room + Compose | SQLDelight + SwiftUI | 迁移 |
| F6 | 云端设备同步 | Retrofit → Flask | Ktor Client → Flask | 迁移 |
| F7 | 后台 Token 刷新 | WorkManager 12h | BGTaskScheduler | 迁移 |
| F8 | 用户封禁处理 | 403 → BannedException | 同逻辑 | 迁移 |
| F9 | 应用更新检查 | GitHub Release → APK 下载安装 | GitHub Release → 引导跳转 AltStore | 适配 |
| F10 | 桌面 Widget | Glance | ❌ 裁剪 | 不开发 |

### 3.2 功能详细说明

#### F1 — CAS SSO WebView 登录

```
用户打开 App
  → 若无有效 Token，展示 WKWebView 加载 CAS 登录页
  → CAS SSO 自动登录（若已有有效 CAS Session）
  → 或手动输入学号+密码+短信验证码
  → 拦截 CAS ticket (ST-xxxxx)
  → 原生 HTTP 请求交换 UIS JWT
  → 原生 HTTP 请求交换 UWC Token
  → 登录成功 → 拉取云端设备 → 进入首页
```

**要求：**
- UA 使用普通 Chrome iOS UA，禁止使用微信/企业微信 UA
- WKWebView Cookie 自动持久化，复用 CAS Session
- 登录流程分为 3 阶段显示进度（同 Android）

#### F2 — QR 码扫码添加设备

**要求：**
- 使用 AVFoundation 获取摄像头画面
- 使用 Vision 框架识别 QR 码内容
- QR 码内容格式与 Android 端一致：饮水机设备唯一标识 URL
- 扫码成功 → 计算 MD5 作为设备唯一 ID → 存入本地数据库 → 同步到云端
- 支持手电筒开关

#### F3 — 多设备管理

**要求：**
- 设备列表展示（图标 + 自定义名称 + 星标状态）
- 添加设备：扫码 / 手动输入
- 重命名设备：自定义名称
- 星标设备：置顶显示
- 删除设备：确认弹窗 → 删除本地 + 同步云端
- 设备唯一 ID = QR 内容的 MD5
- 数据本地存储使用 SQLDelight

#### F4 — 一键开阀

**要求：**
- 点击设备 → 加载 UWC SPA 页面 (`uwc_webapp/`)
- WKWebView 加载 SPA 页面
- 注入 UWC Token 使 SPA 自动完成认证
- 用户点击开阀按钮 → SPA 内部处理
- 注意：SPA 深度依赖微信 JS-SDK，仅加载页面不 mock 微信桥接
- URL 路径注意区分：SPA 前端 `uwc_webapp/`(无下划线) vs API `uwc_web_app/`(有下划线)

#### F5 — 开阀记录

**要求：**
- 记录开阀时间、设备名称
- SQLDelight 本地存储
- 列表展示（时间倒序）
- 支持清除单条 / 全部记录

#### F6 — 云端设备同步

**要求：**
- 同步目标：现有 Flask 后端 `https://hcei.pythonanywhere.com`
- API 接口不变：
  - `GET /api/devices/{userId}` — 拉取设备
  - `POST /api/devices/{userId}` — 全量推送设备
- 本地数据变更后自动推送到云端
- 登录成功后自动拉取云端设备
- 网络异常时本地数据不受影响

#### F7 — 后台 Token 刷新

**要求：**
- UWC Token 有效期约 1 天，需定期刷新
- 使用 iOS `BGTaskScheduler` 注册后台刷新任务
- 刷新频率：每 12 小时
- 刷新流程：用 UIS JWT → 换新的 UWC Token
- UIS JWT 有效约 2 年，无需频繁刷新
- 注意：iOS 后台任务由系统调度，非精确定时（这是 iOS 限制，可接受）

#### F8 — 用户封禁处理

**要求：**
- 向后端请求设备时若返回 HTTP 403
- App 本地捕获 → 设置 `isBanned` 状态
- UI 弹出封禁提示弹窗（不可关闭）
- 弹窗按钮：「退出应用」/「联系开发者」
- `isBanned` 持久化到本地存储
- 后端封禁管理 API 不变

#### F9 — 应用更新检查（适配）

**与 Android 的差异：**

| 环节 | Android | iOS |
|------|---------|-----|
| 检查方式 | GitHub Release API | 同 |
| 下载 | 下载 APK | 不下载 |
| 安装 | Intent 调用系统安装器 | 无法自动安装 |
| 用户操作 | 点击「安装」 | 引导去 AltStore 更新 |

**要求：**
- App 启动时检查 GitHub Release 最新版本
- 本地版本号 vs 远程版本号
- 若有新版本 → 弹窗提示
  - Release body 中 `[FORCED]` 标记 = 强制更新（弹窗不可关闭）
  - `[MIN_VER:x.x.x]` 标记 = 最低容忍版本
- 点击「更新」→ 打开 AltStore 页面或更新引导页
- 多源保底：GitHub Releases → Gitee 镜像 → PythonAnywhere 代理

#### F10 — 桌面小组件

**❌ 不在 iOS 端开发。**

---

## 4. 技术架构

### 4.1 模块划分

```
WaterValve/
├── shared/                          ← KMP 共享模块（新建）
│   ├── src/
│   │   ├── commonMain/              ← 跨平台共享代码
│   │   │   ├── data/
│   │   │   │   ├── local/           ← SQLDelight 数据库
│   │   │   │   ├── remote/          ← Ktor HTTP 客户端
│   │   │   │   │   ├── api/         ← API 接口定义
│   │   │   │   │   └── crypto/      ← TripleDES 加密（纯 Kotlin）
│   │   │   │   └── repository/      ← AuthRepository / DeviceRepository
│   │   │   ├── domain/
│   │   │   │   └── model/           ← Device / WaterRecord / AppRelease
│   │   │   └── util/                ← Constants / 工具函数
│   │   ├── androidMain/             ← Android 平台实现（SQLDelight Driver 等）
│   │   └── iosMain/                 ← iOS 平台实现（SQLDelight Driver 等）
│   └── build.gradle.kts
│
├── ios/                             ← iOS 端（新建）
│   ├── WaterValve.xcodeproj
│   ├── WaterValve/
│   │   ├── App.swift                ← @main 入口
│   │   ├── UI/
│   │   │   ├── Login/               ← CAS WebView 登录
│   │   │   ├── Home/                ← 设备列表 + QR 扫描
│   │   │   ├── Valve/               ← SPA 开阀 WebView
│   │   │   ├── Record/              ← 开阀记录
│   │   │   ├── WebView/             ← WKWebView + JS Bridge
│   │   │   └── Navigation/          ← NavigationStack 路由
│   │   ├── Background/              ← BGTaskScheduler Token 刷新
│   │   └── Update/                  ← 更新检查
│   └── Podfile / SPM                ← 依赖管理
│
├── app/                             ← Android 端（已有）
├── sync_server/                     ← Flask 后端（已有，不变）
└── .github/workflows/               ← CI（新建 macOS runner）
    └── ios-build.yml
```

### 4.2 技术栈对比

| 层面 | Android | iOS |
|------|---------|-----|
| **语言** | Kotlin | Swift 5.9+ |
| **UI 框架** | Jetpack Compose | SwiftUI |
| **导航** | Navigation 3 | NavigationStack |
| **网络** | Retrofit + OkHttp | Ktor Client (shared) |
| **加密** | TripleDES-CBC + MD5 + HMAC-SHA512 | 共享 Kotlin 实现 (shared) |
| **本地数据库** | Room | SQLDelight (shared) |
| **键值存储** | DataStore | NSUserDefaults / DataStore (shared) |
| **DI** | Hilt | 手动注入 / Swinject |
| **扫码** | CameraX + ML Kit | AVFoundation + Vision |
| **WebView** | Android WebView | WKWebView |
| **后台任务** | WorkManager | BGTaskScheduler |
| **更新检查** | GitHub API → APK 安装 | GitHub API → URL Scheme 跳转 |

### 4.3 共享层详细边界

**放入 `shared/commonMain`（跨平台共享）：**
- TripleDES 加密/解密 (`UwcCrypto`)
- MD5 摘要
- HMAC-SHA512 签名
- HTTP API 接口定义 (Ktor)
- 数据模型 (Device, WaterRecord, AppRelease)
- 业务逻辑 (AuthRepository, DeviceRepository)
- Cookie 管理逻辑
- URL / UA / 密钥常量

**保留在各平台原生实现：**
- UI 层（Compose / SwiftUI）
- WebView（Android WebView / WKWebView）
- 扫码（CameraX / AVFoundation）
- 后台任务（WorkManager / BGTaskScheduler）
- Widget（Glance / 无）
- 文件存储路径
- Keychain / KeyStore 安全存储

---

## 5. 认证流程（iOS 适配）

```
① CAS 登录 (WKWebView)
   → CAS SSO 自动登录（若已有有效 CAS Session Cookie）
   → 或手动输入学号+密码+短信验证码

② CAS ticket → UIS JWT (Ktor 原生 HTTP)
   → GET /uias/authentication/index/cas/login?ticket=ST-xxx
   → Set-Cookie: SESSION → 自动持久化到 HTTPCookieStorage
   → POST /uias/authentication/index/token-h5 → UIS JWT (~2年)

③ UIS JWT → UWC Token (Ktor 原生 HTTP)
   → POST /uwc_web_app/miniapps/loginByToken
   → paramStr = TripleDES({ uiastoken: UIS_JWT })
   → 解密响应 → 获取 UWC Token + 用户信息

④ Token 存储到 Keychain
⑤ BGTaskScheduler 每 12h 用 UIS JWT 刷新 UWC Token
```

**关键约束（同 Android）：**
- UA 必须使用普通 Chrome iOS UA，不能使用微信/企业微信 UA
- URL 下划线区分：SPA 前端 `uwc_webapp/` vs API `uwc_web_app/`
- 所有 UWC API 请求在原生层完成（需要 TripleDES 加密），不能交给 WKWebView

---

## 6. 加密方案（共享 Kotlin 实现）

```
算法：       TripleDES-CBC-Pkcs7
密钥：       684523174589651002354157
IV：         00000000

UWC 签名：   MD5 → Base64
UIS 签名：   HMAC-SHA512, 密钥: "hzsun.com.uwc的sign验签加密key"
```

共享 Kotlin 代码的加密逻辑与 Android 端完全一致，iOS 端通过 KMP 直接调用，无需重新实现。

---

## 7. 后端接口（不变）

Base URL: `https://hcei.pythonanywhere.com`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/devices/{userId}` | 获取设备列表（封禁用户返回 403） |
| POST | `/api/devices/{userId}` | 全量替换设备列表（封禁用户返回 403） |
| GET | `/api/stats` | 统计信息 |
| POST | `/api/admin/ban` | 封禁用户 |
| POST | `/api/admin/unban` | 解封用户 |
| GET | `/api/admin/banned` | 封禁列表 |
| GET | `/api/release/latest` | 更新元数据代理 |
| GET | `/api/release/apk?tag=xxx` | APK 下载代理（iOS 不调用） |

---

## 8. CI/CD 方案

### 8.1 GitHub Actions macOS Runner

```yaml
name: iOS Build
on:
  push:
    branches: [master]
  workflow_dispatch:

jobs:
  build:
    runs-on: macos-15      # 最新 macOS runner
    steps:
      - uses: actions/checkout@v4
      - name: Setup Java 21    # KMP 需要 JDK
      - name: Setup Xcode
      - name: Build shared KMP framework
      - name: Build iOS IPA
      - name: Upload IPA artifact
```

### 8.2 构建产物

- IPA 文件（unsigned，需通过 AltStore / SideStore 签名安装）
- 每次构建上传到 GitHub Actions Artifacts 或 GitHub Release

---

## 9. IPA 分发方案

### 9.1 安装流程

```
用户设备：
  1. 安装 AltStore / SideStore（一次性）
  2. 下载 IPA 文件（从 GitHub Release）
  3. 在 AltStore/SideStore 中打开 IPA 完成签名安装
  4. 每 7 天刷新签名（AltStore 可设置自动刷新）
```

### 9.2 更新流程

```
App 内检测到新版本
  → 弹窗提示更新内容
  → 用户点击"更新"
  → 打开浏览器跳转到 GitHub Release 页面
  → 用户下载新 IPA → AltStore 重新签名安装
```

### 9.3 限制说明

- 免费 Apple ID 每 7 天需要重签
- 同时最多安装 3 个侧载 App
- 更新无法静默完成，需用户手动操作

---

## 10. 非功能需求

| 类别 | 要求 |
|------|------|
| **最低 iOS 版本** | iOS 16.0 |
| **适配设备** | iPhone（不含 iPad 优化） |
| **屏幕方向** | 竖屏 |
| **网络** | 需要互联网连接 |
| **摄像头** | 扫码功能需要摄像头权限 |
| **本地化** | 中文（简体） |
| **暗色模式** | 跟随系统 |
| **性能** | 开阀响应 < 3 秒（净网络时间） |

---

## 11. 裁剪清单

以下 Android 功能**不在 iOS 端实现**：

- [x] ~~桌面 Widget（Glance → WidgetKit）~~ — 明确裁剪
- [ ] 应用内自动安装更新 — 改为引导跳转（iOS 平台限制）

---

## 12. 已确认项

| # | 事项 | 结论 |
|---|------|------|
| 1 | iOS 端 App 图标 | 沿用 Android 端图标 (`ic_launcher_foreground_new.png`)，不单独设计 |
| 2 | iOS 桌面显示名称 | 沿用"小河滴答"，不改名 |
| 3 | GitHub Actions 费用 | 接受超出免费额度（2000分钟/月）后的付费（$0.08/分钟） |

---

## 13. 参考文件

| 文件 | 说明 |
|------|------|
| `app/src/main/java/com/hgu/watervalve/` | Android 端完整源码 |
| `sync_server/main.py` | Flask 后端源码 |
| `README.md` | 项目 README |
| `AGENTS.md` | 项目地图 |

---

> **文档状态：** ✅ 已冻结，可进入开发阶段。
