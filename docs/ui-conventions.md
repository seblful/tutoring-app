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
  (`ui/schedule/booking/BookingForm.kt`); reads it needs come back as data (`Lookup`) through the same `change`.
- Every screen's loading/ready state is `Loadable<T>` (`Loading | Ready`), drawn as a skeleton, never a
  spinner; applies to forms too (a blank form is a real, wrong state until data arrives). A placeholder
  draws only after 150 ms of waiting (`rememberPlaceholderShown`), so a fast local read goes straight to
  content instead of blinking grey blocks. A query re-asked as the tutor pages keeps its previous answer
  up to a grace (`readyOrLoadingAfter`); what is drawn from it is labelled by the answer's own key (a
  day's content carries its date), never by the question just asked.
- `derive`/`hold` end every screen flow; `ShowMessagesFor`/`rememberMessages` end one-shot snackbar events;
  the guarded-write tail is `appScope.write(events, failure)` (application scope + `runCatchingCancellable`
  + failure event), and a write the screen waits on is `appScope.writeAwaited { }` — both in
  `ui/common/state/Writes.kt`. The homework writes the tab and both cards share are `ui/homework/HomeworkActions`.
- One-shot outcomes report through one snackbar helper (`ui/common/Messages`) — no `Toast`. A message
  about a save that closes its screen is lost with the screen, so such a form asks *before* saving instead.
- Turning the phone or resizing its window is handled in place (`configChanges` on `MainActivity`), so an
  open dialog and what was typed into it survive; typed dialog fields are still `rememberSaveable`. A money
  input is a `MoneyTextField` whose *text* is saved and the `MoneyField` derived from it
  (`MoneyFieldSurfaceTest`).

## Look and behaviour

- **Language**: `AppLanguage` (SYSTEM|RUSSIAN|ENGLISH) is a setting; pinned onto resources via
  `ProvideLocalizedResources`, never `Locale.setDefault()`. `StringCoverageTest` pins parity between
  `values/`/`values-ru/` (strings, plurals, format args, array lengths). A language change re-syncs the
  calendar (mirrored text is baked in).
- **Time**: `java.time` everywhere (desugared, minSdk 24); 12/24-hour and week-start both follow the
  *phone's* setting, not the app language; today rolls over at local midnight off the injected `Clock` (`Clock.days()`, `ui/common/state/Days.kt` —
  never a "today" read once per emission); a day as instants is `domain/schedule/DayBounds`.
- **Haptics**: five named moments only (`ui/common/Haptics`), wired into shared atoms (a refusal through
  `ShowMessagesFor(refused = …)`), never per-screen; ordinary taps stay silent.
