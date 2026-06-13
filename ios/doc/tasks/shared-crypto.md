# shared-crypto

> Dependencies: `shared-models`
> Current status: `implemented in shared/commonMain and compile-validated on JVM plus simulated GitHub Actions iOS framework tasks`

## Tasks

- [x] Implement shared `UwcCrypto` with TripleDES, MD5, and HMAC-SHA512 helpers.
- [x] Implement request signing, `paramStr` generation, and UWC response parsing helpers.
- [x] Keep constants aligned with the Android source of truth.
- [ ] Add stronger parity tests against Android or live payload samples.

## Notes

- The active Swift iOS app still keeps its own Swift crypto path under `ios/WaterValve/Core`.
- The shared crypto implementation now avoids JVM-only helpers in `commonMain`, which was necessary for Kotlin/Native framework compilation.
- Behavioral parity is still not runtime-verified across platforms.

## Done Criteria

- [x] Shared crypto source exists and compiles on JVM.
- [x] Shared crypto source is compatible with simulated GitHub Actions iOS framework tasks.
- [ ] Cross-platform or fixture-based proof confirms behavioral parity.
