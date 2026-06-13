# ios-core

> Dependencies: current Swift files under `ios/WaterValve/Core`, `ios/WaterValve/Navigation`, `ios/WaterValve/Resources`
> Related requirements: `F8`
> Current status: `implemented in Swift; archived successfully on GitHub-hosted macOS runners`

## Tasks

- [x] Maintain the Xcode project and root SwiftUI app entry under `ios/`.
- [x] Provide a concrete `AppState` object for login state, banned state, devices, records, and update overlay state.
- [x] Provide a concrete `AppContainer` that owns the current Swift repositories and services.
- [x] Route root navigation between login, home, valve, record, and banned views.
- [x] Surface the update alert from the root navigation overlay.
- [x] Configure camera usage and BG task identifiers in `Info.plist`.
- [x] Keep the Xcode target's `Build Shared Framework` shell phase aligned with `ios/BuildPhases/build-shared.sh`.
- [x] Add a minimal Swift import probe so the Xcode target proves `Shared.framework` can be consumed during real builds.
- [x] Keep Xcode scheme/target wiring aligned with the native app target identifier.
- [x] Replace the old abrupt banned-state exit path with support-contact actions.
- [x] Verify the current Xcode target builds successfully on macOS.

## Notes

- Earlier task wording assumed the iOS app would compile against a linked KMP framework from `shared/`.
- That is not the current implementation. The present iOS target is a standalone Swift app that mirrors needed behavior locally.
- The repository still has a `shared/` module for the broader project. The current iOS target now contains a minimal `import Shared` probe and framework search/link settings, but full runtime use of shared business logic is still not wired through Swift screens.

## Done Criteria

- [x] The root iOS app structure is present and internally wired.
- [x] The Xcode project keeps the current shared build shell phase wired to the repository script.
- [x] The Xcode target contains at least one concrete `Shared.framework` consumption point and matching project wiring.
- [x] A real Xcode build confirms the target settings and file graph are valid end to end.
