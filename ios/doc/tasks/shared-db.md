# shared-db

> Dependencies: `shared-models`
> Current status: `implemented with SQLDelight schema and JVM compile validation`

## Tasks

- [x] Keep SQLDelight `Device` and `WaterRecord` schemas in `shared/commonMain`.
- [x] Ensure SQLDelight interface generation works through `:shared:generateCommonMainWaterValveDbInterface`.
- [x] Keep repository mappings compatible with generated query interfaces.
- [ ] Validate the native driver path on macOS during iOS framework build.

## Notes

- The current Swift iOS target still persists its own local state outside the KMP database path.
- SQLDelight generation and JVM compilation now work locally on Windows.

## Done Criteria

- [x] SQLDelight schema generation succeeds.
- [x] Shared JVM compilation succeeds with the generated database interfaces.
- [ ] macOS validates the iOS native-driver path.
