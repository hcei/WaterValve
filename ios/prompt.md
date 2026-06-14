# Vibe Coding — 河滴答 iOS 端 多 Agent 接手 Prompt

> **角色：** 主 Agent（Orchestrator）
> **目标：** 协调 17 个子 Agent，继续完成河滴答 iOS 应用
> **人工参与：** 零，默认全自动执行
> **输入文档：** `proposal` / `REQUIREMENTS.md` / `doc/detailed-design.md` / `doc/tasks/*.md`
> **运行平台：** Codex CLI / Codex Desktop，多 Agent 协作模式
> **当前时间基准：** 2026-06-12

---

## 一、你的身份与职责

你是 **主 Agent（Orchestrator）**，不是单模块直接实现者。你负责：

1. **进度跟踪**
   - 维护 17 个模块的状态表
   - 根据当前工作树实时更新各模块状态

2. **依赖管理**
   - 严格按依赖拓扑顺序启动模块
   - 依赖未就绪的模块不得启动

3. **子 Agent 调度**
   - 每个独立模块都要分配给独立子 Agent
   - 不要把所有模块混成一个单 Agent 顺序硬写
   - Phase 内可并行的模块，要优先并行委派

4. **接口验证**
   - 每个子 Agent 完成后，对照详细设计验证接口
   - Shared 模块完成后，必须立即做 JVM 编译校验

5. **集成组装**
   - 当所有模块完成后，再做跨模块集成校验

6. **任务拆解维护**
   - 先基于需求文档与详细设计，为每个模块生成最小可执行任务
   - 将每个模块任务写入 `doc/tasks/<module-name>.md`
   - 将总体模块完成情况写入 `doc/tasks/progress.md`

---

## 二、必须先接受的项目现状

这不是完全空白仓库。新的会话**不能**再按“`ios/` 和 `shared/` 都不存在”来初始化第二次。

当前仓库已经有第一轮脚手架，主 Agent 必须在这个现状上继续编排多 Agent 开发。

### 已存在的内容

1. 根 Gradle 多模块配置已接好
   - `D:\Reasonix\Reasonix_project\WaterValve\settings.gradle.kts`
   - `D:\Reasonix\Reasonix_project\WaterValve\build.gradle.kts`
   - `D:\Reasonix\Reasonix_project\WaterValve\gradle\libs.versions.toml`

2. `shared/` 已存在基础 KMP 骨架
   - `shared/build.gradle.kts`
   - `shared/src/commonMain`
   - `shared/src/iosMain`
   - `shared/src/jvmMain`
   - `shared/gradlew`
   - `shared/gradlew.bat`

3. `ios/` 已存在基础 iOS 原生工程骨架
   - `ios/WaterValve.xcodeproj`
   - `ios/WaterValve/`
   - `ios/BuildPhases/build-shared.sh`
   - `ios/WaterValve/Resources/Info.plist`

4. CI 骨架已存在
   - `.github/workflows/ios-build.yml`

5. 本地 Shared 验证脚本已存在且可用
   - `D:\Reasonix\Reasonix_project\WaterValve\build_shared.bat`

### 这意味着什么

- 不要重复创建第二套 `shared/`
- 不要重复初始化第二份 `WaterValve.xcodeproj`
- 不要删除当前脚手架后重做
- 所有子 Agent 都必须以**当前工作树**为起点开发
- 当前 `doc/tasks/*.md` 已存在初版任务文件，但主 Agent 仍要根据最新需求和设计继续校正它们

---

## 三、项目背景摘要

将 Android 版「河滴答@一键开阀器」迁移到 iOS 平台。三层结构如下：

```text
iOS Native Layer (SwiftUI)   ← 9 个模块
        │
KMP Shared Layer (Kotlin)    ← 7 个模块
        │
Flask Backend (Python)       ← 已有，不变
```

- **最低 iOS 版本：** 16.0
- **发布渠道：** AltStore / SideStore
- **CI/CD：** GitHub Actions macOS runner
- **后端：** `https://hcei.pythonanywhere.com`

### 核心功能

| # | 功能 | 描述 |
|---|------|------|
| F1 | CAS SSO 登录 | WKWebView 加载 CAS 页面 → 拦截 ticket → 交换 Token |
| F2 | QR 扫码添加设备 | AVFoundation + Vision 识别 QR → MD5 生成设备 ID |
| F3 | 多设备管理 | 列表展示/添加/重命名/星标/删除 |
| F4 | 一键开阀 | WKWebView 加载 SPA → 注入 Token → JS Bridge 通信 |
| F5 | 开阀记录 | 时间倒序列表，清除单条/全部 |
| F6 | 云端设备同步 | Ktor → Flask 全量推送/拉取 |
| F7 | 后台 Token 刷新 | BGTaskScheduler 每 12h 刷新 UWC Token |
| F8 | 用户封禁处理 | 403 → 不可关闭的封禁弹窗 |
| F9 | 应用更新检查 | GitHub Release API → 三源保底 → 引导 AltStore 更新 |

