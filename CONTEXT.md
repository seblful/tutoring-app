# Project Context

## Overview

A native Android app for a **single** private tutor to manage students, subjects,
schedule, payments and debts. **Offline-first / local-only — no backend.** Russian UI,
Material 3. Currency is **BYN** (Belarusian ruble), shown as e.g. `1500 BYN`.

See `PLANNING.md` for the product goal and the 7 success criteria.

---

## Architecture

MVVM with a **pure, unit-tested domain layer**. Room is the single source of truth;
notifications and calendar are best-effort *mirrors* of it, never the other way round.
Dependency injection is Hilt.

```mermaid
flowchart TB
    UI[Compose screens + ViewModels] --> Repos[Repositories]
    UI --> Domain
    Repos --> Domain[Domain: pure rules]
    Repos --> Room[(Room DB)]
    Repos --> Cal[CalendarSync port]
    Work[WorkManager workers] --> Repos
    Work --> Notif[Notifier]
    Cal -.-> Device[Device calendar → Google sync]
```

### Layers / modules (single `app` module)

| Package | Responsibility |
|---|---|
| `domain/` | Pure Kotlin, no Android deps. `Billing` (incl. the multi-student `balancesFor` aggregate), `ClassRules`, `Reminders`, `Reports`, `CalendarSyncPolicy`, `Scheduling`, the `ReminderScheduler` **port**, and `model/Models.kt`. All JVM-unit-tested. |
| `data/local/` | Room: `@Entity` rows, DAOs, `AppDatabase`. Entities are also `@Serializable` (used by backup). |
| `data/repository/` | Repositories mapping entities ⇄ domain models. `ClassRepository` owns *all* side effects of a class change (calendar mirroring + reminder refresh via the port). |
| `data/backup/` | `BackupData` (whole-DB snapshot) + `BackupManager` (JSON export / transactional replace-all import). |
| `calendar/` | `CalendarSync` **port** + `DeviceCalendarSync` adapter (CalendarContract); `CalendarEvent` owns event display shape. |
| `notifications/` | `Notifier` — reminder channel + posting. |
| `work/` | `MaintenanceWorker` (periodic), `ReminderWorker` (one-shot), `WorkScheduler` (implements `ReminderScheduler`). |
| `format/` | Shared `ru`-locale date/time/money formatting, used by UI **and** non-UI (notifications, view models). |
| `ui/` | Compose screens + `@HiltViewModel`s, `navigation/`, shared `common/` widgets. Includes `onboarding/` (first-run setup), `more/SettingsScreen`, and a `RootViewModel` that gates onboarding vs. the main tabs. |
| `di/` | Hilt modules (`DatabaseModule`; `CalendarModule` and `WorkModule` bind the ports). |

---

## Key design decisions

- **Money is an integer count of minor units (kopecks)** — no floating point. `Money` value class.
- **Price is an hourly rate booked on the class.** Each class carries its own `price`
  (`TutoringClass.price`) = the **hourly rate**, pre-filled from the student's rate for the subject
  when booking and then editable per class. The charge captured *at completion*
  (`ClassRules.applyStatus` → `chargeFor`) is that rate **scaled by the lesson's duration** (a
  90-min lesson at 30/h bills 45), rounded to the nearest kopeck. Using the *booked* rate means a
  later edit to the student's rate never touches existing classes. Reports/income still count only
  completed-class charges.
- **Status state machine** (`ClassRules`): `scheduled → completed | cancelled`, with corrections
  (`completed → cancelled`, `cancelled → scheduled`). Only a cancelled class can return to the
  schedule *via the status menu*. A scheduled class
  **auto-completes once its end time passes** (the app knows only the clock; Option A). A class
  that didn't happen is `cancelled`, which voids the charge — there is no separate "missed"
  status. `ClassRules.availableTransitions` is the single source of truth for the actions menu, so
  it never offers the status a class is already in.
- **Editing is allowed in any status; the charge is recomputed, not preserved.** Editing a class's
  student/subject/price/duration/time is offered for SCHEDULED, COMPLETED and CANCELLED classes.
  Saving routes the edit through `ClassRules.move(edited, newStart, now)` then `ClassRules.rebill(...)`
  (see `ClassEditViewModel.save`): the new start gets the same re-planning as a move (future →
  status SCHEDULED + charge voided, so a class can't be "completed in the future"; past → status
  kept), and `rebill` re-derives the charge from the edited price × duration. Because the charge is
  recomputed (not carried over), editing a completed class's price/duration updates its recognized
  income, and re-attributing it to a new student/subject credits the new owner correctly at the
  current rate — this is what makes editing history safe (the old BUG-9/BUG-10 mis-attribution). The
  resolved class is persisted via `ClassRepository.update` (a dedicated in-place update, **not** `add`)
  so an edit can never accidentally insert a duplicate row and double-count income.
  **Move (reschedule) is also allowed for any status** (`ClassRules.move(c, newStart, now)`). A
  completed class still can't be *deleted* (that would erase recognized income) — cancel it first.
  Classes are **one-off only** (no recurring/weekly series) — a removed v1 feature; the dormant
  `recurringGroupId` column is always null.
