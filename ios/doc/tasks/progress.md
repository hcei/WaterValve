# WaterValve iOS Progress

> Last updated: 2026-06-13
> Coverage: `shared/`, `ios/`, and `.github/workflows/ios-build.yml`

## Current Delivery Status

| Area | Status | Basis |
|------|------|------|
| KMP shared layer | Implemented for JVM validation; iOS framework output wired into Xcode/CI but still unverified on macOS | Source review plus local JVM compile/test, including `:shared:jvmTest` |
| Swift iOS app structure | Implemented with minimal shared-framework consumption probe | Static file/project review plus Xcode wiring checks |
| Login / WebView / Valve / Home / Record flows | Implemented | Static code review only |
| Background refresh wiring | Implemented, unverified | Code and plist present; no runtime proof |
| Update flow | Implemented; Android-only release mismatch mitigated, final iOS release path still unverified | Parsing/UI logic plus live release metadata review; distribution still needs confirmation |
| GitHub Actions iOS workflow | Aligned to shared + Swift architecture; rerun pending | Workflow plus static validation updated after real macOS permission and KSP mirror-resolution failures |

## Completion Summary

- Shared KMP modules: `7 / 7` completed
- Swift iOS modules: `7 / 9` completed
- CI/CD modules: `0 / 1` completed

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
- [ ] `ios-background`
- [ ] `ios-update`

### CI/CD modules

- [ ] `ci-cd`

## Notes

- The current iOS app under `ios/WaterValve/**` is implemented locally in Swift and should be evaluated on that basis.
- The repository also now contains a concrete `shared/` KMP layer that compiles and tests on JVM and is included in CI validation.
- The Swift iOS target now has a minimal `import Shared` probe plus framework search/link settings, but the app's runtime business logic is still primarily implemented in Swift.
- `ios/WaterValve.xcodeproj` now keeps `ios/BuildPhases/build-shared.sh` as an active shell phase, aligns its shared scheme to the native target, and uses framework paths that match actual KMP output directories.
- Real GitHub Actions PR runs exposed a macOS `./gradlew` permission failure and then a KSP plugin mirror-resolution failure; the workflow/shared build script now invoke Gradle through `bash`, CI skips China mirrors for official plugin resolution, and the static validator covers these guards.
- `ios-background`, `ios-update`, and `ci-cd` remain unchecked because each still needs validation outside this Windows environment.
- Windows cannot run `swift` or `xcodebuild` here, so all compile, archive, background-task, and live WebView conclusions remain limited to static review unless confirmed on macOS.
