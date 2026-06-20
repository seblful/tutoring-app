# Repetit — Tutoring App

A native Android app for a **single private tutor** to manage students, subjects, schedule,
payments and debts. **Offline-first and local-only — no backend, no account, no cloud.** Russian UI,
Material 3, currency in **BYN** (Belarusian ruble).

> Application ID: `com.seblful.repetit` · current version: `0.1.0-beta`

## Features

- **Students & subjects** — per-student subjects, each with an hourly price; archive-only (history is protected).
- **Schedule** — one-off classes with a `scheduled → completed | cancelled` lifecycle; optional auto-complete once a class ends.
- **Billing** — hourly rate booked per class; balances and income derived on the fly, never stored.
- **Reports** — income and outstanding debt for active students.
- **Calendar sync** (opt-in) — mirrors classes to the device calendar via `CalendarContract`, so the OS syncs them to Google; email reminders ride on calendar attendees.
- **Reminders** — precise notifications driven by WorkManager.
- **Backup** — full database export/import as a JSON snapshot via the Storage Access Framework.
- **Crash diagnostics** — local crash log (viewable/exportable in-app) plus Google Play Vitals; no third-party SDK.

## Tech Stack

- **Kotlin 2.1**, **Jetpack Compose** (Material 3), Navigation Compose
- **Room** (single source of truth) · **Hilt** DI · **WorkManager**
- **kotlinx-serialization** (backup) · `java.time` with core-library desugaring
- AGP 8.7 · Gradle 8.9 · **JDK 21** · compile/target SDK 35 · **minSdk 24**

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
./gradlew :app:assembleDebugAndroidTest # build instrumented tests (Room, calendar, backup round-trip)
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk` and installs without any signing setup.

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
app/src/main/java/com/seblful/repetit/
├── domain/                       # pure rules + ports + models (JVM-tested)
├── data/{local,repository,backup}/
├── calendar/ notifications/ diagnostics/ work/ format/ di/
├── ui/{schedule,students,subjects,reports,backup,more,
│       onboarding,diagnostics,common,navigation,theme}/
├── MainActivity.kt               # @AndroidEntryPoint → AppRoot()
└── TutoringApp.kt                # @HiltAndroidApp, WorkManager config
```

## License

Copyright (C) 2026 seblful

This program is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0** as published by the Free Software Foundation. See the
[LICENSE](LICENSE) file for the full text.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