- **A student's subjects == their priced subjects (single source of truth).** The `prices` table is
  the one source for "which subjects a student takes": the student-detail subject list is derived
  from it (`StudentDetailViewModel`), and so is the booking dropdown (`Scheduling.assignableSubjects`),
  so the displayed set is always exactly the bookable set — no "price not set" rows, no
  bookable-but-hidden subjects. Adding a subject (`addSubject`) links it **and** sets a price in one
  step; removing it (`removeSubject`) drops both, but is **refused when the student has any class for
  that subject** (a completed class snapshots its charge yet still references a priced subject). The
  `student_subjects` cross-ref table is kept in lock-step purely as a mirror for the backup snapshot.
  `PriceRepository.deletePrice` exists only for this paired removal; the "no classes" guard preserves
  the invariant that a class's subject always has a price.
- **Subjects are deletable only when no class references them** (`SubjectRepository.delete` returns
  `false` otherwise), and **`classes`/`payments` use `ON DELETE RESTRICT`** (not CASCADE) on their
  student/subject parents (DB v5, `MIGRATION_4_5`). So even if some future code path tried to delete a
  student or subject with history, the database itself refuses rather than silently cascade-erasing
  earned income — the protection is structural, not just a repository convention. (`prices`/
  `student_subjects` stay CASCADE: dropping a subject with *no* classes cleanly removes its prices.)
  Students remain archive-only (no delete path at all). Backup import deletes children-first, so
  RESTRICT never blocks a restore.
- **Class mode** (`ClassMode`: in person / remote — «Очно»/«Удалённо»): each student carries a
  `defaultMode`; a new class inherits it and can be overridden per occurrence. Mirrored into the
  calendar event's location. Students also carry a free-text `phone`.
- **Offline-first.** Room is authoritative. Calendar and notifications are best-effort: if the
  permission/calendar is absent they no-op, never blocking or corrupting local data.
- **Settings live in Room** (`SettingsEntity`, single row; `SettingsRepository`): `notificationsEnabled`
  (default true), `calendarSyncEnabled` (default false), `onboardingComplete`, `reminderLeadMinutes`
  (default 60 — drives both the in-app and calendar email reminder), `autoCompleteEnabled` (default
  **false** — billing a lesson the tutor hasn't confirmed could over-state a student's debt, so
  auto-billing is opt-in; when off, finished classes stay scheduled until marked completed),
  `themeMode` (SYSTEM/LIGHT/DARK), `defaultLessonMinutes`
  (default 60 — pre-fills the booking end time), and `currency` (BYN/RUB). Device-local, so **not**
  part of the backup snapshot. The **More → Settings** screen (grouped into Reminders / Calendar /
  Classes / Money / Appearance sections) edits them; theme + currency are read
  at `AppRoot` (currency via the `LocalCurrency` CompositionLocal, so `Money.text()` re-renders on
  change). Schema changes here ship a real Room migration (see `DatabaseModule.MIGRATION_3_4`) so the
  tutor's live data survives upgrades.
- **First-run onboarding** (`ui/onboarding`, gated by `onboardingComplete`): forces adding ≥1 subject,
  then a first student (with subjects **and** prices — a class can only be booked for a priced
  subject). The schedule's add-class button is additionally disabled until both a student and a
  subject exist (`ScheduleViewModel.canAddClass`).
- **Reports cover active students only** (`ReportsViewModel`): archived students' classes, income and
  debts are excluded from every total and breakdown. Because archiving therefore drops a student's
  outstanding debt from the totals, the student-detail screen **warns before archiving a student
  whose balance isn't zero** (`StudentDetailScreen`), so the debt isn't hidden silently.
- **Email reminders via the calendar, not a backend.** A student carries an `email`; when calendar
  sync is on, the class event adds them as an attendee with an email reminder 60 min before
  (`DeviceCalendarSync`), so the calendar account (e.g. Google) does the sending. There is no SMTP
  or backend in the app — automated email an hour before is only possible through the calendar.
