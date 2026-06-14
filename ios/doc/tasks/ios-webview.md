# ios-webview

> Dependencies: none beyond iOS system frameworks
> Related requirements: `F1`, `F4`
> Current status: `implemented and archive-validated on macOS CI; live page behavior remains a documented runtime risk`

## Tasks

- [x] Provide a reusable `WebViewConfig`.
- [x] Provide a reusable `UIViewRepresentable` wrapper around `WKWebView`.
- [x] Share a single `WKProcessPool` across web views.
- [x] Support custom user agents.
- [x] Support page-finished callbacks.
- [x] Support URL-change interception for CAS ticket handling and H5 callback URLs.
- [x] Support JavaScript injection after page load.
- [x] Support optional WebKit script-message handling for structured JS bridge events.
- [x] Validate that the reusable wrapper participates in successful macOS archive builds and document that live page behavior still needs downstream runtime confirmation.

## Done Criteria

- [x] Login and valve pages can use the same reusable WebView infrastructure.
- [x] The wrapper supports both navigation interception and message-handler style bridging.
- [x] The current coding scope is complete, and the remaining live-page runtime risk is documented.
