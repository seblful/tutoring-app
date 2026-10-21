# Testing

Choose tests by the behavior they prove. Quick build commands are in the [README](../README.md#build--test).
`./gradlew verify` runs the JVM suites, screenshot comparisons, Android lint, ktlint, detekt and debug
app/test builds used by the main CI job. Device and iOS simulator execution are separate checks.

## Unit tests

| Location | Use for |
|---|---|
| `domain/src/commonTest` | Pure rules with `kotlin.test`; the same tests run on JVM and iOS. |
| `shared/src/commonTest` | Repository atomicity and race cases, backup contracts, feature queries, parsing and clock events over real Room databases. Platform test source sets provide database creation. |
| `app/src/test` | Android locale/graph behavior, view models, mappers, formatters and source/artifact tripwires. Unmocked Android calls fail (`isReturnDefaultValues` is off). |

- Seed through `testutil/Fixtures`, never DAOs (`DirectSeedingRatchetTest` guards exceptions).
- Shared repository/backup tests use a portable `TestEnv` and close it with `@AfterTest`. Android
  view-model tests keep their JUnit-rule `TestEnv` under Robolectric. Both use real Room and bundled
  SQLite: schema, converters, FKs and cascades are production code; only host behavior is doubled.
  No fake DAOs. Store/query and pilot tests use the production shared graph as well.
- Portable port doubles, seeding helpers and `MutableClock` live in `shared/src/testFixtures/kotlin`.
  Both shared and Android unit suites compile those sources against their own `TestEnv`; fixture
  behavior has one implementation without adding test code to the app or library artifacts.
- `PilotWorkflowTest` closes and reopens a temporary file database to prove persistence; each platform
  supplies file creation and cleanup. In-memory tests cannot prove this contract.
- `RecordingTransactor` counts real transactions and `failAt` forces rollback. Queries use
  `Dispatchers.Unconfined`, so writes/invalidated flows settle together; `advanceUntilIdle()` remains
  the test clock. Referenced rows must exist before booking or saving.
- Artifact tripwires use `testutil/ProjectFiles` and inspect all three modules. They guard schema/backup
  coverage, enum names, table writes, screen contracts and migration wiring; `MigrationChainTest` is
  the unit guard for upgrades. Generic formatting/complexity belongs to ktlint/detekt.
- `./gradlew :app:testDebugUnitTest` runs app tests without screenshot comparison.
- `./gradlew :domain:jvmTest :shared:jvmTest` runs common tests on the JVM without Android.
- Common tests use `kotlin.test` assertions and `assertFailsWith`, without JVM-only annotation
  parameters or plain `assert`. Keep commas out of backtick function names; Kotlin/Native rejects them.
- On an Apple Silicon Mac, run `./gradlew :domain:iosSimulatorArm64Test :shared:iosSimulatorArm64Test`.
  Set `KMP_IOS_SIMULATOR_ID` to an available simulator UDID to select a device. CI selects an available
  iPhone, runs both suites and compiles shared iOS device code. Windows can compile native Kotlin
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

`FormFactorScreenshotTest` is the other kind: the real app, seeded with the demo tutor, on a phone, an
unfolded foldable, a tablet and a desktop window — bar or rail, People alone and with a card beside it,
homework in columns, and a settings page beside the index. It ends on the card: a tab tapped before its bar hides and returns may still show a press. Its goldens are recorded and compared by the same tasks.

Review PNG changes and commit them with the UI change. Failures leave `*_compare.png` (golden | diff |
new) in `app/build/outputs/roborazzi/`; CI uploads `screenshot-diffs`. Plain unit runs compose previews
but do not capture/compare them.

`KlasPreviewTester` tolerates up to **0.5%** differing pixels, without neighboring-pixel shifts, for
Windows/Linux rasterization differences. Tiny color changes can pass: inspect those previews yourself.
For platform-only failures, use the Linux render from CI's artifact.

Shared Robolectric configuration lives in `app/src/test/resources/robolectric.properties` (target SDK,
bare `Application`); native graphics, resources and JDK access flags live in `app/build.gradle.kts`.

## Instrumented tests

Use `app/src/androidTest` for migrations, device calendar/notifications, backup round-trip, gestures,
text measurement, plurals and accessibility. `KlasTestRunner` uses `KlasTestApp`'s production graph
without startup scheduling; activity/receiver injection works even before a test creates a rule.
`KlasGraphTest` covers runtime graph discovery separately on the JVM.

- Each migration validates against exported schemas through `migrationTestHelper` in
  `androidTest/.../data/local/MigrationTesting.kt`, using the bundled driver.
- [Device CI](../.github/workflows/device-tests.yml) runs the whole suite on API 35 and the data package
  on minSdk/API 24. Tags run it automatically; any branch can run it manually. To run only data locally,
  add `-Pandroid.testInstrumentationRunnerArguments.package=com.seblful.klas.data` to the connected task.
- If a crashed run locks Gradle's results folder on Windows, bypass that folder:
  `./gradlew :app:installDebug :app:installDebugAndroidTest`, then
  `adb shell am instrument -w -e class <class> com.seblful.klas.debug.test/com.seblful.klas.KlasTestRunner`.
  Repeat from a stopped app (`am force-stop`) to distinguish startup races from test failures.
- Notification display and real calendar writes need observation on a device.

## Static analysis

ktlint reads [.editorconfig](../.editorconfig) and checks all files without a baseline. detekt reads
[its config](../config/detekt/detekt.yml) and per-module `config/detekt/baseline.xml`; new findings fail.
`./gradlew ktlintCheck detekt` checks every module. `./gradlew ktlintFormat` changes the whole tree:
review the diff. Run `./gradlew detektBaseline` only after reviewing findings.
