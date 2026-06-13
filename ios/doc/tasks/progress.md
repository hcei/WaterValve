# WaterValve iOS Progress

> Last updated: 2026-06-13
> Coverage: `shared/`, `ios/`, and `.github/workflows/ios-build.yml`

## Current Delivery Status

| Area | Status | Basis |
|------|------|------|
| KMP shared layer | Implemented and validated in GitHub Actions, including shared iOS framework builds on macOS | Source review plus local JVM compile/test, including `:shared:jvmTest`, and successful GitHub Actions shared iOS framework + archive execution |
| Swift iOS app structure | Implemented with minimal shared-framework consumption probe | Static file/project review plus Xcode wiring checks |
| Login / WebView / Valve / Home / Record flows | Implemented | Static code review only |
| Background refresh wiring | Implemented, unverified | Code and plist present; no runtime proof |
| Update flow | Implemented in app logic; CI now produces unsigned IPA artifacts, but public release-channel adoption still remains unverified | Parsing/UI logic, live release metadata review, and CI artifact packaging proof; public tagged releases still need an actual iOS asset publish cycle |
| GitHub Actions iOS workflow | Implemented and validated on GitHub-hosted macOS runners | Successful GitHub Actions run `27464132330`, including shared validation, shared iOS framework builds, static validation, and app archive |

## Completion Summary

- Shared KMP modules: `7 / 7` completed
- Swift iOS modules: `7 / 9` completed
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
- [ ] `ios-background`
- [ ] `ios-update`

### CI/CD modules

- [x] `ci-cd`

## Notes

- The current iOS app under `ios/WaterValve/**` is implemented locally in Swift and should be evaluated on that basis.
- The repository also now contains a concrete `shared/` KMP layer that compiles and tests on JVM, passes local `GITHUB_ACTIONS=true` iOS framework task simulation, and is included in CI validation.
- The Swift iOS target now has a minimal `import Shared` probe plus framework search/link settings, but the app's runtime business logic is still primarily implemented in Swift.
- `ios/WaterValve.xcodeproj` now keeps `ios/BuildPhases/build-shared.sh` as an active shell phase, aligns its shared scheme to the native target, uses framework paths that match actual KMP output directories, and links `-lsqlite3` so SQLDelight symbols resolve during archive.
- Real GitHub Actions PR runs exposed a macOS `./gradlew` permission failure, then a KSP plugin mirror-resolution failure, and finally a missing SQLite linkage during archive; the workflow/shared build script now invoke Gradle through `bash`, CI skips China mirrors for official plugin resolution, the Xcode target links `libsqlite3`, and the static validator covers these guards.
- `ios-background` remains unchecked because it still needs runtime validation outside this Windows environment.
- `ios-update` remains unchecked because the client logic and CI packaging are in place, but the public release channel still needs a real tagged IPA publish/consume cycle.
- Windows cannot run `swift` or `xcodebuild` here, so all compile, archive, background-task, and live WebView conclusions remain limited to static review unless confirmed on macOS.