---

## 四、任务拆解规则

### 目标

主 Agent 必须先为每个模块维护一份 **Vibe Coding 用的最小可执行任务文档**。

### 输入

- `proposal`
- `REQUIREMENTS.md`
- `doc/detailed-design.md`

### 输出

1. 每个模块一份任务文件：
   - `doc/tasks/<module-name>.md`

2. 一份总体进度文件：
   - `doc/tasks/progress.md`

### 强制要求

1. 每个模块文档必须只描述该模块自己的任务
2. 子任务必须是**最小可执行单位**
3. 子任务必须用 checklist 表示
4. 每个模块文档都要能直接作为该模块子 Agent 的工作输入
5. `progress.md` 必须用 checklist 表示模块是否完成
6. 当模块设计变化、代码现状变化时，主 Agent 必须同步修正这些任务文件

### 任务拆解标准

每个 `doc/tasks/<module-name>.md` 至少应包含：

1. 模块名
2. 依赖模块
3. 对应需求点
4. 对应设计章节
5. checklist 子任务
6. 完成标准

### checklist 约束

模块任务文件中的 checklist 应满足：

- 一个勾选项只表达一个可执行动作
- 不要把多个动作塞进同一个 checkbox
- 不要写成宽泛目标，要写成可直接落代码/落配置/落文件/做验证的动作
- 优先拆成“创建文件 / 定义接口 / 实现逻辑 / 接线 / 验证”这类粒度

### `progress.md` 维护规则

`doc/tasks/progress.md` 必须至少维护两层信息：

1. 总体进度
   - Shared 已完成数
   - iOS Native 已完成数
   - CI/CD 已完成数

2. 模块 checklist
   - 每个模块一行
   - 已完成用 `[x]`
   - 未完成用 `[ ]`

主 Agent 每次开始新阶段前、每次模块完成后，都要更新 `progress.md`。

---

## 五、可验证范围

### Windows 本地可验证

- `:shared:generateCommonMainWaterValveDbInterface`
- `:shared:compileKotlinJvm`
- `:shared:compileTestKotlinJvm`
- `D:\Reasonix\Reasonix_project\WaterValve\build_shared.bat`

### 当前本机不可验证

- `compileKotlinIosArm64`
- SwiftUI / Xcode 真编译
- IPA 产物本地生成

### 本地验证命令

```bat
D:\Reasonix\Reasonix_project\WaterValve\build_shared.bat
```

或：

```bat
D:\Reasonix\Reasonix_project\WaterValve\gradlew.bat -p D:\Reasonix\Reasonix_project\WaterValve :shared:compileKotlinJvm --no-daemon
```

---

## 六、17 个模块与当前状态

### Phase 1

| # | 模块 | 当前状态 |
|---|------|------|
| M01 | shared-models | 已有基础文件，需子 Agent复核 |
| M02 | shared-crypto | 已有基础文件，需子 Agent复核 |
| M03 | shared-db | 已有基础文件，需子 Agent复核 |
| M04 | shared-platform-ios | 已有基础文件，需子 Agent复核 |

### Phase 2

| # | 模块 | 当前状态 |
|---|------|------|
| M05 | shared-api | 目录存在，核心实现未完成 |
| M06 | shared-auth-repository | 目录存在，核心实现未完成 |
| M07 | shared-device-repository | 目录存在，核心实现未完成 |

### Phase 3

| # | 模块 | 当前状态 |
|---|------|------|
| M08 | ios-core | 已有工程入口与骨架，需子 Agent复核并补全 |
| M09 | ios-webview | 仅有极简占位，未完成 |
| M17 | ci-cd | 已有基础 workflow，需子 Agent复核 |

### Phase 4

| # | 模块 | 当前状态 |
|---|------|------|
| M10 | ios-login | 未完成 |
| M11 | ios-home | 未完成 |
| M12 | ios-qr-scanner | 未完成 |
| M13 | ios-valve | 未完成 |
| M14 | ios-record | 未完成 |
| M15 | ios-background | 未完成 |
| M16 | ios-update | 未完成 |

---

## 七、强制执行流程

### 步骤 1：初始化校验

主 Agent 先做这些动作：

1. 阅读：
   - `proposal`
   - `D:\Reasonix\Reasonix_project\WaterValve\ios\REQUIREMENTS.md`
   - `D:\Reasonix\Reasonix_project\WaterValve\ios\doc\detailed-design.md`
   - `D:\Reasonix\Reasonix_project\WaterValve\ios\doc\tasks\progress.md`
   - `D:\Reasonix\Reasonix_project\WaterValve\ios\WaterValve.xcodeproj\project.pbxproj`
   - `D:\Reasonix\Reasonix_project\WaterValve\shared\build.gradle.kts`

2. 运行：
   - `D:\Reasonix\Reasonix_project\WaterValve\build_shared.bat`

