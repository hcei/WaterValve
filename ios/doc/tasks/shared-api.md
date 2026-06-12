# shared-api

> Dependencies: `shared-models`, `shared-crypto`
> Current status: `implemented in shared/commonMain; compile-validated on JVM, iOS framework build still needs macOS proof`

## Tasks

- [x] Implement `UwcApi` for CAS ticket exchange, UIS token fetch, `loginByToken`, `queryCustom`, and `getSysInfo`.
- [x] Implement `SyncApi` for `/api/devices/{userId}` pull and push.
- [x] Implement `ReleaseApi` for GitHub, Gitee, and PythonAnywhere latest-release lookups.
- [x] Align URL paths and bridge-related constants with the Android source of truth.
- [ ] Build and verify the shared iOS framework on macOS.

## Notes

- The current Swift iOS target still uses local Swift networking code.
- This KMP API layer now exists as a real shared implementation, but it is not yet wired into `ios/WaterValve.xcodeproj`.

## Done Criteria

- [x] Shared API source files exist under `shared/src/commonMain`.
- [x] The shared module compiles on JVM.
- [ ] macOS validates the iOS framework outputs and endpoint behavior.
