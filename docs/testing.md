# Testing

Where a test goes in Klas, and what the test setup expects. Commands and what CI runs are in the
[README](../README.md#build--test).

## Unit tests (`src/test`)

- **`domain/src/test`** (`:domain:test`, ~30 classes): a rule of `domain/` tested on its own — JUnit
  only, no fixtures, fakes or project files. A test that needs any of those is not a domain test and
  goes in `app`.
- **`app/src/test`** (`:app:testDebugUnitTest`, ~85 classes): backup format, view models, repository side
  effects, mappers, formatters — against in-memory fake DAOs. `isReturnDefaultValues` is off (a test
  needing Android has found a missing port). Seed through `testutil/Fixtures`, never DAOs directly.
- A handful of tripwire tests guard silent drift by deriving expectations from a real artifact rather than
  restating them (schema coverage, enum names, whole-table writes, screen-state/shell surface rules,
  migration chain wiring and the view each step leaves). `MigrationChainTest` is the only one in the unit
  suite guarding the upgrade path. They live in `app` (they read files through `testutil/ProjectFiles`),
  even one about the domain such as `ReportsSurfaceTest`; a whole-tree scan covers both modules' sources.

## Instrumented tests (`src/androidTest`)

- 32 classes cover what needs a device: Room/FK integration, backup round-trip, calendar sync,
  auto-completion, text measurement, gesture behavior, plural rules, screen-reader semantics.
- Every class runs under a custom `KlasTestApp` (the app's own receivers/workers wake mid-run and need a
  real Hilt component).
- Every migration has a device test validating column-for-column against the exported schema.
- Notification display and real calendar writes can only be observed on a device.
