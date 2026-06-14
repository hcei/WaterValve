# shared-crypto

> Dependencies: `shared-models`
> Current status: `implemented with JVM provider and pure-Kotlin iOS primitives, validated against captured UWC payload fixtures`

## Tasks

- [x] Implement shared `UwcCrypto` with TripleDES, MD5, and HMAC-SHA512 helpers.
- [x] Implement request signing, `paramStr` generation, and UWC response parsing helpers.
- [x] Keep constants aligned with the Android source of truth.
- [x] Add stronger parity tests against Android or live payload samples.

## Notes

- The active Swift iOS app still keeps its own Swift crypto path under `ios/WaterValve/Core`.
- The shared crypto implementation delegates JVM execution to JCE and iOS execution to pure Kotlin 3DES/MD5/HMAC-SHA512 primitives, avoiding Xcode 26 / Kotlin/Native CommonCrypto cinterop incompatibilities.
- `UwcCryptoParityTest` validates the shared implementation against the Android known sign value plus captured `loginByToken`, `queryCustom`, `getSysInfo`, and request `paramStr` payloads.
- `PureCryptoPrimitivesTest` validates the pure Kotlin primitives byte-for-byte against JVM provider output for TripleDES-CBC-PKCS7, MD5, and HMAC-SHA512.

## Done Criteria

- [x] Shared crypto source exists and compiles on JVM.
- [x] Shared crypto source is compatible with real GitHub Actions iOS framework tasks.
- [x] Cross-platform or fixture-based proof confirms behavioral parity.
