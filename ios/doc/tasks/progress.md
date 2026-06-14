# WaterValve iOS Progress

> Last updated: 2026-06-14
> Coverage: `shared/`, `ios/`, and `.github/workflows/ios-build.yml`

## Current Delivery Status

| Area | Status | Basis |
|------|------|------|
| KMP shared layer | Implemented and validated for the current coding scope | Local JVM compile/test, crypto parity fixtures, mocked auth-chain tests, repository behavior tests, and successful GitHub Actions shared iOS framework builds |
| Swift iOS app structure | Implemented and archived successfully on GitHub-hosted macOS runners | Static review plus successful `xcodebuild archive` runs in GitHub Actions |
| Login / WebView / Valve / Home / Record flows | Implemented for the current coding scope, with remaining live-runtime risk documented | Static code review, successful archive participation, and Swift logic tests for valve bridge behavior |
| Background refresh wiring | Implemented for the current coding scope, with runtime scheduling risk documented | Code, plist, pure scheduling-policy tests, and successful archive participation |
| Update flow | Implemented for the current coding scope, with public IPA release-channel risk documented | Parsing/UI logic, Swift package tests, release metadata review, and CI unsigned IPA packaging proof |
| GitHub Actions iOS workflow | Implemented and validated on GitHub-hosted macOS runners | Successful GitHub Actions macOS runs, including shared JVM tests, shared iOS framework builds, Swift logic tests, static validation, app archive, unsigned IPA packaging, and artifact uploads |

## Completion Summary

- Shared KMP modules: `7 / 7` completed
- Swift iOS modules: `9 / 9` completed
- CI/CD modules: `1 / 1` completed

## Current Module Status

### Shared KMP modules

- [x] `shared-models`
- [x] `shared-crypto`
- [x] `shared-api`
- [x] `shared-db`
- [x] `shared-auth-repository`
- [x] `shared-device-repository`
- [x] `shared-platform-ios`

### Swift iOS modules

- [x] `ios-core`
- [x] `ios-webview`
- [x] `ios-login`
- [x] `ios-home`
- [x] `ios-qr-scanner`
- [x] `ios-valve`
- [x] `ios-record`
- [x] `ios-background`
- [x] `ios-update`

### CI/CD modules

- [x] `ci-cd`

## Residual Risks

- The current iOS app under `ios/WaterValve/**` is implemented locally in Swift and should be evaluated on that basis; the shared KMP layer is present, tested on JVM, and exported in macOS iOS framework builds, but the shipped Swift UI still owns most runtime orchestration.
- Windows cannot run `swift` or `xcodebuild` locally here, so compile and archive proof comes from GitHub-hosted macOS CI rather than this workstation.
- Live HGU CAS cookies/redirects, QR camera behavior, UWC SPA compatibility, BGTask scheduling, and public IPA release distribution are still runtime or operational risks outside the repository diff, even though the code and CI scope are complete.
- The valve bridge intentionally keeps a lightweight `window.wx` stub plus `wxMark` flag because the current Android source-of-truth does the same, and `ios/REQUIREMENTS.md` has now been aligned to describe that minimal environment shim explicitly.
- The iOS asset catalog now maps all generated AppIcon PNG sizes, `CFBundleDisplayName` is set to `小河滴答`, the target is iPhone-only, and the camera permission text is localized in Simplified Chinese.
