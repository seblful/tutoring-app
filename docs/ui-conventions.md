# UI conventions

How a screen is built in Klas: where its state comes from, the shell it sits in, and the shared parts
it draws with.

## Screen state

- **A screen draws, it does not decide**: every branch is a named value from the domain layer
  (`ArchiveIntent`, `DeleteWarning`, `ScheduleEmpty`, `Balance`, `Taught.Totals.hint`, etc.), never an
  `if` inside a composable. `ClassFormState`, `MoneyField`, `leaveIntent`, `Maintenance`, `CrashLogger`
  hold the rules. Clock reads for money/scheduling go through the injected `Clock`, never
  `Instant.now()`/`ZoneId.systemDefault()` in a composable.
- The booking form's whole mutable surface is `BookingForm.change(intent, context)`
  (`ui/schedule/BookingForm.kt`); reads it needs come back as data (`Lookup`) through the same `change`.
- Every screen's loading/ready state is `Loadable<T>` (`Loading | Ready`), drawn as a skeleton, never a
  spinner; applies to forms too (a blank form is a real, wrong state until data arrives).
- `derive`/`hold` end every screen flow; `ShowMessagesFor`/`rememberMessages` end one-shot snackbar events;
  the guarded-write tail is named `write` (application scope + `runCatchingCancellable` + failure report).
- One-shot outcomes report through one snackbar helper (`ui/common/Messages`) — no `Toast`.

## Look and behaviour

- **Language**: `AppLanguage` (SYSTEM|RUSSIAN|ENGLISH) is a setting; pinned onto resources via
  `ProvideLocalizedResources`, never `Locale.setDefault()`. `StringCoverageTest` pins parity between
  `values/`/`values-ru/` (strings, plurals, format args, array lengths). A language change re-syncs the
  calendar (mirrored text is baked in).
- **Time**: `java.time` everywhere (desugared, minSdk 24); 12/24-hour and week-start both follow the
  *phone's* setting, not the app language; today rolls over at local midnight off the injected `Clock`.
- **Haptics**: five named moments only (`ui/common/Haptics`), wired into shared atoms, never per-screen;
  ordinary taps stay silent.
- **Shell**: every screen is a `KlasScreen` (owns `Scaffold`, insets, width cap, snackbar host, back arrow,
  scroll behavior) — exactly one `Scaffold` per tree. `Spacing` is the app's rhythm (`tight`…`sectionGap`).
  Card vs. row is by nesting depth (`ContentCard` top-level, `ListItem` nested). Detail cards are all
  identity → `StatRow` → one pinned view menu (`StickyViewMenu`, «Занятия ▾»), not a tab row — four
  tabs were too much to read at a glance. Held, a bar's icon names itself (`HoldToName`) — top-bar
  icons and the bottom bar's tabs. One control per kind of question: `SegmentedChoice` (fixed few,
  swipeable), `ChoiceRow` (settings-shaped choice, segments up to 4 then a field), chips (shortlist +
  escape hatch).
- **Navigation**: bottom bar «Люди·Задания·Расписание·Отчёты·Ещё», opens on the schedule (the middle tab);
  Students+Groups share one tab via a segmented control. Which tab is marked is read off the back stack.
  Destinations cross-fade; predictive back is on.
- **Lists**: shared search rule (`domain/PeopleSearch`) across the three list screens — case-insensitive,
  substring, terms in any order. Empty lists always carry the way out (`EmptyState`). State is a
  `StatusBadge`, never a font style.
- **Schedule**: three views (Day/Week/Month) via segmented control; a day is always summarized as
  count·hours·money (`domain/DaySummary`); a "now" hairline on today only; completing a lesson is a
  revocable swipe (green panel); a cancelled lesson recedes, badge stays red. Can be narrowed to one
  student (dismissible chip — `FilterMenuAction` + `FilterChipRow`, shared with «Задания»). Booking is by
  length, not end time.
- **Reports**: number only where the chart shape adds meaning (column/stacked-bar/ranked-bars/plain text);
  CSV export's delimiter follows the decimal separator; can be saved or sent.
- **Design system**: curated indigo+teal Material 3 palette, dynamic color off by default. Amber = "on
  now"; green = "taught" (`KlasColors`, app-owned, unaffected by dynamic color). Golos Text typeface
  (Cyrillic-first, full Latin). Avatar colors key off id, not name.
- **Icons**: filled for actions and selected states, outlined for art and inactive glyphs. Empty-state
  pictures, decorative glyphs beside a line of text and settings leading icons are `Icons.Outlined`;
  buttons, menu items, chevrons and the selected tab stay filled. The two lesson-kind icons are exempt —
  a matched pair pinned on ink height by `LessonKindIconTest`.
