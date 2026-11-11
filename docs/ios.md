# Run the iOS app

The iOS app is the Android app's screens and logic compiled for iOS: one Compose Multiplatform UI in
`:ui`, hosted by `:iosHost` (as `:app` hosts it on Android) and linked as the static `KlasApp` framework
into a SwiftUI shell in `iosApp/`. A change to a screen,
a rule or a word reaches both apps; only the platform adapters below are written twice.

On an Apple Silicon Mac with Xcode, JDK 21 and the Android SDK configured as in the README:

```bash
brew install xcodegen
xcodegen generate --spec iosApp/project.yml
open iosApp/Klas.xcodeproj
```

Run the `Klas` scheme on a simulator or a phone. For a phone, set a signing team on the `Klas` and
`KlasWidgets` targets and register the app group `group.com.seblful.klas` for both; without the group
the app runs and the widgets stay on their placeholder. The Xcode build runs
`./gradlew :iosHost:embedAndSignAppleFrameworkForXcode`, which builds the framework and copies the Compose
resources (fonts, images, licence) into the app. `iosApp/project.yml` is the project's source; regenerate
after changing it. The generated project, `Info.plist` and build output are ignored by Git.

## What is where

| Part | Android | iOS |
| --- | --- | --- |
| Entry point | `KlasApp`, `MainActivity` | `iosApp/Sources/KlasApp.swift` → `KlasIos`, `MainViewController` |
| Graph | `KlasGraph` (`app/`) | `IosGraph` (`iosHost/`, `di/`) |
| Database, clock, locales, device facts | `app/.../di/` modules | `IosModule` |
| Notifications and their schedule | `AndroidNotifier`, alarms, WorkManager | `IosNotifications.kt` |
| Calendar mirror | `DeviceCalendarSync` (CalendarContract) | `IosCalendar.kt` (EventKit) |
| Files, sharing, backup folder, backup cipher | `app/.../storage/`, `data/backup` | `IosStorage.kt` |
| PDF sheets | `PdfSheetPrinter` | `IosSheetPrinter` |
| Home-screen widgets | Glance widgets (`app/.../widget/`), `WidgetUpdates` | `IosHomeScreen` writes a snapshot; `iosApp/Widgets` draws it |
| Lifecycle, app lock, switcher privacy, icon shortcuts | `MainActivity` → `AppSession`; `AppShortcuts` | `KlasIos` → `AppSession`; `IosHostEffects`; `KlasApp.swift` hands quick actions over |
| Crash log | `FileCrashLogStore`, `CrashHandler` | `IosCrashLogStore`, `KlasIos.start` |
| Screen-level seams (pickers, permissions, theme, lock) | `ui/src/androidMain` `*.android.kt` | `ui/src/iosMain` `*.ios.kt` |

Every notification is decided once as a `TrayNotice` (its kind, words from `TrayText`, where a tap goes
and its buttons, built by `TrayNotices`), and both notifiers extend `TrayNotifier`, so they only draw it:
a reminder's «Taught» and «Move» are Android actions and an iOS notification category alike. The
widgets' rows (`TodayRows.kt`) and the shortcuts on the icon and the quick-actions widget (`Shortcuts`)
are listed once in `:ui` too; each platform only maps `ShortcutIcon` to its own glyphs.
A sealed backup is the same format on both: `AesGcm` in common code builds GCM over the platform's
AES block, so a backup sealed on one phone opens on the other.

## How iOS differs

- **Notifications are decided ahead.** iOS does not wake an app to post a reminder, so `IosScheduler`
  runs the shared decisions (`DueReminder`, `DueDigest`) when the plan is made, as of the moment each
  will show, posting to `IosNotifier.at` that moment, which leaves the result with the system. The plan is made again after every change and each
  time the app comes to the front. At most 60 reminders are pending at once (iOS keeps 64).
- **Upkeep runs in the foreground.** Auto-completion, the next reminder plan and the backup check run
  when the app opens or returns to the front; iOS gives no periodic background time to count on.
- **The calendar mirror writes events only.** EventKit cannot invite guests, and an event alarm would
  repeat the app's own reminder, so iOS events carry neither.
- **The widgets draw a snapshot.** A WidgetKit extension is Swift and cannot run the app's code, so
  `IosHomeScreen` writes `WidgetSnapshot` (every word, and Today's pages for the next seven days: one
  from each midnight and at each lesson's start and end) into the app group `group.com.seblful.klas`, and
  asks WidgetKit to reload. The extension only lays it out. `WidgetSnapshotContractTest` (Kotlin) and
  `WidgetSnapshotTests` (Swift) read the same fixture, `iosApp/Tests/Fixtures/widget-snapshot.json`.
- **Today's tick opens the lesson.** Android's check marks a lesson taught in place, through the app's
  code; on iOS a row and its check open the lesson's sheet (`klas://lesson/<id>`), where it is marked.
  Lessons are redacted on a locked phone unless `Privacy.lockScreenWidgets` is on.
- **The switcher sees a cover.** Android blanks the recent-apps preview; iOS has no such switch, so when
  `Privacy.hideInRecents` or the app lock is on, `IosHostEffects` covers the window as the app resigns
  active, which is the picture the app switcher keeps. Control Center and the Face ID prompt show it briefly.
- **Everything outside the app is a link.** Each notification's tap, quick action on the app's icon and
  widget button carries the `klas://` link of its `TrayNavigation.Destination` (`TrayNavigation.linkFor`),
  as Android's intents do; `KlasIos.openLink` reads it back.

- **Klas Pro is not sold yet.** `IosProStore` sells nothing, so `Access.of` makes everyone Pro and the
  Settings row is hidden. Before an App Store release, write a StoreKit adapter there (the same two products,
  one-time and monthly with the trial) and add the products in App Store Connect.

## Tests

Common tests of `:domain`, `:shared` and `:ui` run on the iOS simulator:

```bash
./gradlew :domain:iosSimulatorArm64Test :shared:iosSimulatorArm64Test :ui:iosSimulatorArm64Test
```

`:ui`'s iOS tests also draw `InkCentredText` with Skia and measure where the digits' ink falls
(`InkCentredTextIosTest`). `iosApp/Tests/LaunchTests.swift` builds the app, checks that the screens come up,
and relaunches it over the database it left; `WidgetSnapshotTests.swift` reads the widget fixture. Choose a simulator UDID from `xcrun simctl list devices available`, then:

```bash
xcodebuild test -project iosApp/Klas.xcodeproj -scheme Klas \
  -destination 'platform=iOS Simulator,id=<UDID>' \
  -derivedDataPath iosApp/DerivedData CODE_SIGNING_ALLOWED=NO
```

The macOS job in [CI](../.github/workflows/ci.yml) runs both. Windows compiles the iOS Kotlin
(`./gradlew :iosHost:compileKotlinIosSimulatorArm64`), which also checks the iOS graph; linking, Swift and
the simulator need macOS.
