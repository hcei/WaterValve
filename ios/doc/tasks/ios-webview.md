# ios-webview

> Dependencies: none beyond iOS system frameworks
> Related requirements: `F1`, `F4`
> Current status: `implemented, not runtime-verified`

## Tasks

- [x] Provide a reusable `WebViewConfig`.
- [x] Provide a reusable `UIViewRepresentable` wrapper around `WKWebView`.
- [x] Share a single `WKProcessPool` across web views.
- [x] Support custom user agents.
- [x] Support page-finished callbacks.
- [x] Support URL-change interception for CAS ticket handling and H5 callback URLs.
- [x] Support JavaScript injection after page load.
- [x] Support optional WebKit script-message handling for structured JS bridge events.
- [ ] Verify the combined login and valve flows on a real iOS runtime.

## Done Criteria

- [x] Login and valve pages can use the same reusable WebView infrastructure.
- [x] The wrapper supports both navigation interception and message-handler style bridging.
- [ ] Real-device verification confirms the live pages behave correctly with the current wrapper.
