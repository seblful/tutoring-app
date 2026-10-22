# UI conventions

Build screens from shared state and controls. Feature rules belong in the domain; see
[architecture](architecture.md) for write lifetimes and [testing](testing.md) for previews.

## Screen state

- Screens render named decisions (`ArchiveIntent`, `DeleteWarning`, `ScheduleEmpty`, `Balance`, etc.).
  Keep business rules in `ClassFormState`, `MoneyField`, `leaveIntent` and domain modules. Money/time
  decisions use injected `WallClock`, never direct system clock/zone reads in composables.
- Booking changes go through `BookingForm.change(intent, context)`; required reads return as `Lookup`.
- Use `Loadable<T>` (`Loading|Ready`) and skeletons, including forms. `rememberPlaceholderShown` delays
  placeholders 150 ms; a skeleton's blocks share one `SkeletonPulse`. `readyOrLoadingAfter` keeps previous content briefly during paging; label it
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

- One `KlasScreen`/`Scaffold` owns insets, width cap, snackbar, back arrow and scroll behavior. The cap
  (`MaxContentWidth`, 600 dp) holds a column of rows to a readable width; a body that lays items out in
  columns lifts it (`maxContentWidth`) — homework cards are an adaptive grid — while `SegmentedPage`
  keeps its tabs at the cap. The bar's title and actions and the FAB line up with the capped column; the
  bar's tint, and a `ScreenHeader`'s, still span the screen. From a medium window on the margins are
  Material's 24 dp (`windowMargins`), panes included, and content and bar keep clear of a camera cutout.
  `ScreenHeader` joins pinned controls to the bar: page-colored at rest, tinted together on scroll,
  no divider. Pass `page` when replacing a non-scrolling body to reset bar tint.
- Edge to edge: `KlasScreen` pads its body's top and sides and hands the bottom inset over as
  `bottomInset`, so rows pass under the system navigation bar. A list adds it to `contentPadding`
  (`plusInset`), a scrolling column pads by it inside its scroll, and anything else pads by it.
  It is zero on a tab (the tab bar takes it) and under the keyboard on an `imeAware` form.
- Use `SegmentedPage` (tabs, no divider) for fixed views, `PageStepper` for successive pages; both
  page titles are ink. A held tab tints a rounded shape around its label, not Material's full-cell ripple. Choices of a value use `SegmentedChoice`, settings-shaped `ChoiceRow` (segments
  up to four, then a field), or shortlist chips with an escape hatch. Screen filters share
  `FilterMenuAction`/`FilterChipRow`. Menus draw options through `MenuOptions`: past 12 it adds a
  search and draws the first 30 matches, since a dropdown composes every item when it opens.
