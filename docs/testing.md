# Testing

Choose tests by the behavior they prove. Quick build commands are in the [README](../README.md#build--test).

## Unit tests

| Location | Use for |
|---|---|
| `domain/src/jvmTest` | Pure rules with `kotlin.test` only. No Android, fixtures, fakes or project files; these belong in `app`. |
| `app/src/test` | Backup format, view models, repositories, mappers, formatters and source/artifact tripwires. Unmocked Android calls fail (`isReturnDefaultValues` is off). |

- Seed through `testutil/Fixtures`, never DAOs (`DirectSeedingRatchetTest` guards exceptions).
- Repository/backup/view-model tests use `TestEnv` as a JUnit rule over the real in-memory `AppDatabase`
  and bundled SQLite under Robolectric. Schema, converters, FKs and cascades are production code;
  only device ports, permissions, schedulers and clock are doubles. No fake DAOs.
- `RecordingTransactor` counts real transactions and `failAt` forces rollback. Queries use
  `Dispatchers.Unconfined`, so writes/invalidated flows settle together; `advanceUntilIdle()` remains
  the test clock. Referenced rows must exist before booking or saving.
- Artifact tripwires use `testutil/ProjectFiles` and inspect both modules. They guard schema/backup
  coverage, enum names, table writes, screen contracts and migration wiring; `MigrationChainTest` is
  the unit guard for upgrades. Generic formatting/complexity belongs to ktlint/detekt.
- `./gradlew :app:testDebugUnitTest` runs app tests without screenshot comparison.

## Screenshot tests

A shared atom's `@KlasPreview` in `app/src/debug/.../preview/` is its screenshot test: light, dark and
1.5× text, in Russian. Avoid previews needing a view model/graph. ComposablePreviewScanner and the
Roborazzi plugin generate tests; no handwritten test class is needed.

```bash
./gradlew :app:recordRoborazziDebug # deliberately update committed app/src/test/screenshots goldens
./gradlew :app:verifyRoborazziDebug # app unit suite + golden comparison
```

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
`./gradlew ktlintCheck detekt` checks both modules. `./gradlew ktlintFormat` changes the whole tree:
review the diff. Run `./gradlew detektBaseline` only after reviewing findings.
