# Klas — Tutoring App

A native Android app for a **single private tutor** to manage students, groups, subjects, schedule,
payments and debts. **Offline-first and local-only — no backend, no account, no cloud.** Russian and
English UI (following the phone unless told otherwise), Material 3, money in **BYN**, **RUB**, **USD**
or **EUR**.

> Application ID: `com.seblful.klas` · launcher label: **Klas**

## Features

- **People** — students and groups in one tab, searchable; students also sortable by who owes the most.
- **Homework** — set for a student or a group from its own tab (or a student's or group's card) and attached to their next lesson, which the app works out itself. Swipe it done and it goes to the archive; the lesson's sheet and its reminder say what was set; a send button shares it.
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

- **Kotlin**, **Jetpack Compose** (Material 3), Navigation Compose
- **Room** (single source of truth) · **Hilt** DI · **WorkManager** + `AlarmManager`
- **kotlinx-serialization** (backup) · **kotlinx-datetime** (`java.time` stays only in the Android formatters, desugared)
- AGP (built-in Kotlin) · Gradle · **JDK 21** · **minSdk 24**
- Two modules: `:app` (Android) and `:domain` (Kotlin Multiplatform — the compiler keeps Android and the JDK out of it)

Versions live in `gradle/libs.versions.toml`, SDK levels in `app/build.gradle.kts`.

Kotlin is capped by KSP's supported line (Room and Hilt codegen): stay on 2.3.x until a KSP 2.4.x
exists — see `gradle/libs.versions.toml`.

Architecture is MVVM over a pure, unit-tested domain layer — see **[docs/architecture.md](docs/architecture.md)**.

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
./gradlew :domain:jvmTest               # domain unit tests (billing, scheduling, reminders, reports)
./gradlew :app:testDebugUnitTest        # app unit tests (view models, repositories, mappers, backup)
./gradlew :app:verifyRoborazziDebug     # the app's unit tests, plus every @Preview compared with its golden screenshot
./gradlew :app:recordRoborazziDebug     # re-record the goldens in app/src/test/screenshots after a deliberate UI change
./gradlew :app:lintDebug                # lint (release lint is off: an AGP file-locking bug on Windows, see app/build.gradle.kts)
./gradlew :app:assembleDebugAndroidTest # build instrumented tests (Room, calendar, backup round-trip)

./gradlew ktlintCheck detekt                     # formatting (ktlint) and static analysis (detekt), every module
./gradlew ktlintFormat                           # auto-format: rewrites every file it can fix, baselined ones too
./gradlew ktlintGenerateBaseline detektBaseline  # regenerate both baselines from the code as it stands
```

ktlint's rules are in `.editorconfig` (the IDE formatter reads the same file), detekt's departures from
its defaults in `config/detekt/detekt.yml`. Findings that predate the linters are recorded per module in
`config/ktlint/baseline.xml` and `config/detekt/baseline.xml` and are not reported; anything new fails.
ktlint's baseline is keyed by line and column, so editing above a recorded finding resurfaces it: fix it,
or regenerate the baselines after a refactor that moves a lot of code. `ktlintFormat` touches the whole
tree, so review its diff before keeping it.

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk` and installs without any signing
setup. It carries a `.debug` application id, so it sits beside a release install with its own database.

CI (`.github/workflows/ci.yml`) runs both unit suites with screenshot verification (uploading the diff
images when one fails), `lintDebug`, ktlint, detekt, the debug APK and the *compilation*
of the instrumented tests on every push to `main` and every PR targeting `main` or `develop` (any branch can be run
on demand). Running the instrumented tests themselves needs a connected device
(`./gradlew :app:connectedDebugAndroidTest`) or the emulators of `.github/workflows/device-tests.yml`.

Pushing a `vX.Y.Z` tag runs the release workflow (`.github/workflows/release.yml`): the same suite and the
instrumented tests on emulators, then a signed APK and AAB attached to a GitHub release and uploaded to
Google Play with the notes in `store/whatsnew/`. Dependabot keeps the workflows' actions current.

## Docs

The reasoning behind a rule lives in KDoc next to the code; these docs are the map.

- [Architecture](docs/architecture.md) — where code goes, and which class owns each rule
- [Domain rules](docs/domain-rules.md) — money, lessons, homework, groups, students: what the app enforces and why
- [Calendar and reminders](docs/calendar-and-reminders.md) — what reaches the device calendar and the notification tray
- [Database and backup](docs/database-and-backup.md) — changing the schema or the backup file without losing data
- [UI conventions](docs/ui-conventions.md) — how a screen is built
- [Testing](docs/testing.md) — where a test goes, and what the test setup expects
- [Releasing](docs/releasing.md) — a signed release APK, sideloading it, and the tag-driven release to GitHub and Google Play
- [Store listing](docs/store-listing.md) — regenerating the Play screenshots, feature graphic, icon, text and promo video from the real app

## License

Copyright (C) 2026 seblful

This program is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0** as published by the Free Software Foundation. See the
[LICENSE](LICENSE) file for the full text.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
