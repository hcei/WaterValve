# ci-cd

> Dependencies: `.github/workflows/ios-build.yml`, `ios/BuildPhases/validate-ios-static.ps1`, `ios/BuildPhases/build-shared.sh`, `ios/WaterValve.xcodeproj`
> Current status: `workflow aligned with shared + Swift architecture; macOS runtime still unverified`

## Tasks

- [x] Validate `shared` on JVM before iOS archive work.
- [x] Keep the shared JVM validation strong enough to cover both compile and test execution locally.
- [x] Build the shared iOS framework from the workflow.
- [x] Run iOS static validation as part of GitHub Actions.
- [x] Keep the validation and shared-build scripts path-agnostic enough for macOS runners instead of depending on local Windows paths or executable bits.
- [x] Keep the shared build script and Xcode configuration aligned for both Debug and Release framework output directories.
- [x] Keep the shared Xcode scheme pointing at the native target used by `xcodebuild archive`.
- [x] Pin Xcode selection explicitly in the workflow instead of relying on runner defaults.
- [x] Archive the Swift iOS target from `ios/WaterValve.xcodeproj`.
- [x] Upload archive, shared-framework, and static-validation artifacts.
- [ ] Verify at least one real macOS GitHub Actions run succeeds end to end.
- [ ] Decide whether unsigned `.xcarchive` is the intended long-term release artifact, or whether IPA/export signing should be added later.

## Notes

- The repository currently contains both a concrete KMP shared layer and a Swift-native iOS target.
- The workflow now reflects that hybrid reality: shared validation/build first, then Xcode archive.
- The workflow disables signing for archive work and now publishes both Debug and Release shared framework artifacts.
- As of 2026-06-13, the public GitHub workflows API for `hcei/WaterValve` still returns `0` visible workflows, so there is no externally verifiable macOS run record yet.
- Windows cannot run `xcodebuild`; final proof still requires GitHub Actions or a local macOS/Xcode machine.

## Done Criteria

- [x] CI reflects the actual repository architecture rather than an ios-only assumption.
- [x] CI validates shared JVM compilation, executes shared JVM tests, and attempts shared iOS framework build.
- [x] Local Windows verification covers `build_shared.bat` and `:shared:jvmTest`.
- [x] CI archives the Swift iOS target and publishes artifacts.
- [ ] A real GitHub Actions run confirms the workflow works on macOS.
