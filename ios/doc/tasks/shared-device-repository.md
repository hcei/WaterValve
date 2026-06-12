# shared-device-repository

> Dependencies: `shared-api`, `shared-auth-repository`, `shared-db`
> Current status: `implemented in shared/commonMain; compile-validated on JVM`

## Tasks

- [x] Implement `DeviceRepository` for device CRUD, record CRUD, and cloud sync.
- [x] Map SQLDelight rows to shared domain models.
- [x] Handle 403 sync failures by surfacing `BannedException` and marking the auth state as banned.
- [ ] Validate shared repository behavior from a real iOS framework consumer.

## Notes

- The active Swift iOS target still has parallel Swift-side device and record flows.
- The shared repository now reflects the same backend and storage contract, but it is not yet wired into the iOS target.

## Done Criteria

- [x] Shared device repository source exists and compiles on JVM.
- [ ] Runtime validation proves the shared path works against the live backend.
