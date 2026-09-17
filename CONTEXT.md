# Project Context

**Klas** — a native Android app for a **single** private tutor to manage students, groups, subjects,
schedule, payments and debts. **Offline-first / local-only — no backend.** Russian and English UI,
Material 3. See `PLANNING.md` for the product goal and success criteria.

The launcher label is the Latin **Klas** in both languages: it is a brand, not a word being read.
Renamed from «Репетит» (`com.seblful.repetit`) while unpublished, which is the only reason the
application id could move with it. `TutoringClass` keeps its name: it is the *domain* noun for a
tutoring class, not the app's.

**This file is the map: *what* was decided and *where* it lives.** The **reasoning** for every rule
below sits in the KDoc next to it — that is the place to add or read the argument, so this file can
stay short enough to read before starting work. If a bullet here tempts you into a paragraph, the
paragraph belongs in the KDoc.

---

## Architecture

MVVM with a **pure, unit-tested domain layer**. Room is the single source of truth; the calendar and
notifications are best-effort *mirrors* of it, never the other way round. DI is Hilt. Single `app`
module.

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

| Package | Responsibility |
|---|---|
| `domain/` | Pure Kotlin, no Android deps. Billing, class/group rules, scheduling, `Recurrence`/`Repeat` (what a repeating booking's dates are), calendar layout, `CalendarSyncPolicy`, reminders, `LessonPhase` (where a lesson stands *now* — the derived «Идёт», and the one definition of "finished and unmarked"), `Digests` (the three summaries: when each fires, whether one already due is still owed, whether it is worth sending, and `Arrangement` — what should become of the queue), reports (+ `ReportPeriod`, `ReportCsv`), `PeopleSearch`, `Taught` (+ `Hint`, which of "still to come" and "called off" the stat tile says), `DaySummary`/`NowMarker` (what a day comes to, **which clauses its sentence has** — `parts` — and where "now" falls in it), `Captions` (the names a lesson is captioned with), `Attendance` (what a lesson's roster comes to), `LessonKind`/`LessonFilter`, `AvatarPalette`, `Diagnostics`, `PaymentDate`, models (incl. `Balance` — settled, credit or owed — `Currency`, `AppLanguage` — the two settings that are about *reading* the app rather than about a lesson — and the report-only id types, `model/Ids.kt`), and the `ReminderScheduler`/`DigestScheduler` ports. All JVM-tested. |
| `data/local/` | Room entities, DAOs, `AppDatabase`, `Migrations` (every way *into* the current schema), the `Transactor` port, and `upsertedId` — the one statement of `@Upsert`'s -1-on-update sentinel (the fakes reproduce it, so the fallback branch runs on the JVM too). Entities are also `@Serializable`, for the backup. `SnapshotDao` is the **separate** whole-table seam (see *Two interfaces onto the same tables*). |
| `data/Mappers.kt` | Entities ⇄ domain models. Where a stored enum is parsed back. |
| `data/repository/` | One invariant each, end to end, written in one transaction (see *Ownership*). Display **name ordering** is applied here. |
| `data/backup/` | The **file format**: export, transactional replace-all import, `BackupIntegrity` (what a file must be readable as before any of it is written), `BackupFailure` (which refusal it was) and `BackupRestore` (everything a *restore* means beyond the rows). What tables exist and the order they may be emptied and filled in is `SnapshotDao`'s. |
| `calendar/` | `CalendarSync` + `CalendarPermission` ports and a `CalendarContract` adapter — which **takes the permission port** rather than checking for itself, and whose two decisions are pure and beside it: `CalendarChoice.preferred` (which calendar may be written to, and in what order of preference) and `guestAction` (add the student, withdraw them, or leave the event alone). |
| `notifications/` | `Notifier`, `NotificationPermission` and `ExactAlarmPermission` ports + Android adapters, `DueReminder` and `DueDigest` (what each notification says, and whether it is still owed), `TrayContent` (**how much of it fits** — the listing digests' cap and their «+N»), `ReminderActions`/`TrayNavigation` (what a tray entry's buttons and taps do) and `ReminderAlarms`/`ReminderReceiver`/`ReminderActionReceiver` — reminders are **alarms**, not deferrable work. |
| `work/` | WorkManager's half: the periodic `MaintenanceWorker`, the two `DigestWorkers`, `WorkScheduler`, `BootReceiver` (alarms die on reboot), and `Maintenance` — what an upkeep pass decides, kept out of the worker. |
| `diagnostics/` | `CrashLogger` over `CrashLogStore`/`DeviceInfo` ports; the pure half is `domain/Diagnostics`. Viewed and exported from `ui/diagnostics`. |
| `storage/` | `DocumentStore` port + SAF adapter over an opaque `DocumentRef`, and `DocumentSharing` + its `ACTION_SEND`/`FileProvider` adapter — so no view model touches a `Uri`, an `Intent` or a `Context`. Two ports because the acts differ: one writes where the tutor pointed, the other hands a file to another app. |
| `format/` | Date/time/money formatting in the app's chosen language (with `LocalDate`/`LocalTime` faces, so no caller restates the zone), `AppLocale` — the one published answer to *which* language, and the decimal separator that goes with it — `MoneyField` (what a money input is — each `Kind` carries its own zero rule), `NameOrder` (the one definition of how names are listed), `bulletJoin` (the app's one « · » separator, shared by a day's summary and a tray entry), and `DayText` — a **wording table** over `DaySummary.parts` and the only file here taking a `Resources`. |
| `ui/` | Compose screens + `@HiltViewModel`s, `navigation/` (routes, tab items, screen transitions), shared `common/` atoms, **one concern per file** — the screen shell (`KlasScreen`), the spacing scale (`Spacing`) and the three fixed figures beside it (`Metrics`), `Loadable`/`Skeleton` (the loaded-yet distinction), `Surfaces`, `Fields`, `Dialogs`, `Avatars`, `Badges`, `EmptyStates`, `DomainText`, `Segments` (`SegmentedChoice` and the `ChoiceRow` a setting is asked with), `Currency`/`Language` (the words for the two stored choices), `LocalizedResources` (the whole composition's language), `HeroCard`, `PinnedHeader` (and `StickyTabRow` over it) — plus `onboarding/` and `RootViewModel` gating onboarding vs. the tabs. **Settings and About live in `ui/more/`.** The theme (`ui/theme/`) owns the two colour schemes, `KlasColors`, the type scale and `dynamicColorAvailable`. |
| `preview/` (debug only) | `@Preview`s of the shared `ui/common/` vocabulary and the `@KlasPreview` multipreview that renders each in light, dark and 1.5× text. In `src/debug/` so neither the previews nor their sample data reach a release APK. |
| `di/` | The database module, the `@DebugBuild` flag, `@ApplicationScope`/`@Computation` (`CoroutinesModule`), the `Clock` and `@FirstDayOfWeek` (`TimeModule`) — which every defaulted `now` in the engine reads (`Maintenance`, `DueReminder`, `RateHistoryRepository`, `CrashLogger`), so the tested path is the shipped one — and `@DeviceLocales` (`LocaleModule`), the phone's own list, which is what «follow the system» and a fresh install's currency are resolved against. Other Hilt modules live beside their adapters. |

### Ownership (one invariant, one place)

- `ClassRepository` — **all** side effects of a class change (calendar mirror + reminder refresh),
  and the **one-tutor-one-lesson refusal itself**: every write that would (re)occupy time checks the
  hour and returns `LessonWrite.Written | HourTaken(clash)` rather than trusting its callers to have
  asked (three used to, each with its own ignoring set; the fourth way in shipped as a double
  booking). **The exclusion set is `ClassSession.ids`', not a caller's** — `holdingHour(session, …)`
  derives it, and `observeConflicts` takes the lesson being edited rather than a set of ids, so the last
  piece of that invariant living outside the module stopped having four hand-written copies. Every write takes a whole **lesson** (`ClassSession`), so there is no single-class path
  to drift. **`bookSeries` is the one write that reports instead of refusing** (`SeriesWrite`) — it
  answers about a term rather than an hour, and one taken Monday must not turn down the other eleven.
- `CalendarMirror` — what a mirrored event *shows*, and every table that decides it. A title is built
  from the student's, subject's and group's name and the guest from the student's email, so **four**
  tables can invalidate an event, not just `classes`; a caller that changes one of them asks for a
  refresh instead of knowing which table it touched. Also owns the sync on/off backfill and withdrawal.
- `EnrollmentRepository` — the subject⇄price invariant, and where prices are read.
- `GroupRepository` — the only writer of group membership; a group is saved with its exact roster,
  and `save` hands back `Saved(id, dropped)` — who the subject rule left out — so the form reports
  what the write did instead of predicting it with a second intersection.
- `PaymentRepository` — a payment's own date, and correcting one in place. **The instant is derived here**
  (`record`/`correct` take the *day* the tutor named and apply `PaymentDate` against the injected clock), so
  no caller can write a payment stamped with a time nobody chose. It used to be a pure pass-through — five
  methods, five one-line delegates, a `PaymentDao` of the same shape and no behavioural test — while the rule
  it was documented as owning was applied by the view model on the way in, twice. `restore` is the one write
  that does **not** derive an instant: an undo reproduces a fact rather than recording one.
- `RateHistoryRepository` — what a *rate change* is, since two repositories set rates.
- `SettingsRepository` — one `update { it.copy(...) }` rather than a setter per field, serialized —
  returning `SettingsChange`, which classifies what the move owes the world (calendar re-earned,
  reminder plan re-derived, digests re-planned, finished lessons settled); the settings screen's one mutator
  discharges it,
  so no setting can lose its tail by lacking a named method. Two already had: the reminder lead left
  mirrored events stale, and auto-completion did nothing at all until something unrelated ran the
  upkeep pass — so confirming one old lesson by hand then credited every other one at once.
  It also owns the two answers the *row* cannot give on its own: what a phone that has written nothing sees
  (`Currency.preferredFor`, off `@DeviceLocales`), and the **display locale**, published on every read and
  write so nothing downstream can be handed a language and format a date in the previous one.
- `StudentRepository` — the whole act of **archiving**, which is more than a flag: a retired student's
  still-planned lessons are called off first, through `ClassRepository`.
- `SubjectRepository` — a subject is deletable only while nothing references it.

**"Is anything still taught here?" is answered by whoever owns the answer.** `ClassRepository.anyFor*`
and `GroupRepository.anyForSubject` are the referenced-by guards behind every refusal to delete or
unenroll. Three repositories used to count rows in `ClassDao` themselves for a single yes-or-no, which
put the reason for the refusal in the module doing the deleting rather than the one that knows it.

**A write that spans tables is one transaction, in all of them**, in three fixed phases: pure rules →
every row inside `Transactor.transaction` → calendar and reminders *after* the commit. What could
half-happen is exactly the invariant the class exists to hold, so a failure partway is not a lost
edit but a broken one. Reads that only *decide* whether to write stay outside the transaction.

---

## Key design decisions

### Money and billing

- **Money is integer minor units** (kopecks) — no floating point — and **bounded** by `Money.MAX`
  (1 000 000,00), because the arithmetic downstream multiplies. Enforced at both ways in: the typed
  input gate (`parseMoneyOrNull`) and a restored file (`BackupIntegrity`).
- **Currency is a display setting, not a fact about the data**: the `Currency` enum (BYN, RUB, USD,
  EUR), chosen in Settings and in first-run setup, provided through `LocalCurrency` and printed by
  `Money.text()`. Changing it **re-labels** stored amounts rather than converting them, so the switch
  confirms first — in Settings, where there is history to re-label, and not during setup, where there is
  none. The stored word is parsed totally (`Currency.parse`, falling back to BYN — a label carries no
  money) and sits in `StoredEnumNamesTest` like every other persisted constant name.
  - **All four print as their ISO code, never a symbol.** The same argument that keeps them
    un-converted: the app is captioning a number it was handed, and «$» *claims* a currency where «BYN»
    only names one — the same amount would read as US dollars to somebody who meant Australian ones. A
    code is also unambiguous in both languages.
  - **A fresh install is captioned in the phone's currency** (`Currency.preferredFor`, off the locale's
    **region** — a Minsk tutor with an English phone is paid in rubles). It is substituted where a
    settings row that *does not exist yet* is mapped to defaults (`SettingsRepository`), not seeded by a
    setup step: a row that has never been written is exactly "the tutor has chosen nothing", so the
    answer holds on every path in — including skipping setup — and the first write persists it.
- **The decimal separator is the app language's, not the currency's** (`AppLocale.decimalSeparator`):
  `1500,50` in Russian and `1500.50` in English, whichever currency captions it. `Money.amountText` takes
  it as a parameter with **no default**, for the reason `hour12` has none — a default is how the wrong one
  gets picked everywhere at once — and `format/` supplies the app's answer to every caller.
  `parseMoneyOrNull` deliberately accepts either, so a field the app pre-filled before the language moved
  still reads instead of turning red.
- **Price is an hourly rate booked per class**, pre-filled from the student's rate for the subject
  (or the group's own), editable per lesson. `charge = booked rate × duration`, so later re-pricing
  never touches existing classes.
- **Balances and income are derived**, never stored.
- **Status**: `scheduled → completed | cancelled`, with corrections. Auto-completes once the end time
  passes (opt-in). Cancelling voids the charge — there is no "missed" status. **«Идёт» is not a fourth
  one** — see *A lesson's phase is derived, never stored*.
- **Reports cover active students only**, so archiving hides a debt — the app warns first.
- **A report is read inside a period** (`domain/ReportPeriod`): a month, a year, or all time, opening
  on the **year in progress**. **Debts are the deliberate exception** — a balance spans a student's
  whole history, and clipping charges to a year while payments stay whole would invent credit.
- **`Reports.of` is the only way in.** It takes the database's lists and a period and returns the whole
  `Report`: which classes count (active students, then the period), how each breakdown is ordered, and
  which sections are worth showing — a single kind of teaching is not a split, an empty month is a gap in
  the chart but not a row in the list. Those decisions used to be spread across the view model and a
  composable, none of them where a report is tested. The aggregations stay public and stay tested one
  rule at a time; `ReportsSurfaceTest` stops the app reaching past `of` to reassemble a report itself.
  The screen supplies only the subject and group **names**, keyed by the typed ids of `model/Ids.kt`.

### The booking form is a pure module

- **`BookingForm.change(intent, context)` is the whole mutable surface** of the booking form
  (`ui/schedule/BookingForm.kt`). Every rule about how the form moves lives there: the
  target/student/group/subject cascade that clears what belonged to the previous choice, the default
  length a start brings with it, the roster ticks. It needs no dispatcher, no `SavedStateHandle` and no
  repository, so `BookingFormTest` exercises it directly.
- **A change returns the reads it wants, as data** (`Lookup`), and the answers come back through the same
  `change` as their own intents. The view model runs the lookups and decides nothing. That is what puts
  the **three staleness checks** — a group's rate arriving after the tutor picked another group, a price
  after the subject changed, the only-subject answer after the target moved — in the module with the rules
  they protect. They were `if` guards inside `viewModelScope.launch` before, which is to say untested.
- **Whether a change is the tutor's work is a property of the intent** (`BookingIntent.isEdit`, and
  `AsSetup` for what the app does on their behalf). Four `selectX`/`applyX` pairs used to encode it by
  existing twice and differing in one line. An intent that moves nothing no longer marks the form
  unsaved — the old pair disagreed about that, and re-picking the target already chosen made back ask
  about work nobody did.
