# Klas — Tutoring App

A native Android app for a **single private tutor**: students, groups, lessons, homework, payments and
debts. The database stays on your device; no backend or account. Calendar sync and sharing are optional.
Russian and English UI, Material 3, and 28 currencies with localized names and symbols.

> Application ID: `com.seblful.klas` · launcher label: **Klas**

## Features

- **People** — searchable students and groups; per-subject hourly rates and rate history. Students are
  archived to protect history; unreferenced subjects and groups can be deleted.
- **Groups** — one subject and rate per roster, one schedule card, individual attendance and billing.
- **Schedule** — day/week/month views, repeating bookings, swipe-to-complete and optional auto-completion.
  Overlapping lessons are refused; back-to-back lessons are allowed.
- **Homework** — student or group assignments attach to the next lesson automatically; swipe done,
  undo, archive, and share. Lesson sheets and reminders show the assignment.
- **Billing and reports** — booked rates preserve history; derived balances, partial payments,
  overpayments, income/debt breakdowns and CSV export. Changing currency labels never converts amounts.
- **Calendar and reminders** — optional device-calendar sync, separate email-invite opt-in, lesson
  alarms and optional agenda/debt digests.
- **Backup and diagnostics** — manual JSON export/restore and a local, exportable crash log; no
  third-party crash SDK. Google Play Vitals provides platform diagnostics.

## Tech Stack

Kotlin, Jetpack Compose/Material 3 (adaptive), Navigation 3, Room, Metro DI, WorkManager, `AlarmManager`, kotlinx-serialization
and kotlinx-datetime. Android MVVM in `:app`; Kotlin Multiplatform persistence/workflows in `:shared`
and pure rules in `:domain`.
**JDK 21**, **minSdk 24**. [Dependency versions](gradle/libs.versions.toml) ·
[SDK/build settings](app/build.gradle.kts) · [Architecture](docs/architecture.md).

## Getting Started

**Prerequisites:** Android Studio with **JDK 21** (the bundled JBR works), and the Android SDK.

1. Clone and enter the repository:
   ```bash
   git clone https://github.com/seblful/tutoring-app.git
   cd tutoring-app
   ```
2. Ensure `local.properties` points at your SDK (`sdk.dir=...`) — Android Studio creates this on first open.
3. Let Gradle sync, then **Run ▶** on an emulator or device.

## Build & Test

```bash
./gradlew verify                       # the same JVM/Android checks as CI; no device required
./gradlew :app:assembleDebug            # debug APK
./gradlew :domain:jvmTest               # domain unit tests (billing, scheduling, reminders, reports)
./gradlew :shared:jvmTest               # shared parsing, clock and real-database contracts
./gradlew :app:verifyRoborazziDebug     # app unit tests + preview screenshot comparisons
./gradlew :app:lintDebug ktlintCheck detekt
./gradlew :app:connectedDebugAndroidTest # instrumented tests; needs a device/emulator
./gradlew :app:generateBaselineProfile  # regenerate the release baseline profile; needs an API 33+ phone
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Its `.debug` app ID keeps its database separate
from release. On Windows, use `gradlew.bat` in place of `./gradlew`.

[CI](.github/workflows/ci.yml) checks pushes to `main` and PRs into `main`/`develop`; device tests run
separately. See [Testing](docs/testing.md) for test setup, screenshot updates and static analysis,
and [Releasing](docs/releasing.md) for signed builds and tag-driven publishing.

The [iOS pilot](docs/ios.md) exercises student creation, booking and persistence after relaunch through
the shared store. It is a small SwiftUI validation host, with its own database and simulator test.

## Docs

- [Architecture](docs/architecture.md) — where code goes, and which class owns each rule
- [Domain rules](docs/domain-rules.md) — money, lessons, homework, groups, students: what the app enforces and why
- [Calendar and reminders](docs/calendar-and-reminders.md) — what reaches the device calendar and the notification tray
- [Database and backup](docs/database-and-backup.md) — changing the schema or the backup file without losing data
- [UI conventions](docs/ui-conventions.md) — how a screen is built
- [Testing](docs/testing.md) — where a test goes, and what the test setup expects
- [iOS pilot](docs/ios.md) — run the SwiftUI host and its persistence test on a Mac
- [Releasing](docs/releasing.md) — a signed release APK, sideloading it, and the tag-driven release to GitHub and Google Play
- [Store listing](docs/store-listing.md) — regenerating the Play screenshots, feature graphic, icon, text and promo video from the real app

## License

Copyright (C) 2026 seblful. [GNU General Public License v3.0](LICENSE); distributed without warranty.
