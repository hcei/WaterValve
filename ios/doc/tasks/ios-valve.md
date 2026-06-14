# ios-valve

> Dependencies: `ios-core`, `ios-webview`
> Related requirement: `F4`
> Current status: `implemented with Swift logic coverage and archive validation; live SPA compatibility remains a documented runtime risk`

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
- [x] Keep the lightweight `window.wx` stub and `wxMark` injection aligned with the current Android source-of-truth, and document the spec-vs-implementation contradiction explicitly.
- [x] Validate that the valve module participates in successful macOS archive builds plus Swift logic tests, and document the remaining live SPA/runtime risk separately.

## Done Criteria

- [x] The valve module no longer behaves like a placeholder-only page.
- [x] Native scan requests can flow from the page into the iOS scanner and back into JavaScript.
- [x] Structured bridge messages can be received through WebKit script messages.
- [x] The bridge contract now has dedicated Swift logic coverage for URL, injection, parsing, and callback/event shaping rules.
- [x] The current coding scope is complete, and the remaining live SPA/runtime risk is documented.
