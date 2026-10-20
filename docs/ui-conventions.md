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
- Collect with `collectAsStateWithLifecycle`, so a backgrounded screen stops its flows. End flows with
  `derive`/`hold`; a table several flows combine is read once through `shared`. Events use
  `ShowMessagesFor`/`rememberMessages`. Save through
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
- Use `SegmentedPage` (tabs, no divider) for fixed views, `PageStepper` for successive pages; both
  page titles are ink. Choices of a value use `SegmentedChoice`, settings-shaped `ChoiceRow` (segments
  up to four, then a field), or shortlist chips with an escape hatch. Screen filters share
  `FilterMenuAction`/`FilterChipRow`. Menus draw options through `MenuOptions`: past 12 it adds a
  search and draws the first 30 matches, since a dropdown composes every item when it opens.
- A card that can hold hundreds of rows (a report's per-student lines, its debts) is one list item per
  row, drawn as `CardSlice`s of one surface, not one item composing them all.
- Follow `Spacing`; page sides use `gutter`. A row gets ripple room from card padding, keeping text
  aligned. Use top-level `ContentCard`, nested `ListItem`; top-level cards lead with a 40 dp `Avatar`
  or `IconAvatar`. Detail cards share `detailCard`: identity → `StatRow` → pinned `StickyViewMenu`.
- Items sit on `itemSurface`, sections (`SectionCard`, `StatRow`, report slices) on `sectionSurface`:
  both above the `surfaceContainer` the bar tints to. Settings and More group rows on cards too
  (`SettingsGroup`), their `ListItem`s transparent (`settingsRowColors`).
- Cards press within their shape; full-width rows use `insetRowPress` (`outsetRowPress` for flush
  controls). Sheets draw `SheetHandle` with `dragHandle = null`, avoiding Material's tooltipped button.
- Indigo marks what is tapped or selected (tabs, switches, links, the selected day, "now", status
  badges); times, amounts, values, counts and decorative icons are ink or `onSurfaceVariant`.
- `SectionTitle` uses `titleSmall` and heading semantics; color only for meaning. Full-screen empty
  actions are tonal because the screen's add button is primary. Destructive confirmations use
  `ConfirmDialog(destructive = true)`; single-input dialogs focus via `rememberRevealedFocus`.
- `OneLineLabel`, bar/page titles and stat values shrink before ellipsizing (`TextAutoSize`). Floor
  sizes in **dp**, not sp, so font scaling leaves room to shrink. Tests cover 1.3×/1.5×; 2× is not guaranteed.
- Use `EmptyState` with a way out, `NoSearchResults`, `ArchivedBadge` and `FabListPadding` consistently.
  Status uses `StatusBadge`, never font style; on lessons it starts line two. A pill and its words share a
  baseline (`BadgeLine`): centred, they give `ListItem` two baselines and a three-line row. Nested trailing
  money uses `TrailingAmount`; ordinary amounts stay ink, red means debt; a settled student shows no
  balance. Lesson rows lead with their date; card times use `ColumnTime`
  (tabular, widest-time width). A kind icon prefixes a name; the lines below start on the icon's edge, not
  hung under the name. The lesson sheet's header repeats the card's layout.
- Forms have one field style: tapped fields (`PickerField`) draw the outlined text field's own decoration,
  so labels float alike and fields space alike. Ticks in a form sit on its edge, not centred in 48 dp.

## Formatting and appearance

- Display money via `Money.money()` (`16 037,50 ₽`, `16,037.50 $`); seed inputs with ungrouped
  `amount()` because `parseMoneyOrNull` treats a comma as decimal. CSV uses ungrouped amounts.
- BYN uses the official vector sign at provisional [U+20C5](https://www.unicode.org/alloc/Pipeline.html),
  embedded in all Golos Text weights. After replacing fonts or editing `art/belarusian_ruble.svg`, run
  `uv run art/add_byn_glyph.py`. Its outline gains stroke weight with the font, using Golos's own
  hyphen thickness as the reference. System notifications fall back to `BYN`.
- Currency choices in setup (a dropdown) and Settings (a value row opening a list dialog) share 28
  currencies. Sort by the localized name and show the name, symbol and ISO code
  (`Belarusian ruble — ⃅ (BYN)`); codes distinguish shared symbols. Use the same label in change
  confirmation. Currency remains a label on stored amounts, without conversion or precision changes.
- Apply `AppLanguage` with `ProvideLocalizedResources`, never `Locale.setDefault()`. `StringCoverageTest`
  checks resource parity; language changes re-sync baked calendar text. Setup and Settings share
  `LanguageChoices`: System first, followed by English and Русский in autonym order.
- Phone settings govern 12/24-hour time and week start, independent of app language; ask the application
  context. Use `WallClock.days()` for midnight rollover, `DayBounds` for day boundaries. Timezone changes
  re-query open calendars; form endings and booking use the same elapsed-time slots across DST.
- Indigo/teal Material 3, dynamic color off by default; app-owned amber means “on now”, green “taught”.
  Dark containers keep the dark accents' chroma, not the seed's (`DarkPaletteTest`).
  Golos Text supports Cyrillic/Latin, with body/label tracking tightened from M3's Roboto values;
  avatar colors depend on ID.
- Material Symbols live in `KlasIcons`: filled only for selection and state (the selected tab, a done tick);
  actions and decoration are outlined, so a bar's or sheet's icons share one stroke weight. Its KDoc
  explains adding icons. Settings categories lead with a decorative `IconAvatar`; preference controls have
  no leading icon. Trailing `OpensScreenMark` names in-app navigation through `onClickLabel`, “open in new”
  marks external links. Lesson-kind icons are a matched pair tested on ink height.
- Haptics use the five moments in `Haptics`, wired through shared atoms/`ShowMessagesFor(refused = …)`;
  ordinary taps are silent.

## Motion, navigation and accessibility

- Tabs are People, Homework, Schedule, Reports, More; Schedule opens first, Students/Groups share People.
  Selection follows the back stack. Destinations cross-fade; predictive back is enabled.
- `navigateToTab` ignores reselection to avoid a pop/restore fade glitch. Navigating to the owning tab
  closes its pushed screens; another tab retains its stack (`owningTabRoute`). Tray “Reschedule” pushes
  the lesson form over the current screen. See [tray destinations](calendar-and-reminders.md#lesson-reminders).
- Lazy items use `animateInPlace`: movement only, no fade overlay. Key items by page when replacing views.
- A row swipe claims only its own direction (`SwipeToward`); the other way turns a `SegmentedPage`.
  Homework slides toward the tab it moves to — current right, done left — the way its tab cannot turn.
- Every swipe has a `swipeAction` on the same semantic node; background actions are not read. Use
  `selected` semantics (`TickedMenuItem`), headings, and `NamedIconButton`/`HoldToName` on bar icons/tabs.

## Feature conventions

- Settings opens an index with current values, ordered Language & appearance, Notifications,
  Lessons & billing, Calendar & invitations, then secondary Diagnostics. Backup, About and Subjects stay in
  More. Each settings destination owns its view model, dialogs and permission launchers. Its loaded
  state joins preferences and permission status so the index never shows invented delivery defaults.
- Notification status covers lesson reminders, all summaries and auto-completion reports. A blocked
  feature keeps its chosen switch state and offers system recovery; exact-alarm access concerns only
  lesson reminders. Dependent controls stay visible and explain how to enable them. Shared reminder
  timing is editable when lesson reminders **or** calendar sync is enabled, from either category.
- Schedule Day/Week/Month share `ScheduleViewModel.content`'s week assembly, avoiding reads for switches
  inside the week. Summaries are count·hours·money (`DaySummary`); show “now” only today. Completion is
  an undoable green swipe; cancelled lessons recede with a red badge. Book by duration, starting on the
  selected schedule day.
- Homework filters offer assignees with current/archived work, including a student's group work; the
  add dialog offers everyone, orders by nearest lesson and names the destination lesson. Card entry
  preselects its owner; tab entry preselects nobody.
- Students and groups are one `PeopleCard` anatomy: avatar; name with a balance or `ArchivedBadge` at its
  end; one line of facts — `LessonLine` first, as the line is cut at its end: the next lesson («Today
  16:00», a weekday within a week, else a date) or, with none booked, the last · subjects (a group's
  subject and head count). Rates and rosters
  live on the detail pages. The line re-reads per minute, so its view-model tests advance time by an amount.
- Settings sections group by subject, not by when a value applies: Lessons (duration, auto-complete)
  and Billing (currency, group rate). Rows are `ListItem`s; a form field never sits in a settings card.
  Backup is built from the same `SettingsGroup` cards: the last-backup row, then export and import.
- People lists share `PeopleSearch`: case-insensitive substrings, terms in any order. Reports use charts
  only when shape helps; CSV delimiter follows decimal separator and exports can be saved or shared.