- **Shell**: every screen is a `KlasScreen` (owns `Scaffold`, insets, width cap, snackbar host, back arrow,
  scroll behavior) — exactly one `Scaffold` per tree. Controls pinned under a compact bar (segments, a
  pager, a period) are a `ScreenHeader`: page-coloured at rest, no rule under them, tinted *with* the bar
  once content scrolls beneath — one block, one lower edge. A body that is one of a fixed few views is a
  `SegmentedPage`; a page read one at a time is a ‹ › `PageStepper`. A screen that swaps its body without
  scrolling passes `page` so the bar's tint resets with it. `Spacing` is the app's rhythm
  (`tight`…`sectionGap`). Card vs. row is by nesting depth (`ContentCard` top-level, `ListItem` nested); a
  top-level card leads with a 40 dp disc — a student's monogram (`Avatar`), else a glyph (`IconAvatar`).
  A press is never a grey block: cards press in their own shape, full-width rows through `insetRowPress`
  (`outsetRowPress` where the row's control sits flush with its column) as a rounded inset tint, and a
  bottom sheet draws `SheetHandle` itself with `dragHandle = null` — Material's handle is a tooltipped
  button (`PressSurfaceTest`). Detail cards are all
  identity → `StatRow` → one pinned view menu (`StickyViewMenu`, «Занятия ▾» — page-coloured at rest, the collapsed bar's colour once pinned), not a tab row — four
  tabs were too much to read at a glance — built by one `detailCard` (`ui/common/DetailCard.kt`), so the two
  cards cannot drift apart again. Held, a bar's icon names itself (`NamedIconButton`, over `HoldToName`) — top-bar
  icons and the bottom bar's tabs. One control per kind of question: `SegmentedChoice` (fixed few,
  swipeable), `ChoiceRow` (settings-shaped choice, segments up to 4 then a field), chips (shortlist +
  escape hatch). Text sits on the gutter: page sides are `Spacing.gutter`, and a tappable row that needs
  ripple room takes it from the card's padding, not from the text's edge.
- **Section titles and dialogs**: a section title is a `SectionTitle` — `titleSmall`, read as a heading —
  on forms, sheets, cards and settings alike; colour only when it marks something (a settings category,
  the selected day). A full-screen empty state's action is tonal: the screen's add button is its primary. A confirmation that loses something — delete, remove, clear, replace from a backup, discard
  typed input — says its button in `colorScheme.error` (`ConfirmDialog(destructive = true)`). A dialog
  opened to type one thing opens with the caret in it (`rememberRevealedFocus`).
- **Text that must fit** (bar titles, tab and segment labels — `OneLineLabel` — stat values, the
  schedule's page title) shrinks before it ellipsizes (`TextAutoSize`), capped at its style's size and floored in **dp** — the system font
  scale is already in the starting size, so an sp floor rises with it and leaves nothing to shrink into.
  Pinned at 1.3× and 1.5× by `BottomBarLabelTest`/`BarTitleFitTest`; 2× is not.
- **Navigation**: bottom bar «Люди·Задания·Расписание·Отчёты·Ещё», opens on the schedule (the middle tab);
  Students+Groups share one tab via a segmented control. Which tab is marked is read off the back stack.
  Destinations cross-fade; predictive back is on. Tapping the tab already showing does nothing
  (`navigateToTab`): switching to it would pop and restore the entry mid-fade, and a second tap in the
  first frames of the fade made the screen lurch (`TabReselectFadeTest`). A screen pushed over the tab asked
  for — a tray tap naming the schedule while a form opened from it is up — is closed by it, so the tap shows
  what it names; one pushed over *another* tab waits with that tab (`owningTabRoute`, shared with the bar's
  mark). «Перенести» from the tray opens the lesson's form *over* what is open, never in place of it.
- **List motion**: lazy list items animate only through `animateInPlace` — a move slides, nothing fades
  in or out, because a fading row keeps drawing over whatever replaces it. A list that shows several pages
  (the schedule's views and days) keys its items per page, so a swap is never read as every row moving.
- **Lists**: shared search rule (`domain/people/PeopleSearch`) across the three list screens — case-insensitive,
  substring, terms in any order. Empty lists always carry the way out (`EmptyState`); a search that matched nothing is
  `NoSearchResults`. «в архиве» is `ArchivedBadge`; a list under an add button pads with `FabListPadding`. State is a
  `StatusBadge`, never a font style (`StatusBadgeSurfaceTest`), and on a lesson it leads the second line —
  schedule card, lesson sheet, student and group cards — so the name or date above keeps the full width.
  A lesson row leads with its date. Money at a nested row's trailing edge is `TrailingAmount`; colour on
  an amount means something (a debt's red, the hero's fill), so other amounts are ink.
- **Schedule**: three views (Day/Week/Month) via segmented control, all fed by one assembly of the week
  holding the selected day (`ScheduleViewModel.content`) — Day and Month read their day out of it, so a
  view switch or a day tap inside the week reads nothing; a day is always summarized as
  count·hours·money (`domain/schedule/DaySummary`); a "now" hairline on today only; completing a lesson is a
  revocable swipe (green panel); a cancelled lesson recedes, badge stays red. Can be narrowed to one
  student (dismissible chip — `FilterMenuAction` + `FilterChipRow`, shared with «Задания»). Booking is by
  length, not end time; a new booking starts on the selected schedule day so the tutor need not choose
  it again.
- **Homework filter**: offers only students and groups with current or archived homework, including
  students with group homework. The add dialog offers everyone so a first assignment can be given.
- **Reports**: number only where the chart shape adds meaning (column/stacked-bar/ranked-bars/plain text);
  CSV export's delimiter follows the decimal separator; can be saved or sent.
- **Previews**: a shared atom gets a `@KlasPreview` in `src/debug/.../preview/`, which is also its
  screenshot test (see [Testing](testing.md)).
- **Design system**: curated indigo+teal Material 3 palette, dynamic color off by default. Amber = "on
  now"; green = "taught" (`KlasColors`, app-owned, unaffected by dynamic color). Golos Text typeface
  (Cyrillic-first, full Latin). Avatar colors key off id, not name.
- **Icons**: filled for actions and selected states, outlined for art and inactive glyphs. Empty-state
  pictures and decorative glyphs beside a line of text are `Icons.Outlined`; buttons, menu items,
  chevrons and the selected tab stay filled. Settings rows carry no leading icon (every title on one
  edge); a row that goes somewhere says so at its trailing edge — a chevron in the app (`OpensScreenMark`, decorative —
  the click's `onClickLabel` says where it leads), «open in new» out of it. The two lesson-kind icons are exempt —
  a matched pair pinned on ink height by `LessonKindIconTest`.
- **Screen readers**: a swipe always has a twin action on the same node (`swipeAction`), and what is drawn
  behind a swipe row is not read; selection is `selected` semantics, not a tick alone
  (`TickedMenuItem`, `TickedMenuSurfaceTest`); section titles are
  headings.