- Named `change`, not `apply`: every type already has `apply` from the standard library, and
  `form.apply { … }` would silently be the scope function.

### The view-model tail, named once

- **A screen's state says whether it has arrived** (`ui/common/Loadable.kt`). Every list flow started at
  an empty collection, which is the same value an empty database produces — so for the frame or two
  before Room's first emission each screen drew its *empty state*: «Нет занятий» over the tutor's real
  morning, "add your first student" to a tutor with forty. `Loadable<T>` is `Loading | Ready(value)`,
  the initial value of every screen flow that had a placeholder, and `ready()` is how a flow marks its
  emissions. Empty is now only ever said about data that was read. Screens draw `Loading` as a
  **skeleton shaped like the content** (`ui/common/Skeleton.kt`), never a centred spinner — a spinner
  reflows the whole screen on the first real frame, which is the jump it exists to prevent.
  - **The lists were only the visible half.** A default value is not neutral wherever the screen is
    *about a record*: `AppSettings()` is another tutor's settings, so «Настройки» opened with the switches
    at their defaults and then flipped them; a blank `ClassForm`/group `Form` states that the lesson has no
    student and the group no name, in the error red, over one that has both — so both edit forms wait
    (`LoadingFormScreen`), while **adding** anything is `Ready` at once, because a blank form is what
    adding *is*. A **form's pickers are part of its own readiness**, not separate state: the fields read
    the names they show out of them, so `ClassEditViewModel` waits for the lists as well as the lesson.
  - **What waits is decided per fact, not per screen.** Backup withholds only the «Последняя копия» card —
    the export button works without it — the student form only its subject checklist, and a calendar cell
    only its tallies (an empty cell *is* how the month says «свободно», so one dim bar stands in). Things
    computable without the database — a month grid, a week's dates, a duration chip set — never wait.
- **`derive` and `hold` end every screen flow** (`ui/common/ScreenState.kt`). Twenty-six flows across
  thirteen view models spelled the same `flowOn(computation).stateIn(viewModelScope,
  WhileSubscribed(5_000), initial)`, so the grace period — which decides whether rotating the device
  recomputes every class against every payment — was a bare literal in twenty-six places. Two functions
  rather than one with an optional dispatcher, so which one a flow uses says whether it computes
  anything.
- **`ShowMessagesFor` ends every screen's one-shot events**, and `rememberMessages` is the only thing
  that builds a `SnackbarHostState`. Three of eleven screens had drifted onto a raw host and lost the
  dismiss-what-is-up rule that keeps a second failure from arriving after the tutor has moved on. A
  screen that does more per event than show a string (an undo action, a navigation) keeps its own
  collector.
- **The guarded-write tail is named `write`** where a screen's actions outlive it:
  `ScheduleViewModel.write` and `StudentDetailViewModel.write` (application scope +
  `runCatchingCancellable` + one failure report, with the success outcome as a parameter). The
  student card had nine hand-spelled copies, and none of the money path's had a failure test.
- `ScreenStateSurfaceTest` pins both: neither tail can be written out by hand again.

### A screen draws, it does not decide

- **Every branch a screen takes is a value the module behind it returned.** `ArchiveIntent`,
  `SaveIntent`, `DeleteWarning` (its four **cases**, not the two facts they are combined from — see below),
  `ScheduleEmpty`, `NotificationsIntent`, `RootRoute`, `ThemeChoice`,
  `OnboardingViewModel.restoreNeedsConfirming`, `ReportCsv.linesFor`, `PaymentDate.instantFor` (and
  `.today`), `ScheduleRow.statusChoices`/`canSwipeComplete`/`recedes`, `Reports.Line.share`,
  `Trend.shareOf`, `Balance`, `Taught.Totals.hint`, `ClassSession.attendance` and the three picker
  defaults on `ClassEditViewModel` are the named answers; the screen holds only the words and the layout.
  - **The last four went the same way as the first, and for the same reason.** A **balance** was read
    straight off `Money.isZero`/`isNegative` by four askers — a sentence, a colour, a card's hero label and
    the archive warning — with the flip that turns a negative into «Переплата» written out twice. `Balance`
    (Settled | Credit | Owed) decides it once and carries an already-positive amount, which is also what
    makes the hero's «Долг 0» for a settled student a *stated* decision rather than the by-product of a
    missing third arm. `Taught.Totals.hint` is "upcoming beats cancelled, and neither prints a zero" — the
    one rule the student and group cards genuinely share, and the one neither could test.
    `ClassSession.attendance` is «N из M» versus the whole roster, decided inside a `LazyColumn` item
    lambda before. A `@Composable` can be exercised
  only on a device, so a decision that lives in one is a decision with no test — and the eight sites
  these replaced included which rows the CSV export contains and what instant a payment is stored at.
- **`PaymentDate` was the app's one exception to the injected `Clock`.** It called `LocalDate.now()`
  and `Instant.now()` directly, in a composable, on the path that records money. Two more had the same
  shape and are gone the same way: «Перенести» combined its picked date and time into an instant inside a
  composable, so `ScheduleViewModel.moveTo(session, day, time)` takes the two the tutor chose and the zone
  is the injected clock's; and `ReportsViewModel` read `ZoneId.systemDefault()` for the period anchor, on
  the screen where a month boundary decides the money — which also meant its test asserted the opening
  period by re-running the production expression, and a year-boundary report could not be written at all.
  `format/Formatters` still reads the ambient zone deliberately: rendering an instant on this phone has one
  possible answer, unlike `hour12`, which is why that one has no default.
- **A named answer says which case it is, not the facts it was picked from.** `DeleteWarning` published
  `billed` and `group` and left the four-armed `when` combining them in the composable — so what had moved
  out was two extractions, not the *picking*, which is the part that can be wrong and the part no test could
  reach. And "why is this schedule empty" was decided twice, in two composables: the day and month views
  weighed three cases, the week view two, and the week had never grown the no-students arm — so a tutor with
  nothing set up met «Добавить ученика» on «День» and a dead end on «Неделя».
- **The condition that enables a save is named `canSave`, on a `LeavableForm`.** `leaveIntent` takes the
  form rather than two booleans: with booleans, one screen passed its full save condition and two passed
  only field validity, and nothing said which the shared rule meant.

### Two interfaces onto the same tables

- **`SnapshotDao` is the whole-database seam, and the only one.** Backup and restore need what nothing
  else does: whole tables, ids preserved, no rule applied. Those 25 methods used to sit on the same
  eight DAOs the repositories use, so every repository holding an invariant was also handed the
  `deleteAll()` that voids it. Its public surface is `read`, `insert` and `replace`; the per-table
  methods are `protected`, because both foreign-key orders — children before parents out, parents
  before children in — belong to the schema and are stated once, there. `WholeTableWritesTest` keeps
  whole-table deletes out of `Daos.kt` and pins that every table read is also emptied.
- **`classes` and `student_groups` are read whole in both**, and that duplicate SQL is deliberate: a
  repository reading every class maps it to the domain and applies a rule, and must not be reachable
  from the wipe next door.
- **The backup *file format* is `BackupData`, a separate type from `DatabaseSnapshot`** though they hold
  the same nine lists. `BackupData`'s field names are JSON keys in files already on somebody's phone, so
  they may never be renamed; the snapshot follows the schema.

### Reading back what was stored

- **An enum's constant name is a persisted value** (`classes.status`, the three `mode` columns, two
  SQL literals, three column defaults), so renaming a constant is a migration — and one that fails
  silently. `StoredEnumNamesTest` is the acknowledgement list.
- **Storage is parsed with a total function, never `valueOf`** (`ClassStatus.parse`, `ClassMode.parse`),
  because the read path is inside a `Flow` where a throw reports nothing. An unreadable **mode** falls
  back to `IN_PERSON`; an unreadable **status** fails loudly, because it carries money.
- **Ids carry a type in the report breakdowns, and only there** (`domain/model/Ids.kt`), where three
  identically shaped breakdowns are paired by hand with three identically shaped name tables.
  Elsewhere an id is a plain `Long`.

### Lessons

- **A lesson lasts a positive number of minutes**, insisted on by `Scheduling.Slot` — a zero-length
  slot made the overlap check fail *open*. Read paths meeting a stored row that breaks it
  (`Scheduling.conflicts`) skip that row rather than throw.
- **A lesson is a row, and who was in it is another** (`lessons` + `attendance`, joined by the `classes`
  view — `data/local/Entities.kt`). The lesson holds the hour, the subject and **one status**; each
  student's row holds their attendance, their rate and their charge. `TutoringClass` is the two of them
  read together, which is what every rule above storage still speaks in.
  - One enum used to answer both "did this hour happen" and "was this student in it", once per member. So
    a group lesson had no status of its own (hence the «Разные» badge, then a derivation), a no-show could
    only be recorded by cancelling that student's class — which also stopped charging them — and
    "called off for everyone" was the same rows as "nobody came". `Attended.ABSENT_BILLED` is the case
    none of it could express: absent, and charged anyway.
  - Only an **explicit absence** stops a charge. `EXPECTED` means nothing was recorded, and for a lesson
    marked taught the app has always assumed it happened for everyone in it — it can see the clock, not
    the room. So «Проведено» still bills the whole room in one tap.
  - What made rows one lesson used to be a `(groupId, start)` pair no schema could hold: edit one member's
    time and the lesson silently became two, each blocking the other's hour. It is a foreign key now, and
    a move is one row's update.
