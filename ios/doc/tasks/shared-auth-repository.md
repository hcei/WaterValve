# shared-auth-repository

> Dependencies: `shared-api`, `shared-platform-ios`
> Current status: `implemented in shared/commonMain; compile-validated on JVM`

## Tasks

- [x] Implement `AuthRepository` with login state flow, banned-state flow, CAS ticket exchange, and token refresh.
- [x] Persist UWC token, UIS JWT, user ID, and session cookie through platform wrappers.
- [x] Persist nickname and user metadata through `UserDefaultsWrapper`.
- [ ] Validate the shared auth chain against live services from an iOS framework consumer.

## Notes

- The active Swift iOS app still uses its own Swift auth repository.
- This shared repository now exists and compiles, but end-to-end runtime proof still needs macOS/iPhone execution.

## Done Criteria

- [x] Shared auth source exists and compiles on JVM.
- [ ] Real runtime validation proves the shared auth path works end to end.
