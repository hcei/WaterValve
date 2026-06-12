# ios-record

> Dependencies: `ios-core`
> Related requirement: `F5`
> Current status: `implemented, not runtime-verified`

## Tasks

- [x] Show the current local valve-record list from app state.
- [x] Render an empty state when no records exist.
- [x] Support deleting a single record from the list.
- [x] Support clearing all records from the toolbar.
- [x] Format timestamps for display.
- [ ] Verify real record creation timing and ordering from live valve-page events.

## Done Criteria

- [x] The record page is wired into the current iOS state and repository flow.
- [x] Users can delete individual records and clear all records.
- [ ] Real-device runtime confirms records are created at the right points in the valve flow.