- **A lesson's phase is derived, never stored** (`domain/LessonPhase`): `UPCOMING | IN_PROGRESS | OVERDUE
  | SETTLED`, from the stored status and the injected clock. It is what the schedule badges «Идёт» with,
  and it is deliberately **not** a fourth `LessonStatus`. A status is something the tutor decides;
  "the lesson is on right now" is something the clock knows, and storing it would need a writer at each
  start — an alarm, or the upkeep pass — which can only ever be *late*: a phone switched off over the
  lesson, a Doze window, a boot mid-hour, and the row says «В плане» while the tutor is in the room, or
  «Идёт» the next morning. Derived, it cannot be wrong and cannot get stuck, and `LessonStatus`, its
  transition table, the billing rule, the backup format and `StoredEnumNamesTest` are all untouched.
  - **`OVERDUE` is the same sentence auto-completion was already saying.** "Finished, and still
    scheduled" decides both `ClassRules.shouldAutoComplete` and what the evening digest counts, and two
    hand-written boundary comparisons could disagree by an instant — which would mean the notification
    naming lessons the upkeep pass had already settled, or staying silent about ones it had not. The
    half-open `[start, end)` is `Scheduling.Slot`'s, so back-to-back lessons are never both running.
  - **Cheap by construction.** The schedule publishes the *running lesson ids* once a minute
    (`ScheduleViewModel.running`), not a phase per row: a `StateFlow` conflates an equal set, so an hour
    of ticks costs sixty comparisons and no recomposition — the argument `NowMarker`'s index already
    makes. A **set** and not one id, because a restored backup is reproduced as given and is not checked
    for overlaps, so two rows can overlap in a database this app did not write.
  - The badge shows it and nothing else does. `OVERDUE` reads exactly as `UPCOMING` does on the card —
    such a lesson *is* still scheduled, and a fourth word there would be a state the tutor never set;
    what it is owed is a nudge at the end of the day. «Идёт» is the one place `KlasColors.live` (amber)
    is used, every other accent being already spoken for — see that colour's own note.
- **A lesson carries a note** (`lessons.notes`, v7): what was covered, what was set for home. **The
  lesson's**, like its status — one note for one hour, shared by every class of it, because it describes
  the room rather than a person in it. So a joiner added to a group lesson gets the lesson's note
  (`GroupRules.rosterChange`), or the `classes` view would hand one lesson's rows two different answers.
  Blank is "nothing written"; there is no null to tell apart from it.
  - **It rides the ordinary edit** (`ClassEdit`/`SessionEdit`) rather than having a writer of its own, so
    it cannot bypass the rebill that an edit owes. A run of bookings copies it to every occurrence and
    each is editable afterwards, like every other field a series copies.
  - **It is read where the tutor already is.** The form is where a note is *added*; the schedule's
    actions sheet is where it is *checked*, under the status, selectable and capped at four lines — a
    homework line locked inside the edit form would cost the same two taps to read as to change, which is
    what makes a written note not worth writing. Shown only when there is one. The field itself has
    **no placeholder**: an example sentence in an empty note reads as text already written.
  - **It is also the calendar event's description**, so it is legible from outside the app — which is
    what `CalendarSyncPolicy.Action.REFRESH` exists for, a note being written after the lesson is over.
- **A lesson is the unit of change.** Every write takes a `ClassSession`: a group lesson's *n* classes,
  or one student's class as a session of one. Re-attributing a class is expressible only for a
  session of one. The one exception is `setAttendance`, which is about one person rather than the hour.
  - **An edit to a lesson is one value, and `GroupRules.lessonEdit` composes it** (`LessonEdit`): the
    lesson row's own new fields, plus what becomes of every member. The three rules it is made of used to be
    assembled at the one call site that needs them, and the lesson's own fields read out of the classes that
    were *staying* — so replacing a roster **whole** left none to read them from, and the save wrote the new
    members while silently keeping the old hour, with the calendar already mirrored at the new one and the
    joiners re-billed from the new length against the stored old one. `lesson` is a field of the returned
    value, which is what makes that unrepresentable rather than fixed.
- **One tutor, one lesson at a time.** An overlapping slot is refused, not warned about. Back-to-back
  is fine, a cancelled class gives its slot back, and the lesson being edited is excluded by every one
  of its class ids. Booking in the **past is allowed**. The rule is pure (`Scheduling.conflicts`); the
  **enforcement is the repository's** — `book`/`apply`/`move`/`setStatus` return
  `LessonWrite.HourTaken(clash)` for a taken hour, so every way in is checked by the module that
  writes, un-cancelling included. The view models own only the *wording*: the clash arrives as data
  and is reported through `timeConflict` (schedule) or `SAVE_REFUSED_TIME_TAKEN` (form).
- **Editing is allowed in any status and the charge is recomputed** in place, so income cannot be
  double-counted. Deleting is allowed in any status; deleting a completed lesson removes its income
  and the UI warns.
- **A repeating booking writes lessons, not a series** (`domain/Recurrence`, `Repeat`,
  `ClassRepository.bookSeries`). «Повторять» on the booking form materialises the run — twelve ordinary
  rows, each independently movable, cancellable and deletable — and nothing afterwards remembers they
  were arranged together. So there is still no such thing as a recurring series in the data: what exists
  is a booking *act*. The alternative, a `series` table expanded on read, would have made every read path
  learn to expand it (`Scheduling.conflicts`, `CalendarLayout.dayLoads`, `Reports`, `Digests`,
  `Reminders`, the mirror, the backup format) and given the exceptions a real schedule is made of — "skip
  the third", "the fifth moved" — a table of their own, against both *Room is the single source of truth*
  and *a lesson is the unit of change*. The cost is no "cancel the rest of the series" in one tap; the
  case that would be reached for is already `cancelScheduledFor`, which archiving a student runs.
  - **Every occurrence is the same weekday at the same wall clock**, anchored on the lesson filled in —
    Monday 16:00 *and* Thursday 18:00 is two bookings. That is what keeps the form from growing a
    weekday grid, and it buys a guarantee: occurrences are ≥ 1 week apart and a lesson is ≤ 23 h 59 m
    (`DurationPickerModal` bounds both fields), so a run cannot collide with **itself** and `bookSeries`
    checks only the schedule as it stands. The hour is held through `ZoneId`, so a term through a
    daylight-saving change stays at four o'clock rather than keeping its interval — the promise
    `Digests` makes for firing at nine.
  - **A taken hour is reported, not refused** (`SeriesWrite`). Over three months a holiday or an
    already-booked evening is the normal case: refusing the whole term for one Monday is unusable and
    skipping quietly is worse, so every free hour is booked and the write **says what it did** — the
    shape `GroupRepository.Saved(id, dropped)` uses, for the same reason. Counted in **lessons**. The
    three outcomes are three different things to say: all of it is an ordinary save, none of it is a
    refusal (worded for the one time on the form, or for the run), and *some* of it holds the screen on a
    dialog naming the skipped **dates** — a snackbar would be raised on a form that is navigating away,
    and finding those Mondays is the whole of what the tutor now has to do.
  - The run is **one write**: one read over the union of the occurrences' windows, one transaction, one
    calendar batch, one reminder refresh. A loop over `book` would re-derive the whole reminder plan
    twelve times.

### Groups

- **A group prices its lessons; the students still owe for them.** The group carries its own hourly
  rate and a lesson writes one class per member *at that one rate*. Balances, debts and reports stay
  strictly per student. A new group's rate is pre-filled from settings (0 = "not set"); a rate is
  required but may be 0.
- **A lesson has one rate.** Only a lesson restored from a pre-v3 `BackupData` can hold differing
  member rates; the form then offers the group's current rate.
- **A group is taught one subject, fixed on the group.** Only students priced for it can be members —
  the roster is intersected on save, and the save **says who it dropped** (`GroupRepository.Saved`),
  so the form's «not priced» notice reports the write instead of predicting it. The column is **nullable** for a group restored from a pre-v4
  backup, and until the tutor picks one; such a group keeps the looser per-lesson behaviour.
- **Who attends one lesson is separate from who is on the roster**, and is correctable afterwards, in
  any status. An unticked member has no class at all (not a cancelled one); ticking one writes a class
  at the lesson's own terms and **the status the lesson is in**. Dropping a class that already billed
  asks first. An old lesson offers the group as it is now but ticks only its own members.
- **A session is a lesson's classes**, grouped by its id. Its status is the lesson's, read rather than
  derived; who missed it is each member's `attended`. Badges, tallies and the sheet's segmented control
  all show the lesson's own status, and none of them reports on the roster.
- **A lesson is captioned by one module** (`domain/Captions`, built per emission from the whole-table
  lists and asked per lesson). A lesson is stored as ids, and the rule that turns them back into words is
  small but real: **a group lesson is titled by its group and an individual one by its student** — the
  difference between «11 «А», 18:00» and «Иван, 18:00» on the busiest screen in the app. Three view models
  did it for themselves, two with `associateBy` maps and one with a linear scan per emission; on the
  schedule it was private to the view model, so it was reachable only through that view model's 1,352-line
  test. `ScheduleRow.of` is the whole of turning a lesson into a drawable row now. An unknown name comes
  back **blank** rather than absent, because a row with a hole in it is worse than one with a gap — except
  for a group, where null is a real and different answer from "a group whose name I do not know".
- **A group earns a report line of its own** (`Reports.incomeByGroup`) — the only line that answers
  whether running the group is worth the evening.
- Groups are **archive-only once they have lessons**. A group lesson mirrors as **one calendar event
  per member**; in-app **reminders are per lesson**.

### Students and subjects

- **A student's subjects == their priced subjects** — for *individual* lessons. Adding links and
  prices in one step; removing is refused while any class exists for that subject. Group lessons sit
  outside this.
- **Class mode** (in person / remote): a student default, overridable per class, mirrored into the
  calendar event's location.

### History and data safety

- **History is structurally protected.** `classes` and `payments` use `ON DELETE RESTRICT` on their
  parents. Students are archive-only; subjects and groups are deletable only while unreferenced.
- **A rate is current state; its past is a log** (`price_history`), one table for both a student's
  price and a group's rate, discriminated once into the sealed `RateOwner`. Two nulls carry meaning:
  no previous rate = the history starts here; no date = the rate predates the log. An **unchanged
  rate logs nothing**, and un-enrolling a subject forgets its log.

### Storage, migrations, backup

- **Offline-first.** Room is authoritative; the calendar and notifications no-op when their permission
  or calendar is absent, never blocking or corrupting local data.
- **Settings live in Room** (single row) and are **device-local** — deliberately not backed up. Writes
  go through one `update {}` **under a `Mutex`**, because the row is written whole.
- **The last export is a receipt, and it lives in that row** (`AppSettings.lastExportAt`). Not a
  preference, but the same kind of fact: device-local, and outside the snapshot on purpose — a backup
  that carried it would have last Tuesday's file claim that the phone restoring it exported something
  last Tuesday. It is **written after the file and only if the file was written**, in its own guard, so a
  stamp can neither be saved for a failed export nor turn a successful one into a reported error.
- **The platform's own backup is off** (`allowBackup="false"` + `res/xml/data_extraction_rules.xml`):
  Auto Backup would have copied `klas.db` — every student's name, phone and email — to Google's
  servers, without a WAL checkpoint, and restored it without `BackupRestore`. The app's own JSON
  export is how data moves between devices, and the export screen says the file is unencrypted PII.
- **Exactly one directory is reachable from outside the app** (`res/xml/file_paths.xml`, the other half of
  the same argument): the `FileProvider` behind the report share sheet is scoped to `cache/shared` and
  nothing else, is `exported="false"`, and hands out only the per-file read grant that travels with an
  `ACTION_SEND` intent and dies with it. A provider pointing at `.` would have made `klas.db` requestable
  by authority alone.
- **The database is at v7 and the baseline is v5** (`AppDatabase.BASELINE_VERSION`) — the oldest version
  any `klas.db` can be at, so `data/local/Migrations.kt` holds exactly two steps. That file was deleted once
  already: a chain of four, v1 → v5, squashed before the first release because every step of it was dead code.
  `com.seblful.klas` v0.2.0 (`versionCode` 3) declared schema **v1** and reached nobody — it is a tag, not a
  shipped artifact; v2–v5 were all written after it, so no device ever held an intermediate version; and the
  one database holding real students, the author's own `.debug` install, was already at v5. The rename is
  why none of it was ever needed: `com.seblful.repetit` is a **different application id**, which Android
  never upgrades from, so its `tutoring.db` is unreachable whatever version it is at. The only bridge out of
  the old app is the **JSON export**, which is `BackupData`'s job and not a migration's — which is why
  `splitIntoLessons` stays (a file on somebody's disk outlives the schema it came from) while its SQL twin
  went.
- **The baseline is no longer free: `0.3.0` (`versionCode` 4) is the v5 build that shipped.** It is now the
  version in tutors' hands, so the v6 bump that added `settings.language` duly shipped `MIGRATION_5_6` from
  it — **the app's first real migration**, running on phones that hold a tutor's students. The squash was
  the one chance to delete a chain, and it has been spent. `MigrationChainTest` reads the constant and fails
  a bump that does not ship a migration, or one not passed to `addMigrations` — verified by bumping the
  version and watching it fail, which is the only way to know a tripwire is one. Raising the baseline is a
  promise that no database below it exists **anywhere**, which after a release means every install has been
  upgraded past it: not a thing to assume, and not a thing v6 changes.
  - **The unit tripwire proves the step is *there*; a device proves it *works*.** So the exported schema
    JSONs are shipped as `androidTest` assets again (`app/build.gradle.kts`) and
    `SettingsLanguageMigrationTest` stands a v5 database up under `MigrationTestHelper` — in both of v5's
    real states, a populated settings row and no row at all, since an `ADD COLUMN` with the wrong `DEFAULT`
    hides on an empty table. It asserts the row's *other* columns too: a migration that dropped and
    recreated the table would validate against the schema perfectly.
  - **v7 (`MIGRATION_6_7`) is the second, and the first to touch a table with history in it**:
    `lessons.notes`, plus the evening digest's two settings columns. `LessonNotesMigrationTest` is its
    device half and adds the check a settings migration does not need — that the **`classes` view** comes
    back projecting the new column. Room drops and recreates its views after the migrations run, from the
    `@DatabaseView` declaration, so every read of a lesson in the app depends on something that is true by
    construction right up until it is not.
- **The destructive fallback is debug-only.** A release build **refuses to open** a database it cannot
  migrate: a crash leaves every row on disk for the next build, and is recoverable in the way a wipe
  is not.
- **Every schema change past the baseline bumps the version and ships a migration**, with an entity's
  `defaultValue` and the migration's `DEFAULT` kept in step; `MigrationChainTest` makes that a rule rather
  than a habit. **`BackupData.version` (currently 7) is the other half of the compatibility story, and the
  two are now independent** — it is the only bridge out of `com.seblful.repetit`, and the reason v6 still
  reads a v1–v5 file's flat `classes` list and splits it on the way in (`splitIntoLessons`). A backup
  **file** version is not a schema version: files written by old builds keep existing after the migrations
  that matched them are gone, so this number may never be squashed the way the schema's chain was.
- **Backup = full DB snapshot → JSON via SAF.** Ids preserved, import is one transaction. The format
  is versioned: an **older** file still imports (missing tables come back empty, missing columns take
  their default), a **newer** one is refused. A restored file is reproduced as given.
- **A backup is checked for readability before a row of it is written** (`BackupIntegrity`), before the
  transaction, so the reason survives to be shown. It covers exactly the columns a read can *fail* on —
  a backup may be strange, just not unreadable; referential integrity is left to the foreign keys.
- **A refused backup says which refusal it was** (`BackupFailure`): from a newer build, not a Klas
  backup, or wouldn't open. Classification is pure; the wording is shared by the backup screen and the
  first-run restore.
- **Restoring is more than importing**: `BackupRestore` owns the whole act (replace rows, end first
  run, re-plan reminders, re-mirror to *this* device's calendar), so both entry points share one path.

### Calendar, reminders, diagnostics

- **Calendar sync via `CalendarContract`, not the Google REST API** — the OS syncs to Google, so there
  is no cloud project or OAuth. Opt-in, behind the `CalendarSync` port. What a status change means for
  the event is pure (`domain/CalendarSyncPolicy`: scheduled → upsert, cancelled → delete, completed →
  refresh).
- **The mirror runs one way: app → calendar.** Every write goes through `ClassRepository.write`'s
  phase 3, so booking, editing, moving, cancelling, deleting, a roster change and a rename all reach
  the event. Nothing reads the calendar back. A lesson dragged in Google Calendar is not a lesson the
  app knows moved — and the reverse direction is not a missing line but a different app: `classes`
  would stop being the source of truth, a deleted event (or an account removed, or a permission
  revoked) would have to be told apart from a tutor's intent before it deleted real billed history,
  and a drag onto a taken hour would meet `Scheduling`'s refusal *after* the calendar had accepted it.
- **A completed lesson's event is kept true, and never created** (`Action.REFRESH`). It used to be
  left alone entirely, which was wrong in the one case that matters most: **a note is usually written
  after the lesson**, by which time auto-completion has marked it taught — so the note, and any
  correction to the hour beside it, reached the database and stopped there. `refresh` is the narrow
  half of `upsert`: no insert (turning sync on never backfills the tutor's past, and an event they
  deleted themselves is not resurrected), no id back to write, and no attendees or reminders —
  re-inviting a student to an hour that has passed would email them about it.
- **The lesson's note is the event's description**, verbatim and unconditionally — written even when
  blank, so clearing a note clears the description instead of leaving last week's homework standing.
- **Email reminders ride on the calendar**: a synced event adds the student as an attendee with an
  email reminder, and the calendar account does the sending.
- **Inviting students by email is its own setting**, separate from sync and **off by default**. Turning
  it off and re-syncing **withdraws** invitations already sent.
- **The calendar-permission rule sits behind the view model**, not behind the switch: sync cannot be
  enabled without the grant. **The adapter asks the same port** rather than calling
  `checkSelfPermission` itself, which it did in two private methods while `CalendarPermission` sat in the
  same package answering the same question for the settings row. Two definitions could drift; worse, the
  check and the writing were joined only inside a class no test can construct, so "a grant revoked while
  the app was away stops the mirror" could not be set up at any seam — `CalendarMirror` takes a
  `CalendarSync` and had no way to make the fake refuse.
- **Which calendar an event lands in is a rule, and it has shipped wrong once** (`CalendarChoice.preferred`).
  Anything at or above `CAL_ACCESS_CONTRIBUTOR` is eligible — the account's primary first, then a visible
  one, then a more privileged one. Demanding `OWNER` matched nothing on devices reporting a synced Google
  calendar as EDITOR or CONTRIBUTOR, so on those phones every event the app believed it mirrored silently
  no-opped: the failure a best-effort port hides best. It was a `WHERE` and a three-key `ORDER BY` inside
  the adapter; the query is now unfiltered and the ranking is pure. `guestAction` went the same way — add,
  withdraw, or leave alone — which is where "touches exactly one address" and "idempotent in both
  directions" are now asserted rather than distributed across three private methods.
- **A lesson mirrors as one batch, under one snapshot of the settings** (`CalendarMirror`), so
  a lesson cannot mirror half under an old invite setting and half under a new one.
- **A rename re-mirrors.** An event's title and guest are read from the student, subject and group
  rows at mirror time, so those writes owe a refresh too — `CalendarMirror.refreshFor*`, driven off the
  classes that actually carry an event id. Until this existed only a `classes` write mirrored, and a
  rename left every already-mirrored event stale until something unrelated triggered a resync.
- **A reminder only fires *ahead* of a lesson, never on booking.** The plan is re-derived from scratch
  on every change — over the **horizon**, not the whole history; `Maintenance` names that horizon once
  and uses it both to fetch the window and to filter it. `MaintenanceTest` pins the boundary lesson.
- **A reminder's plan carries its firing instant** (`Reminders.Plan.fireAt`), not a delay: the delay
  was WorkManager's shape, and it made the alarm adapter re-derive the instant against a second clock
  read. `ReminderAlarms` arms what it is handed and decides nothing.
- **A reminder is an alarm; upkeep is a worker** — split by whether being late is acceptable.
  Reminders ride `AlarmManager` (`ReminderAlarms`): exact where `SCHEDULE_EXACT_ALARM` allows,
  `setAndAllowWhileIdle` where it does not, never prompting for the permission. Armed ids are kept
  because `AlarmManager` has no cancel-all. `BootReceiver` re-derives the plan after a
  reboot. `refreshNow` and the periodic upkeep stay on WorkManager.
  - **The replacement discipline is above the platform, behind two ports** (`AlarmClock`,
    `ArmedReminders`) — the same split `CrashLogger` draws over `CrashLogStore`. "Re-arming replaces exactly
    the previous set" and "an alarm set yesterday is cancellable after a restart" are the two things
    `ReminderAlarms` exists to guarantee, and while the `PendingIntent` building and the preferences read sat
    in the same class they were also the two things only a device could check: it had no test at all.
    `cancel` takes `FLAG_NO_CREATE`, the cancel-side flag — it was `FLAG_UPDATE_CURRENT`, which *creates* the
    intent it is about to cancel.
- **Upkeep also runs in process at startup** (`KlasApp`), not only as an enqueued worker, because
  auto-completion is what *bills* a finished lesson. Arranging the **standing** pass is
  `ReminderScheduler.ensurePeriodicMaintenance` — on the port beside `refreshNow`, which is the same pass
  on demand. It was on the adapter alone, which made `KlasApp` the one place in the app injecting a `work/`
  class by name.
- **Turning auto-completion on is an action, not just a flag.** It sweeps the whole backlog — every
  lesson that has already finished, however long ago — so the switch runs the pass itself, asks first
  whenever that would complete anything at all, and reports what it settled. Both counts are in
  **lessons**, not billed classes. No size threshold: the tutor cannot see the backlog before flipping
  the switch, so "small enough not to mention" would be a judgement made on their behalf about their
  money. Declining leaves the setting **off**: the sweep has no lower bound, so with it on the next pass
  would settle that backlog anyway, and a "no" that only postponed it would be a lie. Wanting
  auto-completion without the backlog is therefore not expressible — cancel the lessons you do not want
  billed first. The **background** pass still asks nobody; a worker cannot.
- **An unwatched pass says what it billed** (`Maintenance.runUnwatched`, the upkeep channel). Completing a
  lesson recognises income, so which pass *announces* itself is decided by who asked for it: the switch
  reports through the screen the tutor is standing in front of, an app-start pass reports by the schedule
  being right when they get there, and the pass that runs at four in the afternoon with the app closed had
  nothing to report to at all — so it moved the income figure in silence. Counted in **lessons**, with what
  they came to beside it.
- **A reminder is answerable from the tray**: «Проведено» and «Перенести», the schedule's own two words.
  «Проведено» acts in place — a broadcast into `ReminderActions`, no screen — because the tutor is
  walking out of the lesson, not sitting down with the app; «Перенести» can only *open* the lesson, since
  a notification cannot show a picker. What the buttons decide is outside the receiver
  (`ReminderActions`, `TrayNavigation`) for the reason `DueReminder` is: a `BroadcastReceiver` is
  reachable only from a device, and one of these buttons **bills**. Reminders **stack**, so they carry a
  group and a summary to collapse under — Android ignores the grouping without one.
- **Where a tray tap lands has one answer** (`TrayNavigation.destinationFor`), sealed rather than a nullable
  id per destination, so a third tray entry cannot quietly become a third boolean in `AppRoot`. It was
  `ReminderNavigation` while a reminder was the only notification that opened anything; the debt digest is
  the second, and it is not a reminder. The debts land on the **reports tab** the way the bottom bar
  reaches it — pushing it on top of wherever the tutor was would leave a back arrow and no tab selected.
- **Two digests, both opt-in, both scheduled by the upkeep pass** (`domain/Digests`, `notifications/DueDigest`):
  a morning agenda naming the day's lessons, and a weekly summary of who owes money. Times and the weekday
  are the tutor's, in settings, defaulting to 09:00 and Monday. They are **workers, not alarms** — the split
  the app already draws is whether being late is wrong, and «сегодня 4 занятия» is still true at twenty past;
  spending the exact-alarm budget here would put the one feature that needs punctuality behind two that do
  not. The firing instant is pure (a wall-clock promise through `ZoneId`, so a DST day still lands at nine),
  and re-derived by every pass, which is what corrects WorkManager's periodic drift.
  - **A digest that is late is still owed** (`Digests.stillOwed`). Re-planning that often against a policy
    that cancels what is enqueued turned *late* into *never*: `plan` looks strictly forward, so a 09:00
    agenda WorkManager had not run yet at 09:12 was re-planned for tomorrow and the pending run was cancelled
    on the way — the most likely trigger being the tutor opening the app, and for the weekly digest the cost
    being a whole week. A run already due is now left alone. Switching a digest **off** still cancels a due
    one; changing the hour lets today's already-due one through once, because it was owed under the setting in
    force when it came due. The adapter reads the clock **once**, through the injected `Clock`, and takes its
    delay from `Digests.delayUntil` — this was the last `Instant.now()` in `main/`.
  - **What should happen to the queue is one value, decided above the port** (`Digests.Arrangement`:
    `Cancel | LeaveAlone | Enqueue`). `WorkScheduler.arrange` made all three calls itself, under a KDoc
    asserting that *nothing here is a decision* — inside 160 lines of WorkManager with **no test reference
    in either source set**, and two of the three are the branches that produced the defect above. The
    adapter is now a `when` that maps each answer to the platform call for it. Whether the queue is worth
    asking about is still the adapter's, but only as a **read**: a digest that is off is taken down without
    the round trip, and `arrangementFor` answers `Cancel` either way.
  - **The evening one is the counterpart of auto-completion, not a companion to it.** Both answer "the
    lesson ended and nobody said anything": auto-completion assumes it happened and bills it, this one
    asks. So a tutor with auto-completion on gets nothing from it, which is correct and is what the
    setting's own description says. It counts **lessons and nothing else** — no money line, unlike the
    other two — because nothing in it has billed, and printing what the evening *would* have come to
    would state income the reports do not recognise. It names today's, not every lesson ever left
    unmarked: the day is the unit a tutor closes down, and the backlog already has an owner (turning
    auto-completion on sweeps it and says what it settled). A lesson still **running** at nine is not
    unmarked yet — that boundary is `LessonPhase`'s, shared with auto-completion.
  - **It lands on today's schedule** (`TrayNavigation.Destination.Today`), where each of those lessons is
    one swipe from being marked, and it carries **no action button**: «Проведено» on a list of five could
    only guess which, and it bills. Today rather than the day the schedule was last left on — `AppRoot`
    passes a ticket into `ScheduleScreen`, because the schedule is the graph's start destination and its
    view model outlives every arrival at it, so "put it on today" has to be an event rather than an
    argument every arrival including the launcher's would have to state.
- **A digest says nothing rather than saying nothing happened.** An empty day, a day whose only lesson was
  called off, a week with no debts: no notification at all. The agenda prints the **same** `DaySummary`
  sentence the schedule puts under that day (`format/daySummaryText`, one rule, two readers) and lists the
  lessons that summary counts — a cancelled one is named by the summary and given no line, exactly as on
  the screen. It covers the **whole** day and not the rest of it, because the total it prints is a whole-day
  total and two day-lengths in one notification would both look right.
  - **The sentence's rules are `DaySummary.parts`; only its words are Android-bound.** Which clauses a day
    has, in what order, and the two cases where it is not the ordinary one — nothing at all for an empty
    day, and *only* the cancellation for a day entirely called off — lived inside `daySummaryText`, which
    takes a `Resources` and so could not be called from a JVM test. The agreement the function exists to
    guarantee therefore rested on a rule nothing could check. `format/DayText` is a wording table over the
    parts now, and it is still the only file in `format/` importing an Android type.
  - **How much of the agenda fits is `TrayContent`** — the six-line cap and the «ещё N» that follows it.
    That arithmetic was in `AndroidNotifier`, as was the « · » join (now `format/bulletJoin`, shared with
    the day summary so a screen and the tray entry summarising it cannot separate). The port's own contract
    already said decisions belong on this side of it; the wording was the part that had been left out.
- **A notification is a stale view of the lesson by construction**, so acting on one asks the same
  legality question the actions sheet asks (`GroupRules.sessionTransitions`): it can sit in the tray for
  hours while the lesson is cancelled, deleted or completed by hand. A cancelled lesson is never
  resurrected as a taught one on the strength of an old reminder. The reminder leaves the tray either
  way — "nothing to do" and "done" look identical from outside, and a button that visibly does nothing
  reads as broken.
- **The notification permission is asked for where reminders are**, in settings, never over a first
  launch — and the row shows the **platform's** answer beside the tutor's intent.
  - **And once at the tutor's first booking** (`ClassEditViewModel.notificationsToAskFor`,
    `ClassRepository.anyLessons`), which is the same rule reaching the one moment Settings could not: a
    reminder is a promise about a lesson, and until a lesson exists the promise is abstract. It stays out
    of first-run setup for exactly the reason this bullet gives — asking over an empty schedule is asking
    about a feature nobody has met. **Only the first**, because on Android 13+ a second denial is
    permanent and no dialog can appear afterwards; every later chance is the Settings row, which is a
    place the tutor goes on purpose. Silent while reminders are switched off, and never on an edit. «May a reminder be
  shown» has one definition, the port's: `AndroidNotifier` asks `NotificationPermission` too, instead
  of a weaker private copy that ignored the app-wide system switch. Deliberately *not*
  the calendar's rule: that flag gates a mirror, this one is only a preference, so it is never refused.
- **Both permission rows show the platform's answer**, not only the tutor's intent. The calendar row does
  it too now: its permission is revoked from the same place, while the app is away, and revoking it left
  the switch on with the mirror silently dead. The two questions stay distinct — whether sync may be
  *enabled* is a refusal (`setCalendarSyncEnabled`), whether it is *working* is a report — and neither is
  a `Flow`, so both are re-asked on resume.
- **A reminder needs two grants, and they fail differently** (`ReminderDelivery`: OFF | ON_TIME | BLOCKED |
  LATE). `POST_NOTIFICATIONS` decides whether it appears; `SCHEDULE_EXACT_ALARM` decides whether it appears
  *when promised* — and that one is absent by default on Android 14+, where `ReminderAlarms` falls back to
  an inexact alarm Doze may hold for hours. So the app's one time-critical promise was quietly approximate
  on every new install with the row underneath reporting it fine. Whether an alarm can be exact now has one
  definition, the port's (`ExactAlarmPermission`), for the reason "may a reminder be shown" does: the private
  copy inside the adapter decided the behaviour and nothing else could read it. **LATE is a report, not a
  refusal** — inexact is a real fallback, and the app still declines `USE_EXACT_ALARM` — but it carries the
  way out: a row that opens the system page, shown only while that is what the state is.
- **A refusal the tutor cannot act on is worse than silence.** «Уведомления запрещены» was a dead-end
  snackbar; it now carries «Открыть настройки», and the same message is shown when a *request* comes back
  denied — on Android 13+ turning notifications off app-wide revokes the permission, so re-asking is
  auto-denied and the switch reads as doing nothing. The guarded launch is shared with the student card's
  contact actions (`ui/common/launchExternally`), because an `ACTION_*_SETTINGS` with no activity behind it
  is as real on an OEM build as a tablet with no dialler.
  - **BLOCKED earned the row LATE already had** (`SettingsViewModel.unblockNotifications`), and until it
    did, *nothing in the app ever asked for `POST_NOTIFICATIONS`.* The setting is on by default and the
    permission is not, and the only caller was the switch — which cannot be turned on when it is already
    on. So a fresh install had reminders reading «включено», `AndroidNotifier` dropping every notification
    at its permission check, and a row describing the dead end without a way out; `ReminderDelivery` was
    added to *report* that state and nothing was added to *leave* it. Found on a real phone, where all four
    kinds were silently absent while the alarms and both digest workers were correctly armed — the
    scheduling half had never been the problem. The tap **requests** where there is something to request and
    **opens the notification page** where there is not, which is the same three-armed answer the switch
    needs, so the two share one definition rather than restating it. It stores nothing: unblocking clears
    the platform's refusal of a setting the tutor already has.
  - **Both platform answers now share one row with one title** (`SettingsScreen.reminderProblem`). Two
    grants failing differently is a fact about Android, not about the tutor's morning: what they have is
    reminders that do not work and what they want is one thing to tap. The states were already mutually
    exclusive, so only one row was ever on screen — but its wording changed with the diagnosis, which made
    one control read as two. The title is fixed and only the line under it says which problem it is. One
    tap cannot fix both because the grants live on different system pages and Android publishes no
    combined one; when both are missing this takes the notification first and becomes the punctuality row
    on return, the order `ReminderDelivery.of` already stacks them in.
  - **No user-visible string says «будильник».** It means *alarm clock* — the thing that wakes you at
    seven — so a tutor meeting it in a tutoring app has no way to connect it to a lesson reminder, and the
    row read as belonging to the Clock app. It is Android's word for the timer behind the notification and
    the tutor never meets a timer: they asked to be told an hour before the lesson, and all they can
    observe is whether they were. So the row reports **lateness** and names no mechanism. The system page
    it opens is «Будильники и напоминания», which is why «напоминания» is the half of that title worth
    sharing.
- **Crash diagnostics are local + Play, no backend**: chains to the platform handler (so Android Vitals
  still reports) and appends to a size-capped file (`Diagnostics.appendBounded`, 64 000 chars, trimmed
  on entry boundaries), exportable from **Ещё → Настройки → Диагностика**. No Crashlytics/Sentry.

### UI — language and where decisions live

- **The app speaks two languages, and which one is the tutor's setting** — `AppLanguage`
  (SYSTEM | RUSSIAN | ENGLISH), stored in the settings row, chosen in Settings and in the first step of
  first-run setup. **SYSTEM is the default and a real answer**, the shape `ThemeChoice` already had: a tutor
  who has set their phone to English has said what language they read software in, and it is the only answer
  that stays right when they change their mind on the phone. `values-ru/` is Russian, `values/` is English
  **and the fallback** — Android loads the default folder for an unmatched locale, so `AppLanguage.FALLBACK`
  has to be English or a French phone would get English strings under Russian dates.
  - **The locale is *pinned onto the resources*, never `Locale.setDefault`** (`Context.inLanguage` in
    `Localization.kt`). A **plural** rule set comes from the *configuration's* locale rather than from the
    folder the string was loaded from, so an unpinned phone applies its own quantity forms to whatever text
    it loaded — the original defect («0 занятия» on an English phone) is symmetric, and an English app under
    Russian rules is the same bug. Counted nouns therefore always go through `<plurals>`, never `%d` plus a
    fixed word. The JVM default is left alone because it is the **phone's** answer and is read as such:
    `WeekStart` asks it which day the tutor starts their week on.
  - **Screens get it from the composition, not from the activity** (`ProvideLocalizedResources`, called once
    by `AppRoot`). `attachBaseContext` runs before Hilt and the row is a suspending query, so the activity
    can only ever pin the *phone's* language; providing it instead of recreating the activity is also what
    makes the switch instant. It provides **all three** locals — `LocalResources`, `LocalContext`,
    `LocalConfiguration` — because a reader may ask any of them, and providing the resources without the
    configuration is a screen whose ordinary strings follow the setting and whose counted nouns do not. Its
    own composable so a device test can exercise the real provision (`LocalizedResourcesTest`): whether
    Compose honours a provided locale is a fact about Compose, and the one part of this that would fail
    silently and completely.
  - **The two surfaces with no composition ask for it themselves** — `AndroidNotifier` and the calendar
    mirror's event location, through `Context.inAppLanguage()`. `KlasApp` and `MainActivity` still pin their
    base contexts, but only to *a language the app has*, which is a statement about plural rules and not
    about the setting.
  - **`AppLocale` is the one published answer**, and it is published where the settings row is read or
    written (`SettingsRepository`) — so a consumer cannot be handed a new language and then format a date in
    the previous one. Ambient for the reason `Formatters`' zone is: at render time there is exactly one
    answer, and threading a `Locale` through seventeen display functions would be ceremony with no decision
    behind it. A test states it through `AppLocaleRule`, which is required of *any* assertion about a
    formatted date or amount — without it such a test asserts the build machine's locale.
  - **A language change owes the calendar a resync** (`SettingsChange.calendarWork`), because a mirrored
    event's location is the app's own word for «Очно» written at mirror time. Every other surface builds its
    words when it shows them; only the mirror stores them.
  - **No string file may drift from the reference** (`StringCoverageTest`): every name in every language, the
    same *kind* everywhere (a `<string>` here and a `<plurals>` there is a crash), the same format arguments,
    positional everywhere, each language's own plural forms, and equal-length `<string-array>`s — both of the
    app's arrays are read by index. A missing translation fails no build: `R.string.foo` resolves against
    `values/`, so it ships and then throws for whichever tutors read the other folder.
- **A third language is an addition, not a rewrite**, and the pieces that would otherwise silently
  half-support it are the ones with tripwires on them. `StringCoverageTest` **discovers** the folders instead
  of naming them, so a new `values-xx/` comes under every assertion at once, and it pins the folders against
  `AppLanguage.translated` both ways — a folder with no enum entry is unreachable, an entry with no folder
  claims a language and serves English. What a new language costs is therefore stated rather than
  remembered: an enum constant, a `values-xx/` folder, its plural forms in that test's acknowledgement list
  (CLDR's rules are not reachable from a JVM test, so they are declared — which is what stops a language
  being exempted by default), a `languageLabel` arm the compiler demands, and a device plural test beside
  the two that exist. Everything else — the formatters, the resolution, the decimal separator, the settings
  and setup rows — is generic over `AppLanguage.entries` already. Verified by dropping a partial
  `values-de/` into the tree and reading what failed.
- **A screen draws; it does not decide.** Rules that would sit inside composables live in the module
  behind them, because a `@Composable` can only be exercised on a device: `ClassFormState`,
  `MoneyField`, `leaveIntent`, `Maintenance`, `CrashLogger`.
- **A refusal is said out loud, and always the same way** (`ui/common/Messages`). View models report
  one-shot outcomes through `UiEvents`; screens show them through **one** snackbar helper, which can
  carry an action. There is no `android.widget.Toast` in the app. The exception to the one-shot rule
  is `ScheduleViewModel.timeConflict`, state because it must survive a rotation.
- **`java.time` everywhere** + core-library desugaring, so it works on `minSdk 24`. **Today rolls over
  at midnight** — from a flow that sleeps until the next local midnight and re-reads an injected
  `Clock`, not `LocalDate.now()` in a composable body.
- **12- or 24-hour is the phone's answer, on the dial and in the text** (`ClockFormatTest`).
  `TimePickerModal` leaves `is24Hour` out; screens print times through `ui/common/timeText` /
  `dateTimeText`, composables that ask `DateFormat.is24HourFormat` and pick one of the two faces
  `format/Formatters` owns — the same division as `Money.text` and the currency. The reminder asks the same
  question directly, having no composition to ask it in. The line is **read on this phone or away from it**,
  not screen or not-a-screen: what keeps `HH:mm` is a report, an export, a backup. `hour12` has **no
  default**, so no surface can pick a clock by saying nothing. The 12-hour marker is a pinned "AM"/"PM",
  since `ru` CLDR renders it «ДП»/«ПП» on some Androids and not others. **A clock face is not a language**:
  both faces are the same in Russian and English, and `FormattersLanguageTest` pins that they do not move
  with the setting.
- **Where the week starts is the phone's answer too, where the phone has one** — the `fw` regional
  preference (Android 14+), read off the device's locales (`WeekStart.kt`) and injected as
  `di.FirstDayOfWeek`. The month grid, the week strip and the header row all take it from there, and the
  header reads its Пн…Вс off the week it labels so the two cannot drift. **Absent a preference: Monday.**
  The locale's own convention is not consulted — it would start an `en-US` phone's week on Sunday while the
  app speaks Russian to a tutor in Minsk. Material's `DatePicker` cannot be made to follow: it reads
  `WeekFields.of(locale)`, which ignores the keyword here (`WeekStartKeywordTest` pins this), so its own
  little month stays Monday-first.
- **The app speaks through the phone's haptics** (`ui/common/Haptics`, `HapticFeedbackTest`), in **five**
  named moments and no others: `GestureThresholdActivate` at a drag's mark («now»), `Confirm` when an action
  lands («done»), `Reject` when one is refused («no»), `ToggleOn`/`ToggleOff` when a stored fact flips, and
  `SegmentTick` moving between the segments of a fixed choice. Each is chosen in one place and wired into
  the shared atom that owns the moment — `SwitchRow`, `SegmentedChoice`, `swipeBetweenSegments`,
  `SwipeToConfirmBox` — never per
  screen, so a switch cannot buzz "on" while turning off and a re-tap on the segment already chosen is
  silent. Two atoms share `SegmentTick` because they are two routes into one act: tapping a segment and
  dragging the page to it. All go through `LocalHapticFeedback`, which honours the system touch-feedback setting and degrades
  an effect the phone is too old to know. No `Vibrator`, no `VIBRATE` permission.
- **What does *not* buzz is the point of the list**: an ordinary button, a list row, a tab, a navigation.
  The platform reserves haptics for state changes, thresholds and confirmations and leaves a plain tap to its
  ripple — a phone that buzzes at everything is one where no buzz means anything.
- **A refusal is felt, and a status change feels the same whichever way it was made.** The app's one hard
  "no" — an hour already taken — arrived as a dialog and nothing else, while a lesson marked taught buzzed
  twice; and marking one taught *from the sheet* only popped, so the same act felt different depending on how
  it was reached. The `Reject` is keyed on the refusal **arriving** rather than on the action, because
  `timeConflict` is state that survives a rotation.
- **A notification's buzz is the channel's**, not the app's: importance is set (HIGH for a reminder, DEFAULT
  for a digest, LOW for the upkeep report) and no `setVibrationPattern` or `setSound` anywhere — the same
  argument that keeps `Vibrator` out of the UI. Channels are created at **app start** rather than before the
  first post of each kind, or a tutor could not pre-mute a digest until it had already interrupted them once.

### UI — the platform edge

The app's own layer was careful long before this one was: these are the places where Compose meets
Android, and each was a default rather than a decision.

- **The launch window is the app's colour, in both themes** (`res/values/themes.xml`,
  `values-night/`, `res/values/colors.xml`). `Theme.Klas` was the platform's `Material.Light`, fixed, so
  the window the system paints for the whole cold start was white — on every dark-mode launch. The
  splash is declared through **core-splashscreen** so it exists on API 24–30 too, and `MainActivity`
  **holds it until the settings row arrives** (`setKeepOnScreenCondition`), which is what makes the
  handover silent: the theme is resolved from that row, so lifting earlier shows a spinner or a frame of
  the wrong theme. `values-night` follows the *system*, since the tutor's own choice lives in Room and
  cannot be read before a window exists — right for SYSTEM, which is the default.
- **The system bars' icons follow the app's resolved theme, not the phone's** (`ui/theme/Theme.kt`). The
  XML answer is per *configuration*; a light app forced onto a dark phone was drawing white icons over
  its own white background.
- **There is exactly one `Scaffold` in any tree.** The root is a `NavigationSuiteScaffold`
  (`ui/AppRoot.kt`) and every screen brings its own `Scaffold`. A `Scaffold` wrapping a `NavHost` of
  `Scaffold`-bearing screens applied the status-bar inset twice — a Compose inset is not consumed by a
  `padding()` call — so the four tab screens sat a status bar too low while every pushed screen sat
  correctly.
- **The chrome follows the window**: bottom bar on a phone, navigation rail once there is width
  (`NavigationSuiteType.None` on a pushed screen). Tabs are defined once as suite *items*
  (`ui/navigation/BottomBar.kt`, `klasTabs`), with the outlined/filled glyph pair Material asks for —
  the bar had one filled icon per tab, leaving the tinted pill as the only mark of where you were.
  Two-pane list-detail browsing is **deliberately not** done: the panes would be redone by the screen-shell
  refactor and no tablet is a target yet.
- **A form applies `imePadding()` before its scroll**, so the keyboard shrinks the viewport instead of
  covering it. Under edge-to-edge the IME is an inset rather than a resize, and M3's default scaffold
  insets leave it out — so the group's rate field, the roster and the per-subject prices sat under the
  keyboard with nothing to scroll. The one dialog tall enough to need it (`StudentEditDialog`) scrolls too.
- **Every scrolling screen's app bar reacts to the content under it** — `pinnedScrollBehavior` for the
  tonal change that separates bar from page, and `LargeTopAppBar` +
  `exitUntilCollapsedScrollBehavior` on the two detail cards, where the title *is* the subject of the
  screen. Repeated per screen for now; this is the strongest argument for a shared screen shell.
- **Every destination arrives the same way — a short cross-fade** (`ui/navigation/Transitions.kt`, one
  `screen` builder rather than four transition arguments per `composable`, because there are sixteen of
  them). It used to say *direction*: `tab` cross-faded between peers while `pushed` slid a quarter-width
  so back reversed the way in. That was not worth what it cost. The slide had to be short to avoid
  reading as a page turn; a short slide needs a fade or the incoming screen appears from nowhere already
  covering most of the display; and a fade across the travel means **neither screen is opaque while both
  are on screen**, so the compositor drew one through the other — «Расписание» and «Новое занятие»
  printed over each other for a quarter of a second, the day's lessons showing through the form's fields.
  The apparatus existed to convey one bit (deeper, or sideways) that the app bar's back arrow already
  states in words, permanently. Predictive back is still opted into
  (`enableOnBackInvokedCallback`); it previews a fade.
- **The navigation chrome leaves with the screen, not ahead of it** (`AppRoot`). Whether the tabs apply
  is settled the instant `navigate` is called, but the screen they belong to takes a fade to go, so
  switching `layoutType` to `None` cut the bar away on the transition's first frame and the outgoing
  screen — still the only thing visible — reflowed into the space it left. The suite's own
  `NavigationSuiteScaffoldState.hide()` holds it for the length of the change instead, so the removal
  falls under a screen that has arrived. It does not slide; this is *when*, not added motion.
- **Which tab is *marked* is not which route is showing.** `klasTabs` takes a `selectedRoute`, read off
  the **back stack** (`AppRoot`): a pushed screen sits on top of the tab it was opened from, so the
  deepest entry that is a tab owns everything above it. Comparing against the *current* route answered
  "none of them" the moment anything was pushed — every glyph fell to its outline and the pill went out
  — which was invisible only while the bar was being deleted on the transition's first frame, and became
  a bar going dead on screen as soon as it stayed. Read rather than remembered, so process death, a deep
  link and a tray tap straight into a lesson all still name the right tab.

### UI — navigation and lists

- **The bottom bar reads who → when → money**: «Люди», «Расписание», «Отчёты», «Ещё»; the app opens on
  the schedule. **Students and groups are one tab** with a segmented control (`ui/people/PeopleScreen`)
  — a group *is* students seen as a roster. The shared app bar works one `PeopleSide` (search, its
  close-clears-it rule, the archive toggle); both view models are its adapters, and the screen picks
  the side on top once instead of per button. (The `showGroupsTab` **column** survives in `SettingsEntity`
  written-but-unread: SQLite gained `DROP COLUMN` only in 3.35 / Android 12 and this app supports API
  24. `AppSettings` has dropped it, so nothing above the DAO can reach it.) Labels shrink a step at a
  time rather than wrapping.
- **A row that opens a screen says so.** «Ещё»'s four entries had a leading icon and nothing trailing,
  which is the shape of a *settings* list — four things to set — and none of them is: each navigates.
  The trailing chevron is the platform's word for that, and the only mark telling these apart from the
  switch rows two taps away.
- **An empty list carries the way out of itself** (`ui/common/EmptyState`): an icon, a line saying what
  would be here, and — where there is one obvious thing to do — that action as a real button. A list
  empty because *nothing matched a search* says so instead and offers nothing.
- **The three list screens can be searched** (`domain/PeopleSearch`, one rule so they cannot disagree):
  case-insensitive in `Locale.ROOT`, substring rather than prefix, every space-separated term
  matching in any order, so «пет ив» finds «Иван Петров». Students also sort **by debt**. Filtering and
  sorting happen in the view model, on the computation dispatcher.
  - **How a name is folded belongs to no language**, and is stated rather than left to the JVM default — the
    phone's — in both places that fold one (`PeopleSearch`, `format/NameOrder`), so what a name *matches* and
    where it *sorts* cannot disagree. Turkish maps `I` to `ı`, which is enough to break both on a phone whose
    language the app does not even offer. `PeopleSearch` was pinned to `ru` on the argument that the app was
    Russian by construction, which stopped being true when English arrived.
- **A state is a `StatusBadge`, never a font style.** «в архиве» was an italic grey word on both lists —
  the one place a state was said in emphasis rather than in the pill the lesson statuses use, and
  emphasis carries nothing to a screen reader. It stays *grey*, deliberately: archiving is not one of the
  three lesson statuses, and a retired student must not borrow the red a debt is written in.
- **The two rows are the same weight.** A group's row was four stacked lines against the students list's
  two, in a list the two halves of one tab swap between, so switching to «Группы» read as a change of
  density rather than of subject. The rate moved to the **trailing edge**, where money already sits.
- **Names are ordered in Kotlin, not SQL** (`format/NameOrder`): `COLLATE NOCASE` folds ASCII only.
  Time and id ordering stays in SQL.
- **Individual and group lessons are told apart wherever they meet** (`domain/LessonKind`,
  `LessonFilter`), including the schedule's markers, a student's mixed lesson list, and the reports.

### UI — schedule and forms

- **The schedule has three views**, picked with a segmented control rather than an unlabelled icon. Day
  and month both show one selected day; week always lists the whole week. Paging only *browses* — it
  never moves the selection. A calendar cell shows **one count per status**. Both markers and lessons
  are queried for the **active view's range only**.
  - **One view's content is never an answer about another's** (`ScheduleContent.day()`/`.week()`). Switching
    view re-keys the query and a `StateFlow` holds its previous value meanwhile, so the state is `Ready` —
    about the view just left. `ScheduleContent` used to default `rows`/`sections`/`summary` on the interface,
    which let a `Week` answer "this day's lessons" with `emptyList()`: «Неделя» drew «Нет занятий на этой
    неделе» over a full week. Narrowing makes a mismatch `Loading`, which draws the skeleton.
  - **A change of view is a new page, and a page starts at the top.** The three views share one
    `LazyColumn` and the grid and the strip are `item`s in it, so switching prepends or removes the first
    item — and a `LazyColumn` keeps the item that was on top on top. The month grid was therefore inserted
    *above the viewport*. `ScheduleScreen` hoists the list state and resets it on `viewMode`; this is the
    only place in the app that owns a scroll position, and `ViewSwitchScrollTest` pins both halves.
- **The schedule can be narrowed to one student**, which is how a term of bookings is checked after it is
  made. The way in is a menu in the app bar; the way out is a **chip under the view segments**, and the
  chip is the point — a narrowed calendar and a genuinely quiet week look identical, so the name stays on
  screen for as long as it applies and carries its own dismissal. Both the chip and the queries read one
  `StudentFilter`, resolved from `observeAll` (so archiving the student being looked at keeps their name,
  and a filter naming nobody resolves to *no filter* rather than to an empty schedule with no way out).
  The **markers are narrowed with the list**, or a cell would count lessons the day under it does not
  show. What is filtered is **whole lessons** (`GroupRules.lessonsWith`), never the student's own class
  out of one: a session of one would draw a group lesson with a roster of one and hand the action sheet a
  session the lesson does not consist of — a filter is a way of looking, not a claim about what a lesson
  is. A day emptied by the filter says so and offers **no** action, like a list emptied by a search.
- **Wherever a day is named, it says what it comes to** (`domain/DaySummary`): «4 занятия · 5 ч ·
  240 BYN», under the stepper, under the month's selected-day heading and on each heading of the week
  agenda. The screen listed a day's lessons and left the tutor to add up its money, which is the thing
  the app is for. A day's money is **what its lessons come to, billed or not yet** — `chargeFor(rate,
  duration)`, which for a taught lesson is the same number `rebill` stored, so tomorrow reads as itself
  instead of as zero. That is why it is not `Taught.Totals`, which is deliberately backward-looking; a
  calendar cell's `DayLoad` is the third of the three and counts statuses. **A cancelled lesson counts
  for nothing** and is named apart.
- **Today's list says where now is** (`domain/NowMarker`): a hairline between the lessons that have
  begun and the ones ahead, drawn only on today, re-derived every minute from the injected `Clock`.
  A `StateFlow` conflates an equal index, so the sixty ticks between two lessons cost no recomposition.
  The week agenda has none on purpose — it is a list of days, and "where am I in today" is not the
  question it asks.
- **The add button never disappears; it explains.** It was hidden while nothing could be booked, which
  fixed the wrong half of the earlier version's problem: that one popped a toast naming what was missing
  and left the tutor to find it. A control that comes and goes on the app's busiest screen is its own
  defect, so it stays and the message it shows **carries the way out** — «Добавить ученика» — which is
  what the toast never did.
- **A cancelled lesson recedes** — dimmed, time struck through — because the question a glance answers
  is "how full is this day". Its **badge is the error red**, so it is quiet without being lost; grey is
  now nobody's status at all (`ui/common/classStatusColor`).
- **Marking a lesson completed is one swipe**, offered only where completing is legal
  (`GroupRules.sessionTransitions`); the row springs back, and the status badge is the confirmation. The
  panel behind it is the **green of «Проведено»** — see *green means taught* below.
- **The swipe is a promise, and it can be taken back** (`ui/common/SwipeToConfirm`): the phone ticks at the
  mark — a third of the row — the lesson is marked **on the lift**, and sliding home again in between
  undoes the whole thing in silence. `swipeArmed` is the only copy of "would letting go act?", so the tick
  and the release cannot disagree; it is pure, and `SwipeToConfirmTest` asks it on the JVM while
  `SwipeToConfirmBoxTest` pins the wiring on a device, list-scroll arbitration included.
- **A moment that is felt is also seen** (`ui/common/Pop`), because haptics can be switched off on the phone
  and a thumb sits on the row: `armedPop` holds the ✓ enlarged for as long as letting go would act — drawn
  from the same flag as the tick, so neither can appear without the other — and `popsOnChange` pops the
  status badge when the status actually flips, by swipe or from the sheet. Both go through `graphicsLayer`,
  so a pop costs a redraw and never a re-layout; `popsOnChange` is **silent on arrival**, or every row
  scrolled into view would announce itself.
  **Deliberately not `SwipeToDismissBox`**: its release also has a *velocity* path (a flick commits from
  anywhere) and its live `targetValue` is half the width, so the mark, the tick and the action were three
  different places. Here a flick that travels nowhere does nothing — marking a lesson taught is what bills
  it.
- **A lesson is booked by its length, not its end time — and *held* as one.** «Длительность» as chips
  seeded from the tutor's default, «Другая» asking for hours and minutes; the end is derived from the
  start **instant** and only shown («до 00:00 (след. день)» when it crosses midnight). The form once kept
  the end as a second wall clock on the lesson's date, and a 23:00 lesson then read as *minus* twenty-three
  hours: it refused a booking it had made itself, and refused ones «Перенести» had already written. Nothing
  now needs a ceiling on how long a lesson may be to tell a late one from a reversed range, because a
  reversed range cannot be expressed — the picker asks for a length, and `Scheduling.Slot` keeps the `> 0`
  backstop.
- **One place answers "who was in this lesson"** — the lesson's own form, where the answer can be
  changed. The schedule's action sheet acts on the whole lesson and deliberately lists no members.
- **Anything hidden behind a tap is drawn as a field** (`ui/common/PickerField`) — a `Surface`, not a
  disabled `OutlinedTextField` with its colours painted back in, which would cost the ripple.
- **Picking one thing looks the same whether or not the thing has a row id.** `DropdownField` (a
  student, a group, a subject) and `ChoiceField` (a fixed set of values — `ModeField` over `ClassMode`)
  are the same private `MenuField`. «Место» used to be a label above two chips *and*, on the booking
  form, a `PickerField` that tapping replaced with them: one input, two appearances, neither matching
  the fields it sat among.
- **Nothing on the booking form is folded.** «Место» was the first to be unfolded and **the price is
  now too**: it was a `PickerField` for a student and a plain text field for a group, so the same input
  had two appearances decided by who the lesson was for. The fold saved one line and hid the only fact
  on the form that is about money — whether the rate pre-filled from the student's price is the one the
  tutor meant. The label is all that still differs, because the question does.
- **A form is a `verticalScroll`, except where it ends in a list.** The group form is a `LazyColumn` —
  its fields are `item`s and its roster is `items` — because the roster is as long as the tutor's student
  list, and a `forEach` inside a scroll composed and measured forty `MemberPickRow`s with forty avatars,
  none recycled and most off-screen. **Nothing on a form is a fixed width beside flexible text**, for the
  reason the per-subject price used to be: at 140 dp next to a checkbox and a subject name it took a
  third of a 360 dp phone, and at a large font scale the name was squeezed to an ellipsis while «Цена»
  kept every dp of its own. It stacks under the name it prices, indented by the checkbox's own width.
- **Leaving a filled-in form asks first** (`ui/common/UnsavedChanges`). `leaveIntent` picks the
  question, and `leaveGuard` is the **whole wiring** — the back handler, the dialog, and what each
  answer does; three screens spelled it by hand and two answered «Сохранить» without closing the
  dialog, trapping a refused save behind it. `UnsavedChangesTest` pins that only the guard asks. What counts as *dirty* differs per form on purpose — a snapshot where the
  baseline settles (student, group), "was touched" where the form keeps filling itself in (booking).
  Dialogs get no question, only `dismissOnClickOutside = false`.

### UI — detail cards

- **A student's and a group's card are tabbed, with the numbers on top** — a `StatRow` whose values
  shrink to fit, then a sticky `SecondaryTabRow` over one full-length list. A student's tabs are
  «Предметы | Занятия | Платежи»; a group's are «Ученики | Занятия» **only**, because a balance belongs
  to a student. Editing sits behind the pencil, so the card answers "how is this going?". A group's
  roster tab adds how many lessons each member attended. **Booking is not offered on a card** — it
  belongs to the schedule, the one place that knows what else the hour holds.
- **A stat row counts lessons, not classes — for a group** (`domain/Taught`): a lesson taught to three
  is *one* lesson of the tutor's, while the money sums the members' charges. A session counts as taught
  when **any** of its classes completed. A **calendar cell's tally** counts the same way
  (`CalendarLayout.dayLoads`, through the same `GroupRules.sessions` the schedule's list uses), so the
  cell and the list under it agree; a mixed lesson is tallied under no status of its own. A student's debt tile flips to «Переплата» rather than printing
  a negative, and a student's row holds **three** tiles, not four — four are unreadable on a 360dp phone.
- **A detail screen's list keys are namespaced** (`ui/students/StudentDetailKeys`): a subject id, a
  class id and a payment id all start at 1, and a duplicate key crashes composition.
- **A student's contacts are actions.** Phone, email and Telegram open the platform's handler; each
  launch is guarded, since a tablet with no dialler must not crash the card. They are reachable **from
  every tab**, through the card's overflow: the contact block is identity and belongs at the top, which
  is also the part that scrolls away first — a tutor two hundred lessons down the «Занятия» tab had to
  scroll back for a phone number. The block still *shows* the number, the email and the note, because
  reading one is why it stays visible; the menu is where you act on them.
- **A payment carries the date it was made**, defaulting to today, and can be **corrected in place**.
- **A rate's history lives in the reprice dialog**, not under every subject row, where it is the
  context for the number being typed.

### UI — reports and onboarding

- **A report draws a number only where the shape says something the number does not**, and stays plain
  text otherwise. The period is pinned above the report. Then: a **column chart** where the order is
  *time* (buckets one step finer than the period); **one stacked bar** where two parts make the whole
  (individual vs group); **ranked bars** where rows are compared with each other, each scaled to the
  largest row and dropped when a section has only one; **plain text** for totals, counts, per-month
  amounts and debts. A percentage is printed only where the rows genuinely partition the income (so
  not for groups, not for debts), is integer arithmetic on kopecks, and is **not** forced to total 100.
  The total is stated **against the period before it** (`Reports.change`); growth from a period that
  earned nothing has no baseline rather than «+100 %». Debt rows **navigate to the student**.
- **The report exports as CSV** (`domain/ReportCsv`), which a backup is not: a BOM for Excel and Cyrillic,
  and bare decimal amounts so a column can be summed. **The delimiter travels with the decimal separator**
  (`ReportCsv.Dialect`, picked from the app's language): Excel's list separator is `;` in every locale that
  writes `1500,50` and `,` in every locale that writes `1500.50`, and either half on its own is a file that
  lands in one column. One type rather than two characters, because choosing them independently is the
  mistake. This is the one place that does *not* follow "read on the phone or away from it" — a report is
  read away from the phone, but by the tutor who exported it, in a spreadsheet set up for the numbers they
  write.
- **The same CSV can be sent as well as saved** (`storage/DocumentSharing`, `ACTION_SEND`). Saving puts a
  file in a folder the tutor then has to find in a second app; sending it to an accountant — or to their
  own mail, so it is somewhere other than this phone — is what they were actually doing with a month.
  Both stay, because a file kept and a file sent are different acts. **The share sheet opening is its own
  confirmation**, so only a failure is reported: the app cannot see whether anything was sent, and
  «Файл сохранён» about a message that may never have gone would be claiming credit for it.
- **First-run onboarding is offered, not imposed**: walk the setup, restore a backup, or skip. Skipping
  is the same flag flip as finishing, so the app may legitimately open empty. **A subject added in step 1
  can be removed again** — undone from the snackbar rather than guarded by a dialog, since nothing can
  reference one yet; the step's only gate is "at least one subject", so a typo used to be something the
  tutor could finish setup with but not fix. Restore ends first run as
  part of what a restore *is*. Setup steps are numbered «Шаг N из M» from the enum.
  - **Step 1 is the language and the currency**, and it is a step rather than a form because **both arrive
    already set from the phone**: SYSTEM follows the device and the currency comes off its region. What earns
    it a screen of its own is that the guess is wrong in a way only the tutor can see — a Minsk tutor's
    English phone names dollars — and that everything after it is read in the answer. The language applies on
    the tap (the composition provides it), which is what makes the pre-set answer checkable rather than
    something to be believed; the currency asks no confirmation here, unlike in Settings, because there is no
    recorded history yet to re-label. Skipping the setup entirely still keeps the phone's currency, since it
    is substituted at the row rather than seeded by this step. «Ещё» holds only
  what a tutor goes looking for — Diagnostics is folded into the foot of Settings, and About states the
  version and the offline promise.
- **A screen that raises a problem carries the way to act on it.** About makes the lost-phone point and
  now offers «Резервная копия» directly under the sentence that earns it; the backup screen leads with
  when the last copy was made, or says «Копий ещё не было». Diagnostics gives the log `weight(1f)` so its
  own scroll has a viewport — an uncapped 64 000-character card pushed the buttons off the screen.

### UI — one shell, one scale, one control per question

The layer above the platform edge. Everything here held because the author was careful, not because
the code prevented drift — so each of these is a **named thing that makes the careless version
impossible**, and three of them carry a tripwire.

- **Every screen is a `KlasScreen`** (`ui/common/KlasScreen.kt`), and it owns the `Scaffold`, the
  insets, the width cap, the snackbar host, the back arrow, the search toggle and the scroll
  behaviour. Fifteen screens hand-spelled that — eight back arrows, four save buttons
  (`SaveAction` now), two near-identical search toggles — and two of the fifteen (`MoreScreen`,
  `BackupScreen`) had already drifted off the scroll behaviour every other screen had.
  **The content lambda is handed a `Modifier`, not a `PaddingValues`**: that is what lets the shell
  compose `padding(insets)`, `imePadding()` and `constrainedContent()` *in the right order*, so
  "`imePadding()` before the scroll" is a rule in one place instead of a habit in fifteen.
  `KlasScreenSurfaceTest` pins it three ways — no `Scaffold(`, no `ArrowBack`, no `SnackbarHost(`
  outside it — plus a fourth assertion that the shell is really used, so the first three cannot pass by
  there being no screens left. It matches **code with comments stripped** (`testutil/SourceText.code`,
  shared with `MigrationChainTest` since two tripwires need it), or documenting the rule would fail the test
  that enforces it — which is exactly what happened the moment `DatabaseModule`'s KDoc had to explain why it
  no longer calls `addMigrations`.
- **`Spacing` is the app's rhythm** — `tight` 4, `inline` 8, `itemGap` 12, `gutter` 16, `leading` 16,
  `sectionGap` 24. Fourteen distinct gap values existed, four of them off Material's 4 dp grid, and
  the same pattern ("cards in a scrolling list") was drawn four ways. **The list owns the gutter**, via
  `contentPadding`, never the item — the schedule did it the other way and paid for it in two places
  `contentPadding` exists for. A gap that is deliberately *not* on the scale is a named local with the
  reason next to it (`TallySpacing` in a calendar cell, `TileInsetX` in a stat tile, `FabClearance`).
- **A card, or a row, by nesting depth.** A **top-level browsable** list uses `ContentCard`; a list
  *inside* a card or a settings surface uses `ListItem`. The old rule was by screen genre ("settings-style
  lists stay plain") and decided neither of the two cases that were actually wrong: Subjects was a
  browsable list of rows, and a lesson was a card in the schedule and a row on a student's card.
- **Both detail cards are the same three blocks in the same order**: identity → `StatRow` → sticky
  tabs. They disagreed, and the student's balance and the group's rate history were each in the wrong
  one of the two.
- **Lifecycle actions live on the entity's card**, behind the overflow — archive and delete, for a
  student and now for a group too (`GroupDetailViewModel`). The group's used to be a "danger zone" at
  the foot of its *form*, so the same verb was reached two different ways depending on the entity. The
  row bin stays for **subjects**, which have no card, and the sheet stays for a **lesson**.
- **A rate's history sits where the rate is typed** — the student's in the reprice dialog, the group's
  on the group form. It is context for the number being entered, which is an argument that applies to
  both and was being made on only one.
- **One control per kind of question.** A choice between a fixed, mutually exclusive few is
  `SegmentedChoice` (`ui/common/Segments.kt`) — seven places already were, and the booking form's
  student-or-group was a pair of chips. `SegmentLabel`'s one-line shrink rule came with it, so the
  settings rows get it too. **Chips stay for a shortlist with an escape hatch**, which is what the
  duration chips are. `ChoiceRow` — the labelled row *shape* a setting takes — sits in the same file and
  not private to `SettingsScreen`, because first-run setup asks two of the same questions Settings does
  (the language, the currency) and a choice that looked like a setting on one screen and like something
  else on the other would be the app disagreeing about what kind of question it is asking.
  - **`ChoiceRow` picks its control by how many options there are** (`fitsInSegments`, `MaxSegments` = 4):
    segments up to the bound, a `ChoiceField` past it. Both are shapes the app already uses, so this
    switches between two established answers rather than inventing a third. It earns its keep because two
    of the app's choices are **lists that grow** — one option per language and per currency — and the
    currency row sits *at* the bound today, so the fifth of either would have shipped as four illegible
    words in 72 dp: no compile error, no crash, nothing that looks deliberate. The decision is a named
    pure function rather than an `if` in the composable, per *a screen draws, it does not decide*; the
    field arm is unreachable until a fifth option exists, which is exactly why `ChoiceRowTest` exercises
    it on a device.
  - **Where the segments choose what the whole screen is, the page also turns by dragging it sideways**
    (`Modifier.swipeBetweenSegments`, same file): «Люди» (Ученики ⇄ Группы) and «Расписание» (День →
    Неделя → Месяц), left for the next and right for the previous. A shortcut and never the only way in —
    a gesture has no affordance and announces nothing to a screen reader, so the control it duplicates is
    what makes the screen usable. It ticks with the same `SegmentTick` a tap gives, one act feeling the
    same whichever way it was made. **One gesture decides once** (`segmentAfterSwipe`, pure and JVM-tested)
    and **the ends do not wrap**: a row of detents is not a carousel, and arriving at «День» by dragging on
    past «Месяц» would be the pill jumping the control's full width against the thumb.
  - **It is on the screen body, and a child that wants the drag keeps it.** That is the design, not a
    limitation: the schedule's lesson cards own the same axis for swipe-to-complete, and a child consumes
    past touch slop before an ancestor is offered anything — so the page turns from the space *around* the
    cards and a drag begun on a lesson still bills it. A `LazyColumn` reaches its own slop first, so a
    flick down the list is still a scroll. Neither gesture knows the other exists.
  - **Deliberately not a `HorizontalPager`.** The schedule assembles only the active view's lessons and
    markers — both flows `flatMapLatest`-keyed on the view mode — so the neighbouring page has nothing to
    draw until the switch has happened, and a pager would slide a skeleton in under the thumb. The same
    plain gesture is used on both screens rather than the nicer one where it would work: a drag that tracks
    the finger here and jumps there is two gestures wearing one name.
- **One empty-state language** (`EmptyState`, with `compact` for inside a tab). The detail tabs drew a
  bare grey `Text` through a private `TabEmptyState` defined **twice, identically**, in the two card
  files — so five screens met a designed empty state and five tabs met an unstyled sentence with
  nowhere to put an action. `AddRow` moved to `ui/common/` for the same reason: the group's roster tab
  named its emptiness and offered nothing because the composable lived in the other file.
- **`HeroCard` is "the one number this screen exists to answer"**, and there are exactly **two**: the
  report's total and the student's balance. A filled accent surface earns its volume by being rare —
  the report's was the app's only one, and its counterpart was red text in a grey tile. The balance
  uses the tonal variant, because «Долг 240» in the app's happiest colour would be congratulating the
  tutor on money they have not been paid.
- **A field says why it is wrong, in its own supporting slot.** `MoneyField.error` classifies which
  rule broke (unreadable, or a zero payment) and the screen holds only the wording; required fields say
  so instead of leaving a greyed-out «Сохранить» to be decoded. **Everything that qualifies a field goes
  in that field's `supportingText`** — `PickerField` and `DropdownField` gained the parameter so the
  standalone hint `Text`s had somewhere to go, the group form having had two hint mechanisms 20 dp apart.
- **Filled for actions and selected states, outlined for art and inactive glyphs.** Empty-state
  pictures, decorative glyphs beside a line of text and settings leading icons are `Icons.Outlined`;
  buttons, menu items, chevrons and the selected tab stay filled. The two **lesson-kind** icons are
  deliberately exempt — they are a matched pair pinned on *ink height* by `LessonKindIconTest`, and
  swapping the set would change what that test measures.
- **A band content passes under is drawn as one** (`PinnedHeader`, and `StickyTabRow` over it):
  `surfaceContainer` and a divider, not the page's own `surface`, which is what made rows appear to
  slide out from behind nothing. **What earns it is that content genuinely passes underneath** — the
  detail cards' tab rows, pinned by `stickyHeader`, and nothing else. «Люди»'s segmented control had one
  too, on the argument that a fixed band is a fixed band however it came to be fixed; but it sits
  *outside* the two lists, which are clipped at their own top edge a few dp below, so nothing ever
  travelled under it. It was drawing an edge nothing crosses, and costing the app's two segmented
  controls a shared appearance on the two screens most likely to be compared. Both are the schedule's
  plain treatment now.
- **«Готово» closes a picker; «Сохранить» writes; «Вернуть» undoes.** All three modals confirmed with
  «Сохранить», so booking a lesson asked for a save twice in a row — and the undo action was «Отменить»
  in an app where «Отменено» is a lesson *status*.

### UI — design system

- **`ui/theme/`**: curated Material 3 palette (indigo + teal), light/dark with a SYSTEM/LIGHT/DARK
  setting, dynamic colour off for a consistent brand.
- **Amber means *on now*** (`KlasColors.live`), the second role Material's scheme has no slot for. Every
  accent was spoken for and each would have said the wrong thing: `primary` is the indigo of a lesson
  *still to come*, which is exactly what «Идёт» has to be told apart from; `tertiary` is the individual
  lesson's teal; `success` is green and means *taught*, which a running lesson is not yet; `error` red is
  a cancellation and a debt. One tone rather than a pair — this is only ever a word, and no panel means
  "in progress" the way the swipe's panel means "taught". Unaffected by dynamic colour, like the green.
- **Green means *taught*, and it is the app's own colour role** (`KlasColors`, handed down by `KlasTheme`
  beside the Material scheme). «Проведено» used to be `tertiary` — the palette's teal — so it read as a
  second blue next to the indigo of a lesson still to come, and the panel the swipe revealed
  (`tertiaryContainer`) was a pale cyan: the gesture promised one colour and produced another. The badge
  and that panel are now the two tones of one green. Roles Material has no slot for live in `KlasColors`
  rather than being read off `isSystemInDarkTheme()`, because the light/dark answer is the *tutor's*
  (`ThemeChoice`), not the phone's.
- **The tonal ladder is stated**, not left to Material's defaults: an *item* sits on
  `surfaceContainerLow` (`ContentCard`), the *section* holding items on `surfaceContainer`
  (`SectionCard`, `StatRow`, a report card, a pinned `StickyTabRow`). Which of the two a list uses is
  decided by **nesting depth** — see *one shell, one scale, one control per question* above.
- **The app is set in Golos Text** (`res/font/`, `ui/theme/Type.kt`), a Cyrillic-first text face — chosen
  when Russian was the only language, and kept now that English is the second: it is a full Latin face too,
  and the alternative is two typefaces for one app. The type scale
  states every style, including the ones matching the default, and names the family through one
  `AppFontFamily` constant so the styles cannot disagree. Shipped as **four static instances** cut from
  the upstream variable font, because Android honours a `wght` axis only from API 26 and `minSdk` is 24 —
  a variable font would have flattened the whole scale to Regular on 24 and 25. Not subsetted: the face
  is 566 codepoints to begin with, and trimming it risks tofu on the Belarusian «і», «ў» or Ukrainian «ї»
  a Minsk tutor's roster can hold. The OFL notice ships in `res/raw/` and is readable from **About**.
- **Dynamic colour is offered, off by default** (`settings.dynamicColorEnabled`, Android 12+ only, and
  Settings hides the row below that). The curated indigo/teal is the app's identity and what a tutor who
  never opens Settings should get; wanting the wallpaper's colours instead is a real preference and now a
  stated one rather than an absence. `dynamicColorAvailable` (`ui/theme/Theme.kt`) is the single place the
  OS version is checked, so the switch and the theme cannot disagree about when it applies. `KlasColors`
  is deliberately **unaffected**: green is what "this lesson happened" means, not a matter of taste.
- **Avatar colours key off the student's id, not their name** (`domain/AvatarPalette`), so a rename
  doesn't move them; `AvatarStack` draws a roster as overlapping monograms.
- **A monogram is a picture, and says nothing.** `Avatar` clears its own semantics — every caller puts
  the full name in the same row, so TalkBack announcing «И» before «Ирина Петрова» was the letter
  arriving as a word. `AvatarStack` replaces its children with one description naming **every** member,
  including the ones behind «+N»: the truncation is what a list row's width can afford and a reader has
  no width.
- **The themed launcher icon has its own drawable** (`ic_launcher_monochrome.xml`), not an alias of the
  colour foreground. Android tints that layer flat and a launcher draws it at 48dp, where the brand art's
  hairline page fan — half a unit wide in a 108-unit viewport — is a fifth of a pixel of grey smear. The
  silhouette resolves that passage into one solid edge, keeps the same geometry (outer radius 30.75, so
  inside the 33-unit safe circle), and is wired into **both** `ic_launcher` and `ic_launcher_round`.
- **Shared components have previews** (`src/debug/java/.../preview/`), rendered light, dark and at 1.5×
  text by one `@KlasPreview` multipreview annotation. In the **debug** source set on purpose: a preview
  is a development tool, so its sample data has no business in a release APK. They exist because a card's
  inset or a badge's weight is repeated across fifteen screens, and that kind of drift is caught by
  seeing two of them side by side rather than a fortnight apart.
- **The two lesson-kind icons are one pair, not a ranking** (`ui/common/LessonKindUi.kt`): same brand
  colour, matched on **ink height** rather than nominal size.
- **Anything tappable clears 48dp** (`ui/common/MinTouchTarget`), and the main lists carry
  `Modifier.animateItem()`.
- **Responsive width**: `constrainedContent()` (~600.dp cap, centred on wide screens, full-width on
  phones) is applied **once, by `KlasScreen`**, to the modifier it hands its content — so no screen body
  applies it again. Vertical fit comes from every screen scrolling.

---

## Data flow

ViewModels observe repository `Flow`s and expose `StateFlow` UI state. Writes go ViewModel →
repository → Room, and the repository then mirrors to the calendar and refreshes reminders — so a
ViewModel expresses intent only and cannot forget a side effect.

**Reads live in `viewModelScope`; writes do not.** A write goes to the injected `@ApplicationScope`
(`di/CoroutinesModule`), because `viewModelScope` dies with its screen and would otherwise cancel a
save between its committed rows and the mirroring that follows. `ClassEditViewModel` awaits the write
for its `onDone`; the schedule's status/move/delete actions just launch it.

**Deriving state happens off the main thread**: every screen is
`combine(…repository flows…) { … }.derive(this, computation, initial)` — see *The view-model tail, named
once*. `flowOn` governs only what is upstream of *it*, which is why `derive` applies it at the end of the
chain. The dispatcher is injected (`@Computation`) so tests keep one scheduler; `ReportsViewModelTest`
has a tripwire that fails if `derive` stops moving the work.

**A failed write is reported, not fatal.** `@ApplicationScope` carries a `CoroutineExceptionHandler`
into the crash log, since `SupervisorJob` scopes *cancellation*, not failure. Where a screen can say
so it does — `ClassEditViewModel` guards inside the `async` (a failure must come back as a value), and
the schedule's sheet actions report `ACTION_FAILED`.

State a view model exposes is **read-only** (`StateFlow`, never `MutableStateFlow`) and changes only
through its mutators.

Guarded suspend calls use `runCatchingCancellable`, never `runCatching`: the latter catches
`CancellationException` too. Plain `runCatching` stays only around **non**-suspending best-effort work,
as in the calendar adapter.

---

## Non-goals (v1)

Multi-user/login, cloud sync across devices, web/iOS, in-app payments, **a recurring-series entity** —
booking a term writes its lessons and keeps no rule (see *Lessons*), so there is nothing to expand on
read and nothing that owns a lesson after it exists.

---

## Testing

- **JVM unit tests** (`src/test`, `testDebugUnitTest`, ~60 classes) cover the domain layer, the backup
  format and its failure classification, view models, repository side effects and transaction
  boundaries, mappers and formatters — against **in-memory fake DAOs** with a test main dispatcher, so
  most logic is verified without a device. Android-bound halves are reachable through their ports; a
  worker or handler is an adapter, never a decision. The fakes reproduce filtering and time ordering
  only — **name ordering is not restated there**, so they have no version of it to disagree with.
- **`isReturnDefaultValues` is deliberately off.** It turns an unmocked framework call into a silent
  pass on `null`/`0`. A test that needs Android has found a missing port.
- **A test reads screen state through `current`, not `.value`** (`testutil/Observing.kt`). State is
  `Loadable`, and `current` insists on `Ready` — so a test that forgot `observing(…)`, or advanced the
  scheduler too early, now fails naming the cause instead of asserting happily against a placeholder
  that was an empty list of exactly the shape it expected.
- **Three test doubles exist for what cannot otherwise be observed** (`testutil/`): `CountingDispatcher`
  (was the derivation really moved off the main thread — a `flowOn` is invisible in the result),
  `MutableClock` (a midnight rollover needs the wall clock and the scheduler moved together) and
  `RecordingTransactor` (see *atomicity* below).
- **Seed through `testutil/Fixtures`, not the DAOs** (`env.fixtures.completedLesson(…)`). Writing rows
  straight to a fake skips the repository that owns the invariant, so a test can set up a state the app
  cannot reach — and every test that builds its own rows builds them differently. Three versions of "a
  completed class" once coexisted, each asserting only the field it had remembered to fill in; one
  hard-coded a charge that ignored the duration it was passed, and the assertion built on it was wrong
  by 20. A fixture books and completes the lesson, so the charge is `ClassRules`'. The ~330 direct
  writes predating this are being migrated a file at a time, and `DirectSeedingRatchetTest` caps the
  count so it can only fall: a new direct write fails there, and a migration has to bring the cap down
  with it. Reaching for a DAO to build what the repositories deliberately refuse is still legitimate —
  lowering the cap by hand is how such a call gets acknowledged rather than absorbed.
- **Fixtures pin the clock, not `now()`.** Seed a fixed hour and pick days by excluding today. **The clock
  belongs to the `TestEnv`**, not to a view model a test happens to build: it is what the engine's
  defaulted `now`s read, and now also what a payment's instant is derived from, so a test that hands its
  own clock to a view model while the repository writes against another is a test asserting about two
  different days.
- Nine tests are **tripwires against silent drift**, all deriving expectations from a real artifact
  rather than restating them: `BackupSchemaCoverageTest` (a new table must be carried or named
  device-local), `MappersTest` (a new column must be mapped), `BackupSplitTest` (the pre-v2 lesson-status
  precedence is `LessonStatus.PRECEDENCE` and not the enum's declaration order — it *was* `entries.firstOrNull`,
  which `StoredEnumNamesTest` compares as a set and so cannot see; reordering the constants would have made
  every old backup import a mixed group lesson as **completed**. `splitIntoLessons` is the last reader of that
  rule now that the SQL twin in `MIGRATION_1_2` has gone, so this is the only thing holding it), `StoredEnumNamesTest` (a renamed enum
  constant is a migration — it reads the DAO source, since `@Query` is not retained at runtime, *and*
  `FakeDaos.kt`, which had restated three of the same predicates as bare strings and so sat outside the
  very scan built for the risk), `WholeTableWritesTest` (a whole-table delete belongs to `SnapshotDao`,
  and every table it reads it must also empty), `ReportsSurfaceTest` (the app asks `Reports` one
  question), `ScreenStateSurfaceTest` (the sharing policy and the snackbar host each live in one place),
  `KlasScreenSurfaceTest` (the screen shell does too — no `Scaffold(`, no back arrow and no
  `SnackbarHost(` outside `KlasScreen.kt`; the `Scaffold(` match is word-bounded so `AppRoot`'s
  `NavigationSuiteScaffold` does not count) and
  `MigrationChainTest` (a schema version bumped **past the baseline** must ship a migration for every step
  *and* wire it into the builder, the baseline must not exceed the declared version, and the destructive
  fallback must stay debug-only — it reads `AppDatabase.kt`, since `@Database` is `BINARY`-retained). The first eight are acknowledgement lists; the last is the
  upgrade path, and it sits in the **unit** suite precisely because instrumented tests need a device CI
  does not have. Two more guard structure: one asserts the booking form leaves **no query open** once
  nothing collects it, one asserts a save is launched into the application scope.
- **`EdgeCaseCombinationsTest` is the one test file not beside its subject**, deliberately: what it
  tests is the *combination*, spanning layers with no shared home. It holds regressions found that way
  and — in the same file on purpose — pins for behaviour that reads like a bug and is decided.
- **Atomicity is tested by counting transactions**, not by rolling back: the fake DAOs are in-memory
  maps with no rollback to reproduce, so `RecordingTransactor` counts and nests them. Real rollback is
  only observable against Room and belongs in `androidTest`.
- **CI** (`.github/workflows/ci.yml`) runs the unit suite, `lintDebug`, the debug APK and the
  *compilation* of the instrumented tests on every PR and every push to `main`/`develop` — a tripwire
  nobody runs is not a tripwire. The instrumented tests themselves still need a device.
- **Instrumented tests** (`src/androidTest`, 13 classes) cover Room integration — including the FK
  cascade and restrict rules (`SubjectDeletionCascadeTest`) — backup round-trip and restore, calendar
  sync, auto-completion, and the questions that need real rendering: text measurement
  (`BottomBarLabelTest` for the tab labels, `StatRowAutoSizeTest` for a stat tile's amount — both through
  the shared `testutil/textLayoutOf`, since an autosized `Text`'s settled size is *not* in its layout
  input and can only be read back off `GetTextLayoutResult`), icon ink height (`LessonKindIconTest`),
  what a screen reader is handed (`AvatarSemanticsTest`), the plural rule set
  (`RussianPluralsTest`) and **a gesture**, which exists nowhere else (`SwipeToConfirmBoxTest` —
  it caught the swipe deciding against a stale offset, so a slide home still confirmed). A debug-only
  manifest entry lets the test host show over the keyguard, so they run against a locked phone (there is
  no emulator).
- **Every instrumented class is `@HiltAndroidTest` with a `HiltAndroidRule`, and the run happens on
  `KlasTestApp`, not Hilt's stock test application.** Not because the tests inject anything — most do
  not — but because the app's own components keep waking up *inside* the instrumentation process:
  `ReminderReceiver` when a real lesson's alarm comes due, `BootReceiver` after the run's own install,
  `MainActivity` when the phone's owner opens the app mid-run, and WorkManager's `SystemJobService` when
  the periodic maintenance job fires. Each needs something the stock test application cannot give —
  a Hilt component, or a WorkManager `Configuration` — and the process dies without it, so the *whole
  run* reports **zero tests** rather than a failure. It looked like flakiness in whichever test was
  unlucky. The argument lives in `androidTest/KlasTestApp.kt`; the rules are what make it hold.
- **Every migration owes a device test, and both have one** (`SettingsLanguageMigrationTest`,
  `LessonNotesMigrationTest`). The shape is fixed: a real database at the old version built from its
  exported schema, rows written as **SQL** (the old entity no longer exists to write them with), migrated
  under `MigrationTestHelper`, and `runMigrationsAndValidate` refusing a result that does not match the
  exported schema column for column. That validation is the half a hand-written `ALTER TABLE` needs — a
  `DEFAULT` on one side and not the other fails there rather than on a phone. Each also asserts the
  **other** columns of a row it migrated, because a migration that dropped and recreated the table would
  validate perfectly; and each covers the empty table, where a wrong `DEFAULT` has no row to be visible on.
  They are instrumented, so CI can only ever compile them; `MigrationChainTest` is the unit-suite half that
  runs on every push, guarding a migration's *existence* and wiring rather than its behaviour.
- Notification display and real calendar writes can only be observed on a device.

---

## Tooling & build

- Kotlin 2.3.21, AGP 9.3.1, Gradle 9.6.1, JDK 21 (Android Studio JBR). compileSdk 37, targetSdk 36,
  minSdk 24. Hilt 2.60.1, Room 2.8.4, WorkManager, Navigation Compose, kotlinx-serialization,
  core-splashscreen, material3-adaptive-navigation-suite (BOM-managed, like material3) — versions
  in `gradle/libs.versions.toml`. Current release: `0.3.0` (`versionCode` 4).
- **AGP 9 compiles Kotlin itself**: the `kotlin.android` plugin is not applied, and the root
  `build.gradle.kts` pins the built-in KGP so it matches the Compose/serialization plugins and KSP.
  That pin repeats the catalog's `kotlin` because `libs` accessors do not exist inside `buildscript {}`,
  so a configuration-time `check` fails the build if the two drift.
- **Kotlin tracks KSP** — KSP drives Room + Hilt codegen, and its `major.minor` is the Kotlin *line* it
  supports, so Kotlin moves freely within that line and never past it. Kotlin 2.4.x is released and lint
  offers it every build; there is no KSP 2.4.x, and taking it would leave the app with no Room DAOs and
  no Hilt graph. The catalog says so at the pin, with how to re-check.
- **Lint's remaining warnings are all "a newer version is available"** and are left deliberately: the
  Kotlin one is held by KSP as above, and `OldTargetApi` asks for a `targetSdk` bump, which changes
  runtime behaviour and so wants a pass on a device rather than a green desktop build.
- `lint { checkReleaseBuilds = false }` works around a lint-jar file-lock bug seen on Windows under
  AGP 9.2.1 (worth re-testing now the project is on 9.3.1); lint still runs via `lintDebug`.
- Needs `JAVA_HOME` on a JDK 21 and `local.properties` pointing at the Android SDK.
- Common commands: `./gradlew :app:testDebugUnitTest`, `:app:assembleDebug`,
  `:app:assembleDebugAndroidTest`, `:app:lintDebug`.
- The **debug build is installable beside the release one**: `applicationIdSuffix = ".debug"`,
  `versionNameSuffix = "-debug"` and its own «Klas-debug» label — so it also has its own database.
- **Release** is minified + resource-shrunk (R8); signing reads `keystore.properties` (gitignored, see
  the `.example`) and is left unsigned when absent. Play artifact: `./gradlew :app:bundleRelease`. Bump
  `versionCode` per upload. R8 keep rules for `@Serializable` types live in `proguard-rules.pro`.

```
app/src/main/java/com/seblful/klas/
├── domain/            # pure rules + ports + models
│   └── model/         # incl. Ids.kt — typed ids, report breakdowns only
├── data/              # Mappers.kt + {local,repository,backup}/
├── calendar/  notifications/  diagnostics/  work/  format/  storage/  di/
│                 └── ReminderAlarms + ReminderReceiver (reminders are alarms, not workers)
│                     + ReminderActions/ReminderActionReceiver («Проведено» from the tray)
├── Coroutines.kt      # runCatchingCancellable — the only way suspend calls are guarded
├── Localization.kt    # Context.inLanguage() + deviceLocales() — how a language reaches the resources
├── ui/{schedule,people,students,groups,subjects,reports,backup,more,
│       onboarding,diagnostics,common,navigation,theme}/
├── MainActivity.kt    # @AndroidEntryPoint → AppRoot()
└── KlasApp.kt         # @HiltAndroidApp, WorkManager config, in-process startup upkeep
```
