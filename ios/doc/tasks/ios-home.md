# ios-home

> Dependencies: `ios-core`
> Related requirements: `F3`, `F6`
> Current status: `implemented and archive-validated on macOS CI; live sync behavior remains a documented runtime risk`

## Tasks

- [x] Show the current device list from the iOS container state.
- [x] Render an empty state when no devices exist yet.
- [x] Support adding a device from QR scan or manual text input.
- [x] Support renaming a device from the home list.
- [x] Support starring and unstarring a device.
- [x] Add a delete confirmation prompt before removing a device.
- [x] Push add, rename, star, and delete operations through the current iOS repository/container path.
- [x] Expose pull-to-refresh for cloud sync.
- [x] Link each device row to the valve page.
- [x] Link the toolbar to the record page.
- [x] Validate that the home module participates in successful macOS archive builds and document the remaining live sync/runtime risk separately.

## Notes

- Deletion confirmation is implemented in the iOS UI, but cloud-sync behavior still needs device-level verification against the live backend.

## Done Criteria

- [x] The home screen is no longer a placeholder.
- [x] The main device operations are wired into the current repository state.
- [x] Deletion now requires user confirmation.
- [x] The current coding scope is complete, and the remaining live sync/runtime risk is documented.
