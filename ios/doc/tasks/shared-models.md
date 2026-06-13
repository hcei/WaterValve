# shared-models

> Dependencies: `shared/` module foundation
> Current status: `implemented in shared/commonMain and validated through the macOS shared framework build plus Swift import probe`

## Tasks

- [x] Define shared `Device`, `WaterRecord`, `UserInfo`, and `AppRelease` models.
- [x] Align shared URL and bridge constants with the Android source of truth.
- [x] Keep model serialization compatible with the current backend payload shape.
- [x] Verify the exported models from a real iOS framework consumer on macOS.

## Notes

- The current Swift iOS target still uses parallel Swift-native models.
- The shared model layer now exists as concrete Kotlin source and is no longer just a historical plan.

## Done Criteria

- [x] Shared model source exists under `shared/src/commonMain`.
- [x] The shared module compiles on JVM.
- [x] macOS validates iOS framework export compatibility.
