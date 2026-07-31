# Klas — Tutoring App

A native Android app for a **single private tutor** to manage students, groups, subjects, schedule,
payments and debts. **Offline-first and local-only — no backend, no account, no cloud.** Russian and
English UI (following the phone unless told otherwise), Material 3, money in **BYN**, **RUB**, **USD**
or **EUR**.

> Application ID: `com.seblful.klas` · current version: `0.3.0` (`versionCode` 4) · launcher label: **Klas**

## Features

- **People** — students and groups in one tab, searchable; students also sortable by who owes the most.
- **Students & subjects** — per-student subjects, each with an hourly price and a change log; archive-only (history is protected).
- **Groups** — a named roster taught one subject at the group's own hourly rate. Booking one lesson books it for every member, so the schedule shows one card while billing stays per-student — including "this one didn't come".
- **Schedule** — day / week / month views of one-off classes, with a `scheduled → completed | cancelled` lifecycle, swipe-to-complete, and optional auto-complete once a class ends. One tutor, one lesson at a time: an overlapping slot is refused.
- **Billing** — hourly rate booked per class, so re-pricing never rewrites history; balances and income derived on the fly, never stored.
- **Reports** — income and outstanding debt for a month, a year or all time: trend over time, individual vs group split, per-student/subject/group breakdowns, and **CSV export**.
- **Calendar sync** (opt-in) — mirrors classes to the device calendar via `CalendarContract`, so the OS syncs them to Google; email reminders ride on calendar attendees (a separate opt-in).
- **Reminders** — notifications ahead of a lesson, driven by `AlarmManager` (exact where the platform allows) so they arrive on time rather than whenever the system feels like it.
- **Backup** — full database export/import as a JSON snapshot via the Storage Access Framework, checked for readability before a single row is written.
- **Crash diagnostics** — local crash log (viewable/exportable in-app) plus Google Play Vitals; no third-party SDK.
- **Language & currency** — Russian or English, following the phone by default, switched instantly in Settings or picked during first-run setup. Money is labelled BYN / RUB / USD / EUR — a display setting, never a conversion — and a fresh install starts on whichever of the four the phone's region names.

## Tech Stack

- **Kotlin 2.3.21**, **Jetpack Compose** (Material 3), Navigation Compose
- **Room** 2.8 (single source of truth) · **Hilt** 2.60 DI · **WorkManager** + `AlarmManager`
- **kotlinx-serialization** (backup) · `java.time` with core-library desugaring
- AGP 9.3.1 (built-in Kotlin) · Gradle 9.6.1 · **JDK 21** · compileSdk 37 / targetSdk 36 · **minSdk 24**

Architecture is MVVM over a pure, unit-tested domain layer. See **[CONTEXT.md](CONTEXT.md)** for the
full architecture, module map, and design decisions.

## Getting Started

**Prerequisites:** Android Studio with **JDK 21** (the bundled JBR works), and the Android SDK.

1. Clone the repository:
   ```bash
   git clone https://github.com/seblful/tutoring-app.git
   ```
2. Ensure `local.properties` points at your SDK (`sdk.dir=...`) — Android Studio creates this on first open.
3. Let Gradle sync, then **Run ▶** on an emulator or device.

## Build & Test

```bash
./gradlew :app:assembleDebug            # debug APK
./gradlew :app:testDebugUnitTest        # JVM unit tests (domain, view models, mappers, backup)
./gradlew :app:lintDebug                # lint (release lint is off — see CONTEXT.md)
./gradlew :app:assembleDebugAndroidTest # build instrumented tests (Room, calendar, backup round-trip)
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk` and installs without any signing
setup. It carries a `.debug` application id, so it sits beside a release install with its own database.

CI (`.github/workflows/ci.yml`) runs the unit suite, `lintDebug`, the debug APK and the *compilation*
of the instrumented tests on every PR and every push to `main`/`develop`. Running the instrumented
tests themselves needs a connected device (`./gradlew :app:connectedDebugAndroidTest`).

## Building a Signed Release APK

The `release` build type is **minified and resource-shrunk (R8)** and is signed when a
`keystore.properties` file is present (it is gitignored). Without it, the release build is left
unsigned so debug builds, CI, and contributors are unaffected.

1. **Generate an upload key** once, storing the `.jks` *outside* the repo:
   ```bash
   keytool -genkeypair -v -keystore ../upload-keystore.jks -alias upload \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
   Keep the file and its password backed up — losing them blocks future updates.

2. **Create `keystore.properties`** in the repo root (copy `keystore.properties.example`):
   ```properties
   storeFile=../upload-keystore.jks
   storePassword=<your keystore password>
   keyAlias=upload
   keyPassword=<your key password>
   ```
   If you pressed Enter at keytool's key-password prompt, both passwords are the same value.

3. **Build:**
   ```bash
   ./gradlew :app:assembleRelease
   ```
   Output: `app/build/outputs/apk/release/app-release.apk` (a `-unsigned` suffix means signing was skipped — check `keystore.properties`).

4. **Install on your own device:**
   ```bash
   adb install "app/build/outputs/apk/release/app-release.apk"
   ```
   Or copy the APK to the phone and tap it (allow "install from unknown sources").

> **Note:** a sideloaded APK is signed with *your* upload key, not Google's. A later Play Store
> install of the same app won't update over it — uninstall the sideloaded copy first.
>
> ⚠️ The release build is minified. Verify the **backup export/import** round-trip on a release build
> before relying on it, since serialization is reflection-driven (R8 keep rules live in
> `app/proguard-rules.pro`).

## Publishing to Google Play

For the App Bundle (`.aab`), Play Console setup, and the testing/production rollout flow, see
**[docs/publishing-to-google-play.md](docs/publishing-to-google-play.md)**.

## Project Structure

```
app/src/main/java/com/seblful/klas/
├── domain/                       # pure rules + ports + models (JVM-tested)
├── data/                         # Mappers.kt + {local,repository,backup}/
├── calendar/ notifications/ diagnostics/ work/ format/ storage/ di/
├── ui/{schedule,people,students,groups,subjects,reports,backup,more,
│       onboarding,diagnostics,common,navigation,theme}/
├── MainActivity.kt               # @AndroidEntryPoint → AppRoot()
└── KlasApp.kt                    # @HiltAndroidApp, WorkManager config, startup upkeep
```

## License

Copyright (C) 2026 seblful

This program is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0** as published by the Free Software Foundation. See the
[LICENSE](LICENSE) file for the full text.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
