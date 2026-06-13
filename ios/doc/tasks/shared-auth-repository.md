# shared-auth-repository

> Dependencies: `shared-api`, `shared-platform-ios`
> Current status: `implemented in shared/commonMain; compile-validated on JVM with mocked auth-chain coverage`

## Tasks

- [x] Implement `AuthRepository` with login state flow, banned-state flow, CAS ticket exchange, and token refresh.
- [x] Persist UWC token, UIS JWT, user ID, and session cookie through platform wrappers.
- [x] Persist nickname and user metadata through `UserDefaultsWrapper`.
- [x] Add mocked JVM tests for invalid ticket handling, CAS/session exchange persistence, refresh persistence, and logout cleanup.
- [ ] Validate the shared auth chain against live services from an iOS framework consumer.

## Notes

- The active Swift iOS app still uses its own Swift auth repository.
- This shared repository now exists, compiles, and has mocked auth-chain coverage, but end-to-end live runtime proof still needs macOS/iPhone execution.

## Done Criteria

- [x] Shared auth source exists and compiles on JVM.
- [x] Mocked JVM tests cover the main persistence and state transitions.
- [ ] Real runtime validation proves the shared auth path works end to end.
