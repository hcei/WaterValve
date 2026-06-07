# 河滴答@一键开阀器

[![Release](https://img.shields.io/badge/release-v1.0.0-blue)](https://github.com/hcei/WaterValve/releases/tag/v1.0.0)
[![Kotlin](https://img.shields.io/badge/kotlin-2.0.21-purple)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/API-26%2B-brightgreen)](https://developer.android.com/)

> 将繁琐的企业微信 H5 开阀流程简化为一键操作。扫码添加饮水机设备，点击即可控制水阀。

## 问题

学校通过企业微信内 H5 控制饮水机水阀，流程极其繁琐：

```
企业微信 → 工作台 → 校园一卡通 → 浏览器 → 生活用水 → 在线开阀 → 扫码 → 开阀
```

且只支持收藏一台设备。

## 解决方案

```
打开 App → CAS 自动登录(3秒) → 点击设备 → 开阀
```

- **多设备管理** — 扫码添加，自由切换
- **收藏常用设备** — 星标 + 桌面 Widget 一键直达
- **自定义名称** — 给每台设备起个容易记的名字

## 技术栈

| 层面 | 技术 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation 3 |
| DI | Hilt |
| 网络 | Retrofit + OkHttp |
| 加密 | TripleDES-CBC + MD5 + HMAC-SHA512 |
| 存储 | Room + DataStore |
| 扫码 | CameraX + ML Kit Barcode |
| 桌面组件 | Glance AppWidget |
| 后台任务 | WorkManager + HiltWorker |

## 项目结构

```
app/src/main/java/com/hgu/watervalve/
├── data/
│   ├── camera/QrCodeScanner.kt     # CameraX + ML Kit 扫码
│   ├── local/
│   │   ├── db/                     # Room (Device, WaterRecord)
│   │   └── datastore/              # Token 持久化
│   ├── remote/
│   │   ├── api/UwcApiService.kt    # Retrofit API 接口
│   │   ├── cookie/                 # SESSION Cookie 管理
│   │   └── crypto/                 # TripleDES 加密签名
│   └── repository/                 # AuthRepository / DeviceRepository
├── domain/model/                   # Device / WaterRecord
├── ui/
│   ├── home/                       # 设备列表 + QR扫描
│   ├── login/                      # CAS WebView 认证
│   ├── valve/                      # SPA 开阀控制
│   ├── record/                     # 开阀记录
│   ├── webview/                    # UwcWebView + h5call桥接
│   └── navigation/                 # 路由定义
├── widget/                         # 桌面 Widget (Glance)
└── worker/                         # Token 刷新 (WorkManager)
```

## 构建

### 环境要求

- JDK 21
- Android SDK 36
- Gradle 9.1 (wrapper 自带)

### 构建 APK

```bash
# 设置 JDK 路径
set JAVA_HOME=<你的JDK21路径>

# 构建 debug APK
gradlew assembleDebug --no-daemon

# 输出
# app/build/outputs/apk/debug/app-debug.apk
```

### 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 认证流程

```
① CAS 登录 (WebView)
   → CAS SSO 自动登录（若已有有效 Session）
   → 或手动输入学号+密码+短信验证码

② CAS ticket → UIS JWT (原生 HTTP)
   → GET /uias/.../cas/login?ticket=ST-xxx
   → Set-Cookie: SESSION → CookieJar 自动管理

③ UIS JWT → UWC Token (原生 HTTP)
   → POST /uwc_web_app/miniapps/loginByToken
   → paramStr = TripleDES({ uiastoken: UIS_JWT })
   → 解密响应 → 获取 UWC Token + 用户信息

④ UWC Token 有效 ~1天，WorkManager 每12h 自动刷新
   UIS JWT 有效 ~2年
```

## 下载

[📦 **v1.0.0 APK 下载**](https://github.com/hcei/WaterValve/releases/download/v1.0.0/app-debug.apk)

## License

MIT
