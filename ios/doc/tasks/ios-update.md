# ios-update

> Dependencies: `ios-core`
> Related requirement: `F9`
> Current status: `implemented in app logic; release strategy still unverified`

## Tasks

- [x] Add an `UpdateService` that checks GitHub, then Gitee, then the PythonAnywhere proxy.
- [x] Compare local and remote versions semantically after trimming a leading `v`.
- [x] Parse `[FORCED]` metadata from release notes.
- [x] Parse `[MIN_VER:x.x.x]` metadata from release notes.
- [x] Prefer `.ipa` assets when release data includes an iOS package.
- [x] Fall back to the release page when no iOS package is present.
- [x] Parse both direct GitHub release payloads and proxy-wrapped release payloads on iOS.
- [x] Surface update info through `AppState` and show `UpdateAlertView` from the root navigation layer.
- [x] Remove the misleading direct-install implication from the update UI and explain that IPA delivery still needs manual AltStore/SideStore import.
- [x] Prevent Android-only releases from becoming an unsatisfiable forced-update lock on iOS when no `.ipa` asset exists.
- [ ] Verify the actual release distribution path the project wants for iOS, because current public release metadata may still be Android-first.
- [ ] Validate the full update UX on a real iPhone.

## Notes

- The update checker exists, but operational success depends on whether releases actually publish iOS-ready assets and whether the team accepts manual IPA distribution.
- As of 2026-06-13, the public `v1.1.2` release exposed by GitHub and the PythonAnywhere proxy is still Android-only (`.apk`), so the iOS client now downgrades forced-update behavior unless an `.ipa` asset is actually present.
- This should not be marked fully complete until the release channel itself is confirmed, not just the parsing logic.

## Done Criteria

- [x] The app can statically decide whether a release is newer and whether it should be forced.
- [x] The UI no longer claims that tapping an IPA link is equivalent to an in-app install.
- [ ] The chosen release flow is validated against real published iOS artifacts.
