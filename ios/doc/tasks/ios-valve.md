# ios-valve

> Dependencies: `ios-core`, `ios-webview`
> Related requirement: `F4`
> Current status: `implemented, not runtime-verified`

## Tasks

- [x] Confirm the iOS valve page uses the same known CAS/UWC host and H5 call scheme as the Android implementation.
- [x] Build a native `ValveViewModel` that prepares the valve page URL and token injection script.
- [x] Inject the known storage keys required by the SPA, including `uwcToken`, `uisToken`, `uiastoken`, `uwcAccNum`, `uwcEpid`, `uwcUserId`, `uwcPerCode`, `wxMark`, and `isSdk`.
- [x] Expose a lightweight `window.__valveBridge` object for pages that read token context from JS globals.
- [x] Intercept `com.hzsun.h5call://` navigation requests from the page.
- [x] Handle native `openScan` requests by presenting the iOS QR scanner.
- [x] Handle native `closeWin` requests by dismissing the valve page.
- [x] Return scan results to the page through both a custom event and a best-effort callback invocation when `paramjson.callback` is present.
- [x] Accept `WKScriptMessageHandler` messages under `valveBridge` so the page can report structured events without relying only on custom URL navigation.
- [x] Record valve actions into the local record store when the page reports a valve-open event.
- [x] Add automated Swift logic tests for valve URL building, token injection, H5 payload parsing, script-message parsing, and scan callback/event payload shaping.
- [ ] Verify the real SPA expects the current callback and event shape on a simulator or physical iPhone.
- [ ] Verify the final valve route format expected by the deployed SPA if it needs something stricter than the current `#/openValve?deviceId=...` fallback.

## Done Criteria

- [x] The valve module no longer behaves like a placeholder-only page.
- [x] Native scan requests can flow from the page into the iOS scanner and back into JavaScript.
- [x] Structured bridge messages can be received through WebKit script messages.
- [x] The bridge contract now has dedicated Swift logic coverage for URL, injection, parsing, and callback/event shaping rules.
- [ ] A real device run confirms the live SPA accepts the bridge contract without additional Android-parity fixes.
