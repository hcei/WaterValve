# shared-crypto

> Dependencies: `shared-models`
> Current status: `implemented in shared/commonMain and compile-validated on JVM`

## Tasks

- [x] Implement shared `UwcCrypto` with TripleDES, MD5, and HMAC-SHA512 helpers.
- [x] Implement request signing, `paramStr` generation, and UWC response parsing helpers.
- [x] Keep constants aligned with the Android source of truth.
- [ ] Add stronger parity tests against Android or live payload samples.

## Notes

- The active Swift iOS app still keeps its own Swift crypto path under `ios/WaterValve/Core`.
- The shared crypto implementation now exists, but parity is only compile-verified here, not runtime-verified across platforms.

## Done Criteria

- [x] Shared crypto source exists and compiles on JVM.
- [ ] Cross-platform or fixture-based proof confirms behavioral parity.
