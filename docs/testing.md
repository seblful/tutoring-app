# Testing

Choose tests by the behavior they prove. Quick build commands are in the [README](../README.md#build--test).
Run `./gradlew testQuick` during development: domain, shared and UI JVM tests, Android behavior tests and
`node --test store/` plus the Android host-script tests, without screenshot rendering. Node 22+ is
required for the tooling suites.
`./gradlew verify` also runs screenshot comparisons, Android lint, ktlint, detekt and debug app/test
builds used by CI. Device and iOS simulator execution are separate checks.

## Unit tests

| Location | Use for |
|---|---|
| `domain/src/commonTest` | Pure rules with `kotlin.test`; the same tests run on JVM and iOS. |
| `shared/src/commonTest` | Repository atomicity and race cases, backup contracts, feature queries, parsing and clock events over real Room databases. Platform test source sets provide database creation. |
| `ui/src/commonTest` | Date, time and money formatting in each language. The JVM run reads Android's `java.text`/`java.time` tables; the iOS run reads Foundation's, so both platforms' CLDR data meet the same expectations. |
| `app/src/test` | Android locale/graph behavior, view models, mappers and source/artifact tripwires. Unmocked Android calls fail (`isReturnDefaultValues` is off). |

- Seed valid application states through `testutil/Fixtures`; use `storedClass` for raw persisted states.
  `DirectSeedingRatchetTest` lists the few direct-write exceptions by file and purpose, and fails if an
  exception grows or disappears without updating that list.
- `RepositoryTestEnv` in `shared/src/testFixtures/kotlin` owns the common repository graph. Shared,
  Android JVM and device adapters supply real Room databases with bundled SQLite and own their cleanup.
  Shared tests close their adapter with `@AfterTest`; Android JVM tests use a JUnit rule. No fake DAOs.
  Query and pilot tests exercise the production shared graph.
- Portable port doubles, fixtures and a pinned `MutableClock` are compiled only into test source sets.
  Give repositories and view models the same clock and zone. Tests of system clock changes explicitly
  opt into the device clock.
- Digest and maintenance behavior belongs in shared common tests; Android tests cover notifier and
  scheduling adapters. Generated invariant tests use fixed seeds and include the seed/case in failures;
  keep their assertions independent of the rule they exercise.
- Wrap manually constructed view models in `mainRule.own(...)`. The dispatcher rule clears them and
  cancels its application scope before the database rule closes; use rule order 1 for the dispatcher
  and order 0 for the database environment. `clearViewModels()` models leaving the screen without
  cancelling application writes. For save races, use `pauseNextTransaction()`, await its entry, perform
  the competing action, then release it in `finally`; assert both the paused state and the final rows.
  Use queued test dispatchers for lifecycle tests rather than relying on immediate execution.
- `PilotWorkflowTest` closes and reopens a temporary file database to prove persistence; each platform
  supplies file creation and cleanup. In-memory tests cannot prove this contract.
- `RecordingTransactor` counts real transactions and `failAt` forces rollback. Queries use
  `Dispatchers.Unconfined`, so writes/invalidated flows settle together; `advanceUntilIdle()` remains
  the test clock. Referenced rows must exist before booking or saving.
- Artifact tripwires use `testutil/ProjectFiles` and inspect all three modules. They guard schema/backup
  coverage, enum names, table writes and migration wiring; `MigrationChainTest` is the unit guard for
  upgrades. Test UI behavior through Compose semantics and interactions, and visual styling through
  previews. General source restrictions belong to detekt; avoid filename and call-spelling assertions.
- `./gradlew :app:testDebugBehavior` runs Android JVM behavior tests without previews or screenshots.
  The underlying `:app:testDebugUnitTest` remains the full JVM suite used by Roborazzi.
- `./gradlew :domain:jvmTest :shared:jvmTest :ui:testAndroidHostTest` runs common tests on the JVM without
  a device.
- Common tests use `kotlin.test` assertions and `assertFailsWith`, without JVM-only annotation
  parameters or plain `assert`. Keep commas out of backtick function names; Kotlin/Native rejects them.
- On an Apple Silicon Mac, run `./gradlew :domain:iosSimulatorArm64Test :shared:iosSimulatorArm64Test
  :ui:iosSimulatorArm64Test`.
  Set `KMP_IOS_SIMULATOR_ID` to an available simulator UDID to select a device. CI selects an available
  iPhone, runs the three suites and compiles the iOS device code. Windows can compile native Kotlin
  libraries but cannot link or run the simulator tests. CI also builds the [SwiftUI host](ios.md)
  and runs its create/book/terminate/relaunch test.

## Screenshot tests

A shared atom's `@KlasPreview` in `app/src/debug/.../preview/` is its screenshot test: light, dark and
1.5× text, in Russian. Avoid previews needing a view model/graph. ComposablePreviewScanner and the
Roborazzi plugin generate tests; no handwritten test class is needed.

```bash
./gradlew :app:recordRoborazziDebug # deliberately update committed app/src/test/screenshots goldens
./gradlew :app:verifyRoborazziDebug # app unit suite + golden comparison
```

`FormFactorScreenshotTest` is the other kind: the real app, seeded with the demo tutor, on a phone (also
in dark theme and at 1.5× text), an unfolded foldable, a tablet and a desktop window — bar or rail, People alone and with a card beside it,
homework in columns, and a settings page beside the index. It ends on the card: a tab tapped before its bar hides and returns may still show a press. Its goldens are recorded and compared by the same tasks.

`ScreenTourScreenshotTest` covers the rest on a phone — lesson sheet, forms, Settings and every page it opens,
onboarding — in light, dark, 1.5× text and Russian (`Tour.*`). It finds controls by their strings, so one
tour runs in both languages; a screen that changes by accident during a UI migration shows here.

`WidgetScreenshotTest` draws the home-screen widgets as a launcher does: each layout composed to RemoteViews
at a cell size and applied under an `AppWidgetHostView` (a list fills only there), in Russian, light and dark.

Review PNG changes and commit them with the UI change. Failures leave `*_compare.png` (golden | diff |
new) in `app/build/outputs/roborazzi/`; CI uploads `screenshot-diffs`. `testQuick` and
`:app:testDebugBehavior` exclude generated previews, form-factor capture and opt-in store captures.
Use Roborazzi tasks to record or verify screenshots; keep those checks in `verify`.

`KlasPreviewTester` tolerates up to **0.5%** differing pixels, without neighboring-pixel shifts, for
Windows/Linux rasterization differences. Tiny color changes can pass: inspect those previews yourself.
For platform-only failures, use the Linux render from CI's artifact.

Shared Robolectric configuration lives in `app/src/test/resources/robolectric.properties` (target SDK,
bare `Application`); native graphics, resources and JDK access flags live in `app/build.gradle.kts`.

## Coverage

`./gradlew coverageReport` runs `:app:testDebugBehavior`, `:domain:jvmTest` and `:shared:jvmTest` with
the JaCoCo agent and writes one merged HTML/XML report to `build/reports/jacoco/coverageReport/`. The
`:ui` screens count through the `:app` behavior tests that drive them.
Preview screenshots are left out: they render code without asserting on it. `:shared` counts only its
own tests, because `:app` runs its Android build, whose classes differ from the JVM ones the report
reads. Metro, Room and Compose-generated classes and debug previews are excluded. Device, iOS and
screenshot runs carry no agent. There is no coverage gate; read the report for untested branches.

## Instrumented tests

Use `app/src/androidTest` for migrations, device calendar/notifications, backup round-trip, gestures,
text measurement, plurals and accessibility. `DevicePersistenceTest` checks the bundled Android driver;
repository business rules and query calculations run in shared common tests on JVM and iOS.
`KlasTestRunner` uses `KlasTestApp`'s production graph
without startup scheduling; activity/receiver injection works even before a test creates a rule.
`KlasGraphTest` covers runtime graph discovery separately on the JVM.

- Each migration validates against exported schemas through `migrationTestHelper` in
  `androidTest/.../data/local/MigrationTesting.kt`, using the bundled driver. `SupportedUpgradeTest`
  also opens populated v5–v9 files through the production factory with destructive fallback off,
  checking final repository queries and foreign keys.
- [Device CI](../.github/workflows/device-tests.yml) runs the whole suite on API 35 and the data package
  on minSdk/API 24 for releases and manual runs. Relevant pull requests run the data suite on both
  levels, including populated old-schema upgrades. Both modes also force-stop and relaunch the debug
  app, then verify its persisted lesson, payment and balance in a new process. To run only data locally,
  add `-Pandroid.testInstrumentationRunnerArguments.package=com.seblful.klas.data` to the connected task.
- If a crashed run locks Gradle's results folder on Windows, bypass that folder:
  `./gradlew :app:installDebug :app:installDebugAndroidTest`, then
  `adb shell am instrument -w -e class <class> com.seblful.klas.debug.test/com.seblful.klas.KlasTestRunner`.
  Repeat from a stopped app (`am force-stop`) to distinguish startup races from test failures.
- To run the cold-relaunch probe on a connected device, install both APKs with
  `./gradlew :app:installDebug :app:installDebugAndroidTest`, then run
  `node scripts/android-relaunch.mjs`. Use `--serial <device>` to select a device and `--adb <path>`
  when adb is outside PATH and the SDK environment variables. It force-stops the debug app, seeds
  temporary records through the production graph, checks a different process ID after relaunch and
  removes its records. `ColdRelaunchTest` is intentionally skipped outside this host-driven sequence;
  running its phases in one instrumentation invocation cannot prove a process restart.
- Payment, duration and repeat-count dialogs have saved-state restoration tests that confirm the
  restored value, rather than only checking that their controls reappear.
- Draft restoration tests create new student, group and booking view models from saved handles; editor
  tests restore the parent payment/homework interaction with typed fields. Test process death with the
  activity stopped in the background and return to its existing task; force-stop represents a fresh launch.
- Device UI checks cover 200% text, RTL swipe direction, search Back/IME behavior and short landscape
  dialogs. Whole-word label checks at large text apply to the selected navigation label; every icon must
  retain its full accessible name.
- `./gradlew testDeviceTools` tests the host runner's failure detection and cleanup without a device.
- Notification display and real calendar writes need observation on a device.

## Static analysis

ktlint reads [.editorconfig](../.editorconfig) and checks all files without a baseline. detekt reads
[its config](../config/detekt/detekt.yml) and per-module `config/detekt/baseline.xml`; new findings fail.
`./gradlew ktlintCheck detekt` checks every module. `./gradlew ktlintFormat` changes the whole tree:
review the diff. Run `./gradlew detektBaseline` only after reviewing findings.
Its `ForbiddenImport` list also keeps Material's dialogs, sheets, cards, FABs, dividers and buttons out of
feature code: features use the `ui/designsystem` wrappers (see [UI conventions](ui-conventions.md)).