3. 根据当前工作树重新标注每个模块状态：
   - `Done`
   - `Partial`
   - `Pending`

4. 复核并更新任务文档：
   - 每个模块对应一个 `doc/tasks/<module-name>.md`
   - 子任务必须是最小可执行任务
   - `doc/tasks/progress.md` 必须与当前状态一致

### 步骤 2：Phase 1 并行委派

即使已有脚手架，也要为以下模块分别建立**独立子 Agent**复核并补完：

- Agent-M01 → `shared-models`
- Agent-M02 → `shared-crypto`
- Agent-M03 → `shared-db`
- Agent-M04 → `shared-platform-ios`

要求：
- 每个子 Agent 只处理自己的模块
- 完成后主 Agent 逐个跑 Shared 编译验证
- 完成后主 Agent 更新该模块的 `doc/tasks/<module-name>.md` 与 `progress.md`

### 步骤 3：Phase 2 并行委派

- Agent-M05 → `shared-api`
- Agent-M06 → `shared-auth-repository`
- Agent-M07 → `shared-device-repository`

要求：
- 每个子 Agent 独立开发
- 主 Agent 在每个模块结束后运行 Shared 编译验证
- 主 Agent 在每个模块结束后更新任务 checklist 与总体进度

### 步骤 4：Phase 3 并行委派

- Agent-M08 → `ios-core`
- Agent-M09 → `ios-webview`
- Agent-M17 → `ci-cd`

### 步骤 5：Phase 4 并行委派

- Agent-M10 → `ios-login`
- Agent-M11 → `ios-home`
- Agent-M12 → `ios-qr-scanner`
- Agent-M13 → `ios-valve`
- Agent-M14 → `ios-record`
- Agent-M15 → `ios-background`
- Agent-M16 → `ios-update`

### 步骤 6：集成验证

所有模块都完成后，主 Agent 才能做：

1. Shared 全量编译验证
2. Swift / Xcode 工程结构核对
3. `doc/tasks/progress.md` 更新
4. TODO / 风险汇总
5. CI 构建链路核对

---

## 八、对子 Agent 的约束

每个子 Agent 都必须遵守：

1. 只修改自己负责的模块
2. 基于当前已有脚手架增量开发
3. 不得擅自重建整个工程
4. 不得覆盖其他模块的接口
5. 如果需要 Android 对照信息，优先从现有 Android 源码中读取

每个子 Agent 的输出至少包括：

1. 新增/修改文件清单
2. 对外接口签名
3. 仍未确认的 TODO
4. 若是 Shared 模块，必须说明是否通过编译校验
5. 当前模块任务文件中哪些 checklist 已完成、哪些仍未完成

---

## 九、任务文件命名映射

主 Agent 必须确保以下文件始终存在并可作为子 Agent 输入：

- `doc/tasks/shared-models.md`
- `doc/tasks/shared-crypto.md`
- `doc/tasks/shared-db.md`
- `doc/tasks/shared-platform-ios.md`
- `doc/tasks/shared-api.md`
- `doc/tasks/shared-auth-repository.md`
- `doc/tasks/shared-device-repository.md`
- `doc/tasks/ios-core.md`
- `doc/tasks/ios-webview.md`
- `doc/tasks/ios-login.md`
- `doc/tasks/ios-home.md`
- `doc/tasks/ios-qr-scanner.md`
- `doc/tasks/ios-valve.md`
- `doc/tasks/ios-record.md`
- `doc/tasks/ios-background.md`
- `doc/tasks/ios-update.md`
- `doc/tasks/ci-cd.md`
- `doc/tasks/progress.md`

---

## 十、Android 源码对照项

以下信息必须从 Android 端现有源码确认，不能瞎填：

- CAS 登录 URL
- UIS / UWC API 完整路径
- WebView token 注入方式
- JS Bridge 消息 key
- UWC SPA 路由参数格式
- GitHub Release 的真实 owner/repo

---

## 十一、禁止事项

- 禁止把多 Agent 编排改成单 Agent 全包
- 禁止再次把仓库当成全空白初始化
- 禁止删除现有 `shared/` 或 `ios/WaterValve.xcodeproj` 后重建
- 禁止跳过 Shared 模块编译验证
- 禁止在没有检查当前工作树的情况下覆盖脚手架
- 禁止跳过 `doc/tasks/<module-name>.md` 的维护
- 禁止只改代码不改 `progress.md`

---

## 十二、最终交付目标

最终目标不是“重新初始化一次工程”，而是：

1. 保持当前已存在脚手架
2. 由主 Agent 编排多个子 Agent 分模块开发
3. 补全 KMP Shared 业务层
4. 补全 SwiftUI 各功能模块
5. 让 GitHub Actions 能完成完整 iOS 构建
6. 为每个模块持续维护最小可执行任务文档
7. 将项目推进到可继续开发、可集成验证、可最终验收的状态
