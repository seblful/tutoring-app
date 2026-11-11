# Run the iOS app

The iOS app is the Android app's screens and logic compiled for iOS: one Compose Multiplatform UI in
`:ui`, linked as the static `KlasApp` framework into a SwiftUI shell in `iosApp/`. A change to a screen,
a rule or a word reaches both apps; only the platform adapters below are written twice.

On an Apple Silicon Mac with Xcode, JDK 21 and the Android SDK configured as in the README:

```bash
brew install xcodegen
xcodegen generate --spec iosApp/project.yml
open iosApp/Klas.xcodeproj
```

Run the `Klas` scheme on a simulator or a phone (set a signing team for a phone). The Xcode build runs
`./gradlew :ui:embedAndSignAppleFrameworkForXcode`, which builds the framework and copies the Compose
resources (fonts, images, licence) into the app. `iosApp/project.yml` is the project's source; regenerate
after changing it. The generated project, `Info.plist` and build output are ignored by Git.

## What is where

| Part | Android | iOS |
| --- | --- | --- |
| Entry point | `KlasApp`, `MainActivity` | `iosApp/Sources/KlasApp.swift` → `KlasIos`, `MainViewController` |
| Graph | `KlasGraph` (`app/`) | `IosGraph` (`ui/src/iosMain/.../di/`) |
| Database, clock, locales, device facts | `app/.../di/` modules | `IosModule` |
| Notifications and their schedule | `AndroidNotifier`, alarms, WorkManager | `IosNotifications.kt` |
| Calendar mirror | `DeviceCalendarSync` (CalendarContract) | `IosCalendar.kt` (EventKit) |
| Files, sharing, backup folder, backup cipher | `app/.../storage/`, `data/backup` | `IosStorage.kt` |
| PDF sheets | `PdfSheetPrinter` | `IosSheetPrinter` |
| Crash log | `FileCrashLogStore`, `CrashHandler` | `IosCrashLogStore`, `KlasIos.start` |
| Screen-level seams (pickers, permissions, theme, lock) | `ui/src/androidMain` `*.android.kt` | `ui/src/iosMain` `*.ios.kt` |

Words every notification says come from the shared `TrayText`, so the two trays read the same.
A sealed backup is the same format on both: `AesGcm` in common code builds GCM over the platform's
AES block, so a backup sealed on one phone opens on the other.

## How iOS differs

- **Notifications are decided ahead.** iOS does not wake an app to post a reminder, so `IosScheduler`
  runs the shared decisions (`DueReminder`, `DueDigest`) when the plan is made, as of the moment each
  will show, and leaves the result with the system. The plan is made again after every change and each
  time the app comes to the front. At most 60 reminders are pending at once (iOS keeps 64).
- **Upkeep runs in the foreground.** Auto-completion, the next reminder plan and the backup check run
  when the app opens or returns to the front; iOS gives no periodic background time to count on.
- **The calendar mirror writes events only.** EventKit cannot invite guests, and an event alarm would
  repeat the app's own reminder, so iOS events carry neither.
- **Not yet on iOS:** home-screen widgets (WidgetKit), launcher shortcuts, and optical centring of
  digits in round badges (`rememberInkBounds` returns null).

## Tests

Common tests of `:domain`, `:shared` and `:ui` run on the iOS simulator:

```bash
./gradlew :domain:iosSimulatorArm64Test :shared:iosSimulatorArm64Test :ui:iosSimulatorArm64Test
```

`iosApp/Tests/LaunchTests.swift` builds the app, checks that the screens come up, and relaunches it over
the database it left. Choose a simulator UDID from `xcrun simctl list devices available`, then:

```bash
xcodebuild test -project iosApp/Klas.xcodeproj -scheme Klas \
  -destination 'platform=iOS Simulator,id=<UDID>' \
  -derivedDataPath iosApp/DerivedData CODE_SIGNING_ALLOWED=NO
```

The macOS job in [CI](../.github/workflows/ci.yml) runs both. Windows compiles the iOS Kotlin
(`./gradlew :ui:compileKotlinIosSimulatorArm64`), which also checks the iOS graph; linking, Swift and
the simulator need macOS.
