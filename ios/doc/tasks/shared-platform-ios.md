# shared-platform-ios

> Dependencies: `shared-db`, `shared-auth-repository`
> Current status: `implemented as KMP platform wrappers; validated by JVM checks and real macOS shared framework builds`

## Tasks

- [x] Provide `KeychainWrapper` expect/actual declarations.
- [x] Provide `UserDefaultsWrapper` expect/actual declarations.
- [x] Provide platform clock expect/actual helpers for shared repositories.
- [x] Keep `DatabaseDriverFactory` in `iosMain` for SQLDelight native-driver integration.
- [x] Verify the iOS actual implementations against simulated GitHub Actions iOS framework tasks.
- [x] Verify the iOS actual implementations during a real macOS framework build.

## Notes

- The live Swift app already has Swift-native equivalents under `ios/WaterValve/Core`.
- These KMP platform wrappers now compile under local `GITHUB_ACTIONS=true` iOS framework task simulation after Kotlin/Native interop fixes.
- Real GitHub Actions macOS runs now prove the `iosMain` actual implementations survive full shared framework builds.

## Done Criteria

- [x] Shared platform wrapper source exists for commonMain, jvmMain, and iosMain.
- [x] JVM compilation succeeds with the expect/actual structure.
- [x] Local CI-style iOS framework tasks compile with the iosMain actual implementations present.
- [x] macOS validates the iOS actual implementations.
