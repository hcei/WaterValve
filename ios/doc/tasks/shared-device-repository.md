# shared-device-repository

> Dependencies: `shared-api`, `shared-auth-repository`, `shared-db`
> Current status: `implemented in shared/commonMain; JVM-tested for CRUD/sync/banned paths and exported in successful iOS framework builds`

## Tasks

- [x] Implement `DeviceRepository` for device CRUD, record CRUD, and cloud sync.
- [x] Map SQLDelight rows to shared domain models.
- [x] Handle 403 sync failures by surfacing `BannedException` and marking the auth state as banned.
- [x] Add JVM repository tests that cover local CRUD, record persistence, cloud pull/push, and 403 banned handling.
- [x] Validate shared repository behavior through JVM tests plus macOS CI iOS framework export, and document the remaining live-backend runtime risk separately.

## Notes

- The active Swift iOS target still has parallel Swift-side device and record flows.
- The shared repository now reflects the same backend and storage contract, and the Swift app already consumes it indirectly through `IosSharedBridge`, but the main Swift screens are not yet driven by a fully shared-native state model.
- `DeviceRepositoryTest` now exercises local device CRUD, record CRUD, cloud push/pull payload flow, and 403 banned propagation on JVM using SQLDelight plus Ktor mock APIs.
- Live backend behavior from a fully shared-native iOS UI path is still a residual runtime risk, not a missing code module.

## Done Criteria

- [x] Shared device repository source exists and compiles on JVM.
- [x] Automated JVM tests prove the main local and sync behavior.
- [x] Shared device implementation, automated tests, and iOS framework export are complete for the current coding scope.
