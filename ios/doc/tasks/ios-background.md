# ios-background

> Dependencies: `ios-core`
> Related requirement: `F7`
> Current status: `implemented in code and plist; runtime unverified`

## Tasks

- [x] Create `BackgroundTaskManager`.
- [x] Register the background refresh task using `com.hgu.watervalve.tokenRefresh`.
- [x] Schedule the next refresh about 12 hours later with `BGAppRefreshTaskRequest`.
- [x] Call `refreshUwcToken()` from the background task handler.
- [x] Reschedule the next refresh from the handler.
- [x] Add a simple re-entry guard so concurrent refresh handlers do not overlap.
- [x] Register the task during app launch preparation instead of waiting for deeper UI navigation.
- [x] Only schedule background refresh work when an authenticated session exists.
- [x] Cancel pending background refresh requests on logout so stale token jobs do not linger.
- [x] Add `BGTaskSchedulerPermittedIdentifiers` to `Info.plist`.
- [x] Add `UIBackgroundModes` entries needed for refresh and processing.
- [ ] Verify actual task scheduling and execution in Xcode or on a real iPhone.

## Notes

- This item is complete only at the static code/config level.
- Background execution behavior cannot be confirmed from Windows; it still requires Xcode diagnostics or device testing on macOS.

## Done Criteria

- [x] The iOS app has a concrete background refresh path in code and plist configuration.
- [x] The handler can refresh tokens and mark completion.
- [ ] A real iOS runtime confirms the system accepts the registration and launches the refresh task.
