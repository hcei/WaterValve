# ios-login

> Dependencies: `ios-core`, `ios-webview`
> Related requirement: `F1`
> Current status: `implemented and archive-validated on macOS CI; live HGU login behavior remains a documented runtime risk`

## Tasks

- [x] Build `AuthViewModel`.
- [x] Start CAS login from native code and pass the configured URL and user agent into the WebView wrapper.
- [x] Intercept CAS callback URLs and extract `ticket=ST-...`.
- [x] Exchange the CAS ticket in native networking code instead of delegating auth to the page.
- [x] Reflect login progress in the view model and expose a third-stage UWC token fetch message.
- [x] Push successful login state back into `AppContainer` and `AppState`.
- [x] Surface login failures to the UI with a retry path.
- [x] Validate that the login module participates in successful macOS archive builds and document the remaining live HGU login risk separately.

## Notes

- Static review indicates the login flow is native-network driven after ticket interception.
- Cookie, redirect, and UA behavior still need verification on macOS/iOS because they cannot be exercised from this Windows environment.

## Done Criteria

- [x] Login is no longer a static placeholder screen.
- [x] Ticket interception and native token exchange are wired together.
- [x] The progress UI can represent page load, ticket verification, and UWC token fetch.
- [x] The current coding scope is complete, and the remaining live-service runtime risk is documented.