- A card that can hold hundreds of rows (a report's per-student lines, its debts) is one list item per
  row, drawn as `CardSlice`s of one surface, not one item composing them all.
- Compose Styles (experimental, opted in) are for the app's own foundation-built atoms only — `StatusBadge`
  so far: defaults live in `KlasStyles`, the caller's `style` goes last. Material components keep their
  parameters until Material supports Styles; a Style cannot read `MaterialTheme`, so it uses `Shapes`/`Typography`.
- Shapes are Material's baseline scale (`Shape.kt`); search opens Material's search input in place of the
  bar's title (`SearchField`).
- Follow `Spacing`; page sides use `gutter`. A row gets ripple room from card padding, keeping text
  aligned. Use top-level `ContentCard`, nested `ListItem`; top-level cards lead with a 40 dp `Avatar`
  or `IconAvatar`. Student and group cards are overviews (`OverviewSection.kt`): under the large title, one muted
  facts line (a student's contact and lesson format with call/Telegram/email icons; a group's subject and
  rate), the next lesson (`NextLessonCard`, `primaryContainer`, its «+» books), a headline card (the
  student's balance with its payment button; the group's earnings), then `OverviewSection`s with their
  latest few rows and «See all» to the full list (`StudentList`/`GroupList` routes, beside People like the
  card). Short lists — a student's subjects, a group's members — are shown in full.
- Items sit on `itemSurface`, sections (`SectionCard`, `StatRow`, report slices) on `sectionSurface`:
  both above the `surfaceContainer` the bar tints to. Settings and More group rows on cards too
  (`SettingsGroup`), their `ListItem`s transparent (`settingsRowColors`).
- Cards press within their shape; full-width rows use `insetRowPress` (`outsetRowPress` for flush
  controls). Sheets draw `SheetHandle` with `dragHandle = null`, avoiding Material's tooltipped button;
  given `onDismiss`, it keeps the handle's spoken name and dismiss action.
- Indigo marks what is tapped or selected (tabs, switches, links, the selected day, "now", status
  badges); times, amounts, values, counts and decorative icons are ink or `onSurfaceVariant`.
- `SectionTitle` uses `titleSmall` and heading semantics; color only for meaning. Full-screen empty
  actions are tonal because the screen's add button is primary. Destructive confirmations use
  `ConfirmDialog(destructive = true)`, and any text button that loses something
  `destructiveTextButtonColors`; single-input dialogs focus via `rememberRevealedFocus`. A button with an
  icon uses Material's 18 dp icon and icon padding. Times are picked in Material's time dialog, whose
  toggle switches the dial for typing.
- `OneLineLabel`, bar/page titles and stat values shrink before ellipsizing (`TextAutoSize`). Floor
  sizes in **dp**, not sp, so font scaling leaves room to shrink. Tests cover 1.3×/1.5×; 2× is not guaranteed.
- Use `EmptyState` with a way out, `NoSearchResults`, `ArchivedBadge` and `FabListPadding` consistently.
  Status uses `StatusBadge`, never font style; on lessons it starts line two. A pill and its words share a
  baseline (`BadgeLine`): centred, they give `ListItem` two baselines and a three-line row. Nested trailing
  money uses `TrailingAmount`; ordinary amounts stay ink, red means debt; a settled student shows no
  balance. Lesson rows lead with their date; card times use `ColumnTime`
  (tabular, widest-time width, measured once per list by `rememberColumnTimeWidth`). A kind icon prefixes a name; the lines below start on the icon's edge, not
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
  context. `ProvideClockFormat` at the root asks for the clock once, and again on every resume. Use `WallClock.days()` for midnight rollover, `DayBounds` for day boundaries. Timezone changes
  re-query open calendars; form endings and booking use the same elapsed-time slots across DST.
- Indigo/teal Material 3, dynamic color off by default; app-owned amber means “on now”, green “taught”.
  Dark containers keep the dark accents' chroma, not the seed's (`DarkPaletteTest`). Every scheme role is
  set, inverse and fixed ones included. The phone's contrast setting (Android 14+) picks Material's
  medium or high scheme, generated from the standard palettes into `ContrastColors.kt` by
  `uv run art/contrast_schemes.py` — rerun it after editing `Color.kt`. Under dynamic color the app-owned
  hues and avatar colours are harmonized toward the wallpaper's primary (`harmonize`). Text tinted below
  full strength, a status pill's included (`readableOn`), keeps 4.5:1 (`ContrastSchemesTest`).
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
  Selection follows the back stack. Destinations arrive by Material's fade through; predictive back
  shrinks the leaving screen over the one beneath (`predictiveBackPop`). The tabs are Material's short
  navigation bar on a phone, a landscape one included, and its wide rail from medium width
  (`NavigationSuiteScaffold`). The rail's header carries a tab's FAB (`RailAction`, decided per entry so a
  fading screen keeps its own) and keeps a FAB's height without one; a pushed screen keeps its own FAB.
  Side camera cutouts are cleared once, around the whole suite.
- Navigation 3 shows one flat back stack of `Screen`s (`KlasNavigator`): a route plus an id, so the same
  card opened twice is two entries. Per-tab stacks were rejected: a tray tap may put a tab over a form from
  another tab. `KlasGraph` is one exhaustive `when` over the sealed `Route`.
- A screen's back arrow closes that screen (`KlasNavigator.close`), not whatever is on top: a screen stays
  tappable while it fades out, and a second tap used to close the one beneath it too.
- `NavDisplay` owns system back for the stack. Back that stays inside a screen (a dirty form's
  `leaveGuard`, an onboarding step) uses `NavigationBackHandler`, never the legacy `BackHandler`. Where
  that back changes what is on screen and `NavDisplay` cannot preview it, the same state drives
  `Modifier.predictiveBack` so the gesture still shows where it leads.
- `navigateToTab` ignores reselection to avoid a re-targeted fade. Navigating to the owning tab closes its
  pushed screens; another tab's screens are set aside and keep their view models and saved state
  (`KlasNavigator.live`). All tray actions preserve a pushed screen beneath their destination; Back returns
  to its draft. See [tray destinations](calendar-and-reminders.md#lesson-reminders).
- With room for two panes, People and Settings are list-detail layouts (Material `ListDetailSceneStrategy`,
  one scene per list): a student or group card beside the People list, a settings page beside the index. A
  detail opened from its list replaces the one shown, is marked on the list and has no back arrow; opened
  elsewhere it fills the window. A student's full list stacks on their card in the same pane, with a back
  arrow. The tabs stay beside People, a tab, and not beside Settings, a pushed screen, whose back arrow
  leaves with the page. On an expanded, unfolded window (`roomForSupportingPane`)
  Schedule's month
  and Reports are supporting-pane layouts up to `SupportingPaneMaxWidth`: the month grid beside the day it
  opens, the period's summary beside its breakdowns and debts. Other screens stay one pane.
- Lazy items use `animateInPlace`: movement only, no fade overlay. Key items by page when replacing views.
  Every lazy item has a key and a `contentType`.
- Motion uses Material's standard springs and easings from `KlasMotion` (spatial for movement, effects
  for colour and fades); material3 1.4 keeps `MotionScheme` internal. Pops use its one bouncy spring.
- Launch: the splash mark's page turn (`ic_splash_logo`, an animated vector at rest before Android 12),
  then a fade through into the app (`fadeThrough`) once settings load. Neither delays the first screen.
  Onboarding's steps and the way from setup into the app (`RootSwitch`) fade through too; a step left by a
  released back gesture fades from where it was dragged (`predictiveBackRelease`).
- Inside a screen, what comes and goes in a column grows from its top and fades (`Reveal`: folds, a ticked
  subject's price, a lesson sheet's homework); a bar whose text changes length animates its size; a pinned
  header reserves a line rather than changing height as pages turn. A moving selection (a calendar day)
  fades, and report bars move to a new period's values, both drawn from animated state. Page, view and tab
  bodies still swap in one frame.
- State that moves every frame — a back gesture's progress, a tint or pop animating, a drag — is read in
  layout or draw (`graphicsLayer`, `drawBehind`, `offset {}`), not in composition. A list row does no work
  that every row shares (a measured width, the clock format, a pulse): it is done once above the list.
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
- The booking form is cards in the order the tutor answers — Who, When, then one details card of
  Length, Repeat (new bookings only), Price and Format. A new booking picks who from one dropdown like the subject's (`TargetChoices`: recently taught
  first, students and groups together, then adding a student or a group), so there is no Student/Group
  switch; an edit keeps its kind. The details card's rows (`DetailsSection`) are folded to one line saying
  what each is set to — Price the rate, or in red that the rate is all that is missing
  (`rateNeeded`); Format in person or remote — pressed with a rounded `outsetRowPress` tint and opened in place by growing downwards
  (`Fold`), one at a time; never a dialog or sheet. Opened, each is a value between tonal −/+ (`StepRow`,
  `Steps`): Length ±15 min, its value tapped to type hours and minutes (`DurationPickerModal`); Repeat as lessons ±1, one being once
  (`ClassForm.withLessons`), the count tapped to type it (`CountPickerModal`), and weekly/fortnightly segments; Price the typed rate
  ± a step that suits its size (5 at 40, 100 at 1 500, so any currency); Format as segments. It alone saves from a bottom `BookingSaveBar`, not the bar's
  `SaveAction`: the bar shows the booking it will make, or names what is missing or in the way
  (`ClassFormState.saveBar`, `Ready` exactly when `canSave`). When opens on the schedule's day, where bookings
  start, so it has no calendar: a day row steps a day either way and its date opens the date dialog,
  over how many other lessons that day holds (or asks for a day). The start is a stepper: the time in
  large numbers between −15 and +15 tonal buttons, each moving the lesson a quarter hour, past a lesson
  in the way and anywhere in the day (`ClassFormState.stepped`, `DayTimeline.shifted`). The first press
  takes the first free time (`DayTimeline.firstFree`); a tap on the time opens the time dialog. The time
  is red, over a line naming the lesson in the way, only while it is taken. The day's lessons stay data
  (`DayTimeline`), not drawn: a draggable timeline was more form than setting a time needs.
