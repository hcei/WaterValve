# ci-cd

> Dependencies: `.github/workflows/ios-build.yml`, `ios/BuildPhases/validate-ios-static.ps1`, `ios/BuildPhases/build-shared.sh`, `ios/WaterValve.xcodeproj`
> Current status: `workflow aligned with shared + Swift architecture; real macOS runs now succeed and publish archive + unsigned IPA artifacts`

## Tasks

- [x] Validate `shared` on JVM before iOS archive work.
- [x] Keep the shared JVM validation strong enough to cover both compile and test execution locally.
- [x] Build the shared iOS framework from the workflow.
- [x] Run iOS static validation as part of GitHub Actions.
- [x] Run lightweight Swift package tests for iOS update/background logic on macOS.
- [x] Keep the validation and shared-build scripts path-agnostic enough for macOS runners instead of depending on local Windows paths or executable bits.
- [x] Resolve Gradle plugins from official repositories first on GitHub Actions to avoid stale mirror metadata.
- [x] Keep the shared build script and Xcode configuration aligned for both Debug and Release framework output directories.
- [x] Keep the shared Xcode scheme pointing at the native target used by `xcodebuild archive`.
- [x] Pin Xcode selection explicitly in the workflow instead of relying on runner defaults.
- [x] Archive the Swift iOS target from `ios/WaterValve.xcodeproj`.
- [x] Package an unsigned `.ipa` from the archived `.app` for AltStore / SideStore delivery.
- [x] Upload archive, shared-framework, and static-validation artifacts.
- [x] Verify at least one real macOS GitHub Actions run succeeds end to end.
- [x] Keep CI release artifacts aligned with the documented unsigned-IPA distribution flow.

## Notes

- The repository currently contains both a concrete KMP shared layer and a Swift-native iOS target.
- The workflow now reflects that hybrid reality: shared validation/build first, then Xcode archive.
- The workflow disables signing for archive work, publishes both Debug and Release shared framework artifacts, runs a lightweight Swift package test suite for iOS business logic, and packages an unsigned IPA from the archived app bundle for AltStore / SideStore installs.
- Earlier PR runs exposed `./gradlew: Permission denied`, stale mirror metadata for `com.google.devtools.ksp`, missing SQLite linkage during archive, and Xcode 26 / Kotlin/Native CommonCrypto cinterop failures.
- The workflow and `build-shared.sh` now both invoke the Gradle wrapper through `bash`, CI skips China mirrors so official plugin repositories resolve first, the Xcode target links `libsqlite3`, and shared iOS crypto no longer depends on CommonCrypto cinterop.
- GitHub Actions run `27470917081` succeeded end to end on macOS, including shared JVM tests, shared iOS framework builds, Swift package logic tests, static validation, Xcode archive, unsigned IPA packaging, and artifact uploads.
- Windows cannot run `xcodebuild`; local verification remains limited to `build_shared.bat` and static validation, while macOS proof comes from GitHub Actions.

## Done Criteria

- [x] CI reflects the actual repository architecture rather than an ios-only assumption.
- [x] CI validates shared JVM compilation, executes shared JVM tests, and attempts shared iOS framework build.
- [x] CI executes Swift package tests that cover the iOS update decision and background scheduling rules.
- [x] Local Windows verification covers `build_shared.bat` and `:shared:jvmTest`.
- [x] CI archives the Swift iOS target, packages an unsigned IPA, and publishes the release artifacts needed by the documented iOS distribution flow.
- [x] A real GitHub Actions run confirms the workflow works on macOS.
