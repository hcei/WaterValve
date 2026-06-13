# WaterValve iOS Progress

> Last updated: 2026-06-14
> Coverage: `shared/`, `ios/`, and `.github/workflows/ios-build.yml`

## Current Delivery Status

| Area | Status | Basis |
|------|------|------|
| KMP shared layer | Implemented broadly and framework-validated on macOS, but shared repository modules still lack live-runtime proof | Source review plus local JVM compile/test, captured crypto fixture tests, mocked auth-chain tests, repository behavior tests for shared device sync paths, and successful GitHub Actions shared iOS framework builds |
| Swift iOS app structure | Implemented and archived successfully on GitHub-hosted macOS runners | Static review plus successful `xcodebuild archive` runs in GitHub Actions |
| Login / WebView / Valve / Home / Record flows | Implemented, runtime unverified | Static code review plus Swift logic tests for valve bridge behavior; no live iPhone/session proof yet |
| Background refresh wiring | Implemented with logic-level tests; runtime unverified | Code, plist, and pure scheduling-policy tests exist; no real BGTask runtime proof yet |
| Update flow | Implemented in app logic with automated decision tests; CI now produces unsigned IPA artifacts, but public release-channel adoption still remains unverified | Parsing/UI logic, Swift package tests, live release metadata review, and CI artifact packaging proof; public tagged releases still need an actual iOS asset publish cycle |
| GitHub Actions iOS workflow | Implemented and validated on GitHub-hosted macOS runners | Successful GitHub Actions run `27470917081`, including shared JVM tests, shared iOS framework builds, Swift logic tests, static validation, app archive, unsigned IPA packaging, and artifact uploads |

## Completion Summary

- Shared KMP modules: `5 / 7` completed
- Swift iOS modules: `1 / 9` completed
- CI/CD modules: `1 / 1` completed

## Current Module Status

### Shared KMP modules

- [x] `shared-models`
- [x] `shared-crypto`
- [x] `shared-api`
- [x] `shared-db`
- [ ] `shared-auth-repository`
- [ ] `shared-device-repository`
- [x] `shared-platform-ios`

### Swift iOS modules

- [x] `ios-core`
- [ ] `ios-webview`
- [ ] `ios-login`
- [ ] `ios-home`
- [ ] `ios-qr-scanner`
- [ ] `ios-valve`
- [ ] `ios-record`
- [ ] `ios-background`
- [ ] `ios-update`

### CI/CD modules

- [x] `ci-cd`

## Notes

- The current iOS app under `ios/WaterValve/**` is implemented locally in Swift and should be evaluated on that basis.
- The repository now contains a concrete `shared/` KMP layer that compiles/tests on JVM and builds successfully as an iOS framework on GitHub-hosted macOS runners; `shared-crypto` now has captured payload parity tests plus pure-Kotlin primitive parity checks, `shared-device-repository` now has automated CRUD/sync/banned behavior tests on JVM, and `shared-auth-repository` plus both repositories still retain unmet live-runtime acceptance checks.
- The Swift iOS target now has a minimal `import Shared` probe plus framework search/link settings, but the app's runtime business logic is still primarily implemented in Swift.
- `ios-valve` now has dedicated Swift package tests covering bridge URL generation, token injection keys, `h5call` payload parsing, script-message parsing, and scan callback/event payload shaping; live SPA compatibility still remains unproven without a real iOS runtime.
- `ios/WaterValve.xcodeproj` now keeps `ios/BuildPhases/build-shared.sh` as an active shell phase, aligns its shared scheme to the native target, uses framework paths that match actual KMP output directories, and links `-lsqlite3` so SQLDelight symbols resolve during archive.
- Real GitHub Actions PR runs exposed a macOS `./gradlew` permission failure, a KSP plugin mirror-resolution failure, missing SQLite linkage during archive, and an Xcode 26 / Kotlin/Native CommonCrypto cinterop incompatibility; the workflow/shared build script now invoke Gradle through `bash`, CI skips China mirrors for official plugin resolution, the Xcode target links `libsqlite3`, shared iOS crypto avoids cinterop, and the static/JVM validators cover these guards.
- Most Swift feature modules remain unchecked because their task files still require real iPhone or live-service verification for login cookies, QR camera behavior, WebView bridge compatibility, live valve routing, and record timing.
- `ios-background` remains unchecked because it still needs runtime validation outside this Windows environment.
- `ios-update` remains unchecked because the client logic and CI packaging are in place, but the public release channel still needs a real tagged IPA publish/consume cycle.
- Windows cannot run `swift` or `xcodebuild` here, so all compile, archive, background-task, and live WebView conclusions remain limited to static review unless confirmed on macOS.