- Lesson context opens homework, payment entry and profiles for the named student/group. Group payments
  first ask who paid; completing a lesson never creates a payment. Profiles book through the same form.
- Setup detours push over the unfinished booking/group form and return created IDs through the result bus
  (`ResultEffect`). The parent selects the result without resetting its date, time or other edits. Group eligibility
  can be repaired with an individual subject price in place; group lessons still use the group rate.
- Homework filters offer assignees with current/archived work, including a student's group work; the
  add dialog offers everyone, orders by nearest lesson and names the destination lesson. Card entry
  preselects its owner; tab entry preselects nobody. Reassignment previews the first lesson after the
  original given time, including historical lessons. Rows carry Send and a Done/Restore icon button,
  the swipe's visible twin, sharing its undo.
- Students and groups are one `PeopleCard` anatomy, roomier than a lesson card: avatar; name with a balance or `ArchivedBadge` at its
  end; one line of facts — `LessonLine` first, as the line is cut at its end: the next lesson («Today
  16:00», a weekday within a week, else a date) or, with none booked, the last · subjects (a group's
  subject and head count). Rates and rosters live on the detail pages. The line re-reads per minute,
  so its view-model tests advance time by an amount.
- Settings sections group by subject, not by when a value applies: Lessons (duration, auto-complete)
  and Billing (currency, group rate). Rows are `ListItem`s; a form field never sits in a settings card.
  Backup is built from the same `SettingsGroup` cards: the last-backup row, then export and import.
- People lists share `PeopleSearch`: case-insensitive substrings, terms in any order. Reports use charts
  only when shape helps; CSV delimiter follows decimal separator and exports can be saved or shared.
