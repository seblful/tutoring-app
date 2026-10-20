# UI conventions

Build screens from shared state and controls. Feature rules belong in the domain; see
[architecture](architecture.md) for write lifetimes and [testing](testing.md) for previews.

## Screen state

- Screens render named decisions (`ArchiveIntent`, `DeleteWarning`, `ScheduleEmpty`, `Balance`, etc.).
  Keep business rules in `ClassFormState`, `MoneyField`, `leaveIntent` and domain modules. Money/time
  decisions use injected `WallClock`, never direct system clock/zone reads in composables.
- Booking changes go through `BookingForm.change(intent, context)`; required reads return as `Lookup`.
- Use `Loadable<T>` (`Loading|Ready`) and skeletons, including forms. `rememberPlaceholderShown` delays
  placeholders 150 ms. `readyOrLoadingAfter` keeps previous content briefly during paging; label it
  with its own key/date, never the newly requested one.
- End flows with `derive`/`hold`, events with `ShowMessagesFor`/`rememberMessages`. Save through
  `appScope.write(events, failure)` or awaited `writeAwaited`; homework shares `HomeworkActions`.
- Screens open other apps via `rememberExternalApps(messages)`/`ExternalTarget`, files via
  `rememberCreateDocument`/`rememberOpenDocument` returning `DocumentRef`. These report missing handlers
  in a snackbar. Only system-settings links and permission prompts carry Android `Intent`/`Uri` details.
- Use `Messages` snackbars, never `Toast`. Forms that close on save ask beforehand when confirmation
  is needed: their snackbar would disappear with them.
- Rotation/resizing is handled in place by `MainActivity.configChanges`; dialog text still uses
  `rememberSaveable`. Save a `MoneyTextField`'s text and derive `MoneyField` from it.

## Layout and controls

- One `KlasScreen`/`Scaffold` owns insets, width cap, snackbar, back arrow and scroll behavior.
  `ScreenHeader` joins pinned controls to the bar: page-colored at rest, tinted together on scroll,
  no divider. Pass `page` when replacing a non-scrolling body to reset bar tint.
- Use `SegmentedPage` for fixed views, `PageStepper` for successive pages. Choices use
  `SegmentedChoice`, settings-shaped `ChoiceRow` (segments up to four, then a field), or shortlist chips
  with an escape hatch. Screen filters share `FilterMenuAction`/`FilterChipRow`.
- Follow `Spacing`; page sides use `gutter`. A row gets ripple room from card padding, keeping text
  aligned. Use top-level `ContentCard`, nested `ListItem`; top-level cards lead with a 40 dp `Avatar`
  or `IconAvatar`. Detail cards share `detailCard`: identity → `StatRow` → pinned `StickyViewMenu`.
- Cards press within their shape; full-width rows use `insetRowPress` (`outsetRowPress` for flush
  controls). Sheets draw `SheetHandle` with `dragHandle = null`, avoiding Material's tooltipped button.
- `SectionTitle` uses `titleSmall` and heading semantics; color only for meaning. Full-screen empty
  actions are tonal because the screen's add button is primary. Destructive confirmations use
  `ConfirmDialog(destructive = true)`; single-input dialogs focus via `rememberRevealedFocus`.
- `OneLineLabel`, bar/page titles and stat values shrink before ellipsizing (`TextAutoSize`). Floor
  sizes in **dp**, not sp, so font scaling leaves room to shrink. Tests cover 1.3×/1.5×; 2× is not guaranteed.
- Use `EmptyState` with a way out, `NoSearchResults`, `ArchivedBadge` and `FabListPadding` consistently.
  Status uses `StatusBadge`, never font style; on lessons it starts line two. Nested trailing money
  uses `TrailingAmount`; ordinary amounts stay ink, red means debt. Lesson rows lead with their date.

## Formatting and appearance

- Display money via `Money.money()` (`16 037,50 BYN`, `16,037.50 USD`); seed inputs with ungrouped
  `amount()` because `parseMoneyOrNull` treats a comma as decimal. CSV uses ungrouped amounts.
- Apply `AppLanguage` with `ProvideLocalizedResources`, never `Locale.setDefault()`. `StringCoverageTest`
  checks resource parity; language changes re-sync baked calendar text.
- Phone settings govern 12/24-hour time and week start, independent of app language; ask the application
  context. Use `WallClock.days()` for midnight rollover, `DayBounds` for day boundaries. Timezone changes
  re-query open calendars; form endings and booking use the same elapsed-time slots across DST.
- Indigo/teal Material 3, dynamic color off by default; app-owned amber means “on now”, green “taught”.
  Golos Text supports Cyrillic/Latin; avatar colors depend on ID.
- Material Symbols live in `KlasIcons`: filled for actions/selection, outlined for decoration/inactive
  art. Its KDoc explains adding icons. Settings rows have no leading icon; trailing `OpensScreenMark`
  names in-app navigation through `onClickLabel`, “open in new” marks external links. Lesson-kind icons
  are a matched pair tested on ink height.
- Haptics use the five moments in `Haptics`, wired through shared atoms/`ShowMessagesFor(refused = …)`;
  ordinary taps are silent.

## Motion, navigation and accessibility

- Tabs are People, Homework, Schedule, Reports, More; Schedule opens first, Students/Groups share People.
  Selection follows the back stack. Destinations cross-fade; predictive back is enabled.
- `navigateToTab` ignores reselection to avoid a pop/restore fade glitch. Navigating to the owning tab
  closes its pushed screens; another tab retains its stack (`owningTabRoute`). Tray “Reschedule” pushes
  the lesson form over the current screen. See [tray destinations](calendar-and-reminders.md#lesson-reminders).
- Lazy items use `animateInPlace`: movement only, no fade overlay. Key items by page when replacing views.
- Every swipe has a `swipeAction` on the same semantic node; background actions are not read. Use
  `selected` semantics (`TickedMenuItem`), headings, and `NamedIconButton`/`HoldToName` on bar icons/tabs.

## Feature conventions

- Schedule Day/Week/Month share `ScheduleViewModel.content`'s week assembly, avoiding reads for switches
  inside the week. Summaries are count·hours·money (`DaySummary`); show “now” only today. Completion is
  an undoable green swipe; cancelled lessons recede with a red badge. Book by duration, starting on the
  selected schedule day.
- Homework filters offer assignees with current/archived work, including a student's group work; the
  add dialog offers everyone, orders by nearest lesson and names the destination lesson. Card entry
  preselects its owner; tab entry preselects nobody.
- People lists share `PeopleSearch`: case-insensitive substrings, terms in any order. Reports use charts
  only when shape helps; CSV delimiter follows decimal separator and exports can be saved or shared.
