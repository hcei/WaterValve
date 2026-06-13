# shared-crypto

> Dependencies: `shared-models`
> Current status: `implemented with platform crypto primitives and validated against captured UWC payload fixtures`

## Tasks

- [x] Implement shared `UwcCrypto` with TripleDES, MD5, and HMAC-SHA512 helpers.
- [x] Implement request signing, `paramStr` generation, and UWC response parsing helpers.
- [x] Keep constants aligned with the Android source of truth.
- [x] Add stronger parity tests against Android or live payload samples.

## Notes

- The active Swift iOS app still keeps its own Swift crypto path under `ios/WaterValve/Core`.
- The shared crypto implementation now delegates 3DES, MD5, and HMAC-SHA512 to platform primitives while keeping Base64, JSON wrapping, and response parsing in common code.
- `UwcCryptoParityTest` validates the shared implementation against the Android known sign value plus captured `loginByToken`, `queryCustom`, `getSysInfo`, and request `paramStr` payloads.

## Done Criteria

- [x] Shared crypto source exists and compiles on JVM.
- [x] Shared crypto source is compatible with simulated GitHub Actions iOS framework tasks.
- [x] Cross-platform or fixture-based proof confirms behavioral parity.