- **Calendar sync via `CalendarContract`, not the Google Calendar REST API** *(deviation from
  PLANNING's Stack line)*. Writing events to the device's calendar makes the OS sync them to the
  user's Google account — so there is **no Google Cloud project, OAuth consent screen, or privacy
  policy** to set up, which fits the "no backend" goal. It sits behind the `CalendarSync` port, so
  a REST adapter can be added later without touching the rest of the app. Opt-in via the More tab
  grants **both** `READ_CALENDAR` (the provider query that finds the writable calendar needs read)
  and `WRITE_CALENDAR`; enabling it backfills events for the existing classes.
- **WorkManager** for time-driven behavior: precise one-shot `ReminderWorker`s for upcoming
  classes, plus a periodic `MaintenanceWorker` that auto-completes due classes and re-derives
  reminders (the safety net for changes made while the app was closed). The *decisions*
  (`Reminders.plan`) are pure and tested; WorkManager is just the trigger.
- **Backup = full DB snapshot → JSON via SAF.** Entities are `@Serializable`; ids are preserved so
  import into an empty DB reproduces identical data. Import is one transaction (no partial restore).
- **`java.time` everywhere + core-library desugaring** so it works on `minSdk 24` (java.time is API 26+).
- **Design system** (`ui/theme/`): a curated Material 3 palette (indigo primary + teal accent),
  full light/dark schemes, a modern type scale and rounded `Shapes`. Dynamic color is **off by
  default** so the app keeps a consistent brand (opt-in via `dynamicColor`). Convention: *content*
  lists (schedule, students) use the shared `ContentCard` for a card-based look, while
  *settings-style* lists (More, Subjects) stay as plain `ListItem` rows. Shared visual atoms live in
  `ui/common/Components.kt` (`ContentCard`, `StatusBadge`, `Avatar`).
- **Responsive width.** The app is phone-first but must stay usable on tablets, unfolded foldables
  and landscape. Every screen's top-level scrollable content (scrolling `Column`, `LazyColumn`, or
  settings list) applies `Modifier.constrainedContent()` (`ui/common/Components.kt`), which caps
  content at `MaxContentWidth` (600.dp) and centres it on wide screens while staying full-width on
  phones (a no-op below 600.dp). Vertical fit is handled by every screen scrolling, so short/landscape
  viewports never clip content.

---

## Data flow

ViewModels observe repository `Flow`s (Room → domain models) and expose `StateFlow` UI state.
Writes go ViewModel → repository → Room; the repository then mirrors the change to the calendar
and asks the `ReminderScheduler` port (implemented by `WorkScheduler`) to refresh reminders — so
ViewModels express intent only and can't forget a side effect. Balance/income are derived on the
fly by `Billing` / `Reports` from the observed data — never stored.

---

## Non-goals (v1)

Multi-user/login, cloud sync across devices, web/iOS, in-app payments.

---

## Testing & success criteria

- **JVM unit tests** (`src/test`, run with `testDebugUnitTest`) cover the domain layer and the
  backup serialization format: criteria 1–4 (domain), 5 (move), 6 (format), 7 (reminder planning).
  They also cover the **view models**, **`ClassRepository` side effects** (calendar mirroring +
  reminder refresh), the entity⇄domain **mappers**, and the formatters. View models and repositories
  are tested for real against **in-memory fake DAOs** (`src/test/.../testutil/`: `FakeDaos`, `Fakes`/
  `TestEnv` wiring the real repos onto fakes + fake `CalendarSync`/`ReminderScheduler`), with a
  `MainDispatcherRule` swapping `Dispatchers.Main`. So most logic outside the device-bound layers
  (Room SQL, real calendar/notification I/O, WorkManager triggering) is verified without an emulator.
- **Instrumented tests** (`src/androidTest`, need a device/emulator) cover the Room integration:
  `AutoCompleteTest` (criterion 1 end-to-end), `ClassCalendarSyncTest` (criterion 5: move updates
  the synced event), `BackupRoundTripTest` (criterion 6: export→import on an empty DB).
- Notification *display* and real calendar writes can only be observed on a device.

---

## Tooling & build

- Kotlin 2.1.0, AGP 8.7.3, Gradle 8.9, JDK 21 (Android Studio JBR). compile/target SDK 35, minSdk 24.
- Hilt 2.55 (older Hilt can't read Kotlin 2.1 metadata), Room 2.6.1, WorkManager, Navigation Compose,
  kotlinx-serialization — all via `gradle/libs.versions.toml`.
- Build needs `JAVA_HOME` set to a JDK 21 (e.g. `C:\Program Files\Android\Android Studio\jbr`) and
  `local.properties` pointing at the Android SDK.
- Common commands: `./gradlew :app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`.
```
app/src/main/java/com/seblful/tutoringapp/
├── domain/            # pure rules + ports + model/Models.kt
├── data/{local,repository,backup}/
├── calendar/  notifications/  work/  format/  di/
├── ui/{schedule,students,subjects,reports,backup,more,onboarding,common,navigation,theme}/
├── MainActivity.kt    # @AndroidEntryPoint → AppRoot()
└── TutoringApp.kt      # @HiltAndroidApp, WorkManager config, startup scheduling
```
