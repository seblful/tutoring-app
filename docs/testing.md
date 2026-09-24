# Testing

Where a test goes in Klas, and what the test setup expects. Commands and what CI runs are in the
[README](../README.md#build--test).

## Unit tests (`src/test`)

- ~100 classes: domain, backup format, view models, repository side effects, mappers, formatters —
  against in-memory fake DAOs. `isReturnDefaultValues` is off (a test needing Android has found a missing
  port). Seed through `testutil/Fixtures`, never DAOs directly.
- A handful of tripwire tests guard silent drift by deriving expectations from a real artifact rather than
  restating them (schema coverage, enum names, whole-table writes, screen-state/shell surface rules,
  migration chain wiring and the view each step leaves). `MigrationChainTest` is the only one in the unit
  suite guarding the upgrade path.

## Instrumented tests (`src/androidTest`)

- 27 classes cover what needs a device: Room/FK integration, backup round-trip, calendar sync,
  auto-completion, text measurement, gesture behavior, plural rules, screen-reader semantics.
- Every class runs under a custom `KlasTestApp` (the app's own receivers/workers wake mid-run and need a
  real Hilt component).
- Every migration has a device test validating column-for-column against the exported schema.
- Notification display and real calendar writes can only be observed on a device.
