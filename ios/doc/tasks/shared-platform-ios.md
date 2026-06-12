# shared-platform-ios

> Dependencies: `shared-db`, `shared-auth-repository`
> Current status: `implemented as KMP platform wrappers; compile-validated on JVM, iOS actuals still need macOS proof`

## Tasks

- [x] Provide `KeychainWrapper` expect/actual declarations.
- [x] Provide `UserDefaultsWrapper` expect/actual declarations.
- [x] Provide platform clock expect/actual helpers for shared repositories.
- [x] Keep `DatabaseDriverFactory` in `iosMain` for SQLDelight native-driver integration.
- [ ] Verify the iOS actual implementations during a real macOS framework build.

## Notes

- The live Swift app already has Swift-native equivalents under `ios/WaterValve/Core`.
- These KMP platform wrappers now exist, but Windows cannot prove the Kotlin/Native actual implementations compile and run.

## Done Criteria

- [x] Shared platform wrapper source exists for commonMain, jvmMain, and iosMain.
- [x] JVM compilation succeeds with the expect/actual structure.
- [ ] macOS validates the iOS actual implementations.
