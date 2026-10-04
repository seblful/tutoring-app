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
- One-shot outcomes report through one snackbar helper (`ui/common/Messages`) — no `Toast`. A message
  about a save that closes its screen is lost with the screen, so such a form asks *before* saving instead.
- Turning the phone or resizing its window is handled in place (`configChanges` on `MainActivity`), so an
  open dialog and what was typed into it survive; typed dialog fields are still `rememberSaveable`.

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
  identity → `StatRow` → one pinned view menu (`StickyViewMenu`, «Занятия ▾» — page-coloured at rest, the collapsed bar's colour once pinned), not a tab row — four
  tabs were too much to read at a glance. Held, a bar's icon names itself (`HoldToName`) — top-bar
  icons and the bottom bar's tabs. One control per kind of question: `SegmentedChoice` (fixed few,
  swipeable), `ChoiceRow` (settings-shaped choice, segments up to 4 then a field), chips (shortlist +
  escape hatch). Text sits on the gutter: page sides are `Spacing.gutter`, and a tappable row that needs
  ripple room takes it from the card's padding, not from the text's edge.
- **Section titles and dialogs**: a section title is `titleSmall`, read as a heading, on forms and cards
  alike. A confirmation that loses something — delete, remove, clear, replace from a backup, discard
  typed input — says its button in `colorScheme.error` (`ConfirmDialog(destructive = true)`). A dialog
  opened to type one thing opens with the caret in it (`rememberRevealedFocus`).
- **Text that must fit** (bar titles, tab labels, stat values, the schedule's page title) shrinks before
  it ellipsizes (`TextAutoSize`), capped at its style's size and floored in **dp** — the system font
  scale is already in the starting size, so an sp floor rises with it and leaves nothing to shrink into.
  Pinned at 1.3× and 1.5× by `BottomBarLabelTest`/`BarTitleFitTest`; 2× is not.
- **Navigation**: bottom bar «Люди·Задания·Расписание·Отчёты·Ещё», opens on the schedule (the middle tab);
  Students+Groups share one tab via a segmented control. Which tab is marked is read off the back stack.
  Destinations cross-fade; predictive back is on.
- **Lists**: shared search rule (`domain/PeopleSearch`) across the three list screens — case-insensitive,
  substring, terms in any order. Empty lists always carry the way out (`EmptyState`). State is a
  `StatusBadge`, never a font style (`StatusBadgeSurfaceTest`). A lesson row leads with its date.
- **Schedule**: three views (Day/Week/Month) via segmented control; a day is always summarized as
  count·hours·money (`domain/DaySummary`); a "now" hairline on today only; completing a lesson is a
  revocable swipe (green panel); a cancelled lesson recedes, badge stays red. Can be narrowed to one
  student (dismissible chip — `FilterMenuAction` + `FilterChipRow`, shared with «Задания»). Booking is by
  length, not end time; a new booking starts on the selected schedule day so the tutor need not choose
  it again.
- **Homework filter**: offers only students and groups with current or archived homework, including
  students with group homework. The add dialog offers everyone so a first assignment can be given.
- **Reports**: number only where the chart shape adds meaning (column/stacked-bar/ranked-bars/plain text);
  CSV export's delimiter follows the decimal separator; can be saved or sent.
- **Design system**: curated indigo+teal Material 3 palette, dynamic color off by default. Amber = "on
  now"; green = "taught" (`KlasColors`, app-owned, unaffected by dynamic color). Golos Text typeface
  (Cyrillic-first, full Latin). Avatar colors key off id, not name.
- **Icons**: filled for actions and selected states, outlined for art and inactive glyphs. Empty-state
  pictures, decorative glyphs beside a line of text and settings leading icons are `Icons.Outlined`;
  buttons, menu items, chevrons and the selected tab stay filled. The two lesson-kind icons are exempt —
  a matched pair pinned on ink height by `LessonKindIconTest`.
- **Screen readers**: a swipe always has a twin action on the same node (`swipeAction`), and what is drawn
  behind a swipe row is not read; selection is `selected` semantics, not a tick alone; section titles are
  headings.
