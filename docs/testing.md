# Testing

Where a test goes in Klas, and what the test setup expects. Commands and what CI runs are in the
[README](../README.md#build--test).

## Unit tests (`src/test`)

- **`domain/src/test`** (`:domain:test`, ~30 classes): a rule of `domain/` tested on its own — JUnit
  only, no fixtures, fakes or project files. A test that needs any of those is not a domain test and
  goes in `app`.
- **`app/src/test`** (`:app:testDebugUnitTest`, ~85 classes): backup format, view models, repository side
  effects, mappers, formatters. `isReturnDefaultValues` is off (a test needing Android has found a missing
  port). Seed through `testutil/Fixtures`, never DAOs directly (`DirectSeedingRatchetTest` counts the
  exceptions).
- **Repository, backup and view-model tests run on the real database.** `testutil/TestDatabase` opens the
  production `AppDatabase` in memory (schema, converters, foreign keys, cascades) on the bundled SQLite
  driver, under `RobolectricTestRunner` for the `Context`; `TestEnv` wires the real repositories on it and
  is a `@get:Rule`, so the database closes after each test. Every write goes through the real
  `RoomTransactor`, counted by `RecordingTransactor`, whose `failAt` makes a transaction fail to commit
  and roll back. Queries run inline (`Dispatchers.Unconfined`), so a write and the `Flow`s it invalidates
  have settled when it returns and `advanceUntilIdle()` stays the only clock. Only the device ports
  (calendar, notifier, permissions, schedulers, clock) are doubles; there are no fake DAOs. Rows must
  exist before something names them — a test that books for subject `1` seeds subject `1` first.
- A handful of tripwire tests guard silent drift by deriving expectations from a real artifact rather than
  restating them (schema coverage, enum names, whole-table writes, screen-state/shell surface rules,
  migration chain wiring, the view each step leaves and the tables it rebuilds). `MigrationChainTest` is
  the only one in the unit suite guarding the upgrade path. They live in `app` (they read files through `testutil/ProjectFiles`),
  even one about the domain such as `ReportsSurfaceTest`; a whole-tree scan covers both modules' sources.
- ktlint and detekt own generic formatting and static smells (line length, naming, complexity); the
  source-reading tripwire tests above own the rules specific to this project, which no off-the-shelf
  linter knows.

## Screenshot tests (`@Preview` → `src/test/screenshots`)

- **A screenshot test is a `@Preview`.** Every preview in the app (today `src/debug/.../preview/`, under
  `@KlasPreview`: light, dark, 1.5× text, all Russian) is found by ComposablePreviewScanner, drawn by
  Robolectric with native graphics and compared by Roborazzi with its golden PNG. There is no test class
  to write — the Roborazzi plugin generates one (`roborazzi {}` in `app/build.gradle.kts`); a new preview
  is a new golden on the next record. Previews are for shared atoms; nothing that needs a ViewModel or Hilt.
- Goldens live in `app/src/test/screenshots/` and are committed: a visual change shows up in review as
  a changed PNG.
- `./gradlew :app:recordRoborazziDebug` writes the goldens; `./gradlew :app:verifyRoborazziDebug` runs
  the whole unit suite and fails on any preview that moved, leaving `*_compare.png` (golden | diff | new)
  in `app/build/outputs/roborazzi/`. A plain `testDebugUnitTest` still composes every preview (a crash
  fails it) but captures and compares nothing.
- Change a component on purpose → record, look at the PNG diff, commit the new goldens with the change.
- **Cross-OS tolerance.** Goldens are recorded on Windows and verified on CI's Linux; the same Skia built
  per OS rasterises text a shade differently. `KlasPreviewTester` therefore passes an image when at most
  **0.5% of its pixels** differ beyond Roborazzi's default per-pixel colour distance, with no
  neighbouring-pixel shift allowed — so a moved edge, a new padding or a tinted badge still fails (a
  badge's wash going from 15% to 22% failed 9 of 39 renders). A change smaller than that — one short
  label's colour — can pass; look at the preview when touching such a thing. If CI ever fails on a
  platform-only difference, its `screenshot-diffs` artifact holds the Linux render to commit instead.
- Robolectric settings shared by every Robolectric test are in `src/test/resources/robolectric.properties`
  (SDK 36 = targetSdk, a bare `Application` rather than the Hilt `KlasApp`); the JDK 17+ `--add-opens`
  flags and `isIncludeAndroidResources` are in `app/build.gradle.kts`.

## Instrumented tests (`src/androidTest`)

- 38 classes cover what needs a device: Room/FK integration, backup round-trip, calendar sync,
  auto-completion, text measurement, gesture behavior, plural rules, screen-reader semantics, and the
  shape of a fade frame by frame (`TabReselectFadeTest`, `ItemMotionTest`: pixels on a paused clock).
- Every class runs under a custom `KlasTestApp` (the app's own receivers/workers wake mid-run and need a
  real Hilt component). A receiver woken *before* the first test builds one — some phones send the app
  `BOOT_COMPLETED` at every process start — is dropped by `HiltTestRunner.onException`; it used to kill the
  run with zero tests reported.
- **Running one class without Gradle's results folder**, which a crashed run leaves locked on Windows:
  `./gradlew :app:installDebug :app:installDebugAndroidTest`, then
  `adb shell am instrument -w -e class <class> com.seblful.klas.debug.test/com.seblful.klas.HiltTestRunner`.
  Run it a dozen times from a stopped app (`am force-stop`) to tell a start-up race from a test failure.
- Every migration has a device test validating column-for-column against the exported schema.
- Notification display and real calendar writes can only be observed on a device.
