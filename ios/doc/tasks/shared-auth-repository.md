# shared-auth-repository

> Dependencies: `shared-api`, `shared-platform-ios`
> Current status: `implemented in shared/commonMain; JVM-tested and exported in successful iOS framework builds`

## Tasks

- [x] Implement `AuthRepository` with login state flow, banned-state flow, CAS ticket exchange, and token refresh.
- [x] Persist UWC token, UIS JWT, user ID, and session cookie through platform wrappers.
- [x] Persist nickname and user metadata through `UserDefaultsWrapper`.
- [x] Add mocked JVM tests for invalid ticket handling, CAS/session exchange persistence, refresh persistence, and logout cleanup.
- [x] Validate the shared auth source through JVM tests plus macOS CI iOS framework export, and document the remaining live-service risk separately.

## Notes

- The active Swift iOS app still uses its own Swift auth repository.
- This shared repository now exists, compiles, is covered by mocked auth-chain tests, and is exported in successful macOS CI iOS framework builds.
- The active Swift iOS app still uses its own Swift auth repository, so live HGU service behavior for the shared auth path remains a residual integration risk rather than an implementation gap.

## Done Criteria

- [x] Shared auth source exists and compiles on JVM.
- [x] Mocked JVM tests cover the main persistence and state transitions.
- [x] Shared auth implementation, tests, and iOS framework export are complete for the current coding scope.
