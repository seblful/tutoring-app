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
- A change that can be reversed is said with Undo instead of confirmed first: lesson status, moves and
  attendance, archiving, payment edits and deletions, homework, deleting an unused subject. Undo is
  guarded — it applies only while the thing is as the change left it, and says so when it is not. The
  calendar settles after `UndoWindow` (`settleAfterUndoWindow`). Confirms stay where the docs require them
  (deleting a taught lesson, forms that drop members) or where nothing can be undone (restore a backup).
- Rotation/resizing is handled in place by `MainActivity.configChanges`; dialog text still uses
  `rememberSaveable`. Save a `MoneyTextField`'s text and derive `MoneyField` from it.

## Layout and controls

- Generic parts live in `ui/designsystem/component` and take no domain types: `KlasRow` (typed
  `RowStart`/`RowEnd`, so a row has one trailing thing: an `Amount`, a value, a chevron, a switch or one
  named icon action), `Amount` (an `AmountText` worded with its meaning), `HeaderedCard` (title, count or
  summary and one `SectionAction` inside the card), `KlasButton` (`ButtonKind`), `KlasFab` (labelled,
  icon-only from 1.5× text, named once for TalkBack), `KlasDivider`, `FormDialog`/`ChoiceDialog`,
  `AccentCard` and `KlasSheet`. detekt's `ForbiddenImport` keeps the Material components they wrap out
  of features; a wrapper file says `@file:Suppress("ForbiddenImport")` and why. New lists use `KlasRow`;
  older ones move to it one list per change, reviewed in the goldens.

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
  page titles are ink. The schedule's page title is also its date picker (`PageNavigator`); «today» stays
  an icon beside it. A held tab tints a rounded shape around its label, not Material's full-cell ripple. Choices of a value use `SegmentedChoice`, settings-shaped `ChoiceRow` (segments
  up to four, then a field), or shortlist chips with an escape hatch. Screen filters share
  `FilterMenuAction`/`FilterChipRow`. Menus draw options through `MenuOptions`: past 12 it adds a
  search and draws the first 30 matches, since a dropdown composes every item when it opens. Every menu,
  a field's included, takes `MenuShape` and its colours (`KlasMenu`) and `MenuItem`: a rounded container, rows rounded inside
  it, the choice in force filled `secondaryContainer` and ticked (`TickedMenuItem`).
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
  facts line (`FactsLine`: one line when it fits, else a fact per line, never one broken) (a student's contact and lesson format with call/Telegram/email icons; a group's subject and
  rate), the next lesson (`NextLessonCard`, `primaryContainer`, its «+» books), a headline card (the
  student's balance with its payment button; the group's earnings), then `OverviewSection`s with their
  latest few rows and «See all» to the full list (`StudentList`/`GroupList` routes, beside People like the
  card). Short lists — a student's subjects, a group's members — are shown in full.
- Rows on an overview have one trailing slot and no bin (R1). A lesson row: date and subject, status,
  the amount trailing for a billed lesson only — a planned or cancelled one shows none, only billed amounts
  being money; no person glyph, every row is theirs. A payment row: the day as title, the method under it, the amount
  trailing; deleting is in its dialog, with Undo. A subject row: the rate trailing with a chevron, its last
  change under the name ("since 1 Sep · was 20 $/h"); a tap opens the rate, where Remove also lives.
- A student's lesson history is a card per month (`LessonMonth`) titled by the month, summed as held
  lessons · hours · earned (planned money is a forecast, so it is not summed). Its chips are Planned,
  Taught and Cancelled with counts, each tapped again to let go; a student taught both ways adds the kinds.
- A group's overview lists members before lessons: each with "Present 7 of 8" of the taught lessons they
  were booked into (not marked absent counts as there) and, when they owe, the debt. Its facts line says
  the rate as "12 $/h per student". The group form runs name, subject, rate, place, members, note; ticks
  lead the member rows, a roster longer than eight can be searched, and the rate field's hint carries its
  last change.
- People's students and groups are one surface with dividers (`SurfaceRow`, `SlicePosition`; D3), the
  divider drawn over each row's edge so rows keep one height; lessons stay a card each. Each side has its
  own orders behind the bar's sort button, tinted when not the first: students by name or debt, groups by
  name or next lesson. The archive is last in every order. Groups narrow by subject once they are taught two.
- Filters are a `FilterBar`: at most three chips, wrapped and never scrolled sideways (they sit in pages
  that swipe), the rest behind a «Filter» chip that names the one chosen (R5). Above the list, not in it,
  so a filter that leaves nothing can still be taken off. A filter with a menu is a `MenuChip` named by
  its choice. The schedule's row (`ScheduleFilterRow`) is two of them — Everyone/a student/a group, and
  a status — with the Day view's summary beside them, so its header keeps one height.
- Items sit on `itemSurface`, sections (`SectionCard`, `StatRow`, report slices) on `sectionSurface`:
  both above the `surfaceContainer` the bar tints to. Settings groups rows on cards too
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
  (tabular, widest-time width, measured once per list by `rememberColumnTimeWidth`), the end under the
  start in a quieter style, so back-to-back lessons and gaps read without a time grid (D4). Ranges are
  `timeRangeText`. A kind icon prefixes a group lesson's name only — the row that differs, and lists its
  members — and the lines below start on its edge, not hung under the name.
- The lesson sheet (`ClassActionsSheet`) opens on the day, hours and length (it also opens from the week, a
  reminder and Today), then who, then the status line with the rate. Then what is done most: «Taught»
  filled, «Reschedule» and «Edit» tonal; a taught lesson's tick stays spent, a cancelled one's says why it
  cannot be marked yet. Status is acted on, never chosen from segments: «Cancel lesson» and «Return to
  planned» are rows near Delete, each with its guarded Undo. Attendance is a value row per class
  (`LessonAttendance`), then homework, «Set homework», «Add a payment», «Book the next one like this» (a
  prefilled booking a week on, `ClassEdit.like`) and «Open profile ›».
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
- First run asks only what a lesson needs. Step 1 is the basics, all pre-set: language, currency and the
  usual lesson length (Settings' `LessonLengthSetting`). Step 2 is subjects: suggestion chips that add a
  subject and take it back, plus a name typed in place (Enter adds it). Step 3 is the first student's name and subject prices
  (`StudentFormContent(contacts = false)`); phone, email, note and place are the card's to ask. The student
  form ticks a lone subject for the tutor, and while the usual length is not an hour each price says what
  one lesson costs. Each step shows its number over a progress bar, offers Skip in the bar, and moves on
  from the `FormSaveBar` every form saves from (R3). No step books a lesson: setup stays a minute long.
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
- Features ask for a meaning, not a scheme role: `klasSemantics.debt`, `.overdue`, `.destructive`,
  `.validation`, `.cancelled`, `.attention`, and the `blocking`/`advisory` banner pairs. Each aliases a
  tested scheme role, so contrast and dynamic color come with it. The error-hued meanings share a hue, so
  each must come with its word or icon. Feature code does not read `colorScheme.error` or `.tertiary`
  for meaning; review catches it, since a property read cannot be banned by import.
  Golos Text supports Cyrillic/Latin, with body/label tracking tightened from M3's Roboto values;
  avatar colors depend on ID.
- Material Symbols (Rounded family, weight 400) live in `KlasIcons`: filled only for selection and state
  (the selected tab, a done tick); actions and decoration are outlined, so a bar's or sheet's icons share
  one stroke weight. Its KDoc explains adding icons. Settings categories lead with a decorative `IconAvatar`; preference controls have
  no leading icon. Trailing `OpensScreenMark` names in-app navigation through `onClickLabel`, “open in new”
  marks external links. Lesson-kind icons are a matched pair tested on ink height.
- Haptics use the five moments in `Haptics`, wired through shared atoms/`ShowMessagesFor(refused = …)`;
  ordinary taps are silent.

## Motion, navigation and accessibility

- Every way into the app but its icon is a `TrayNavigation` entry, so each lands where the same tap in
  the tray or on the widget does: notifications, widgets, the launcher's long-press shortcuts
  (`AppShortcuts`: Book a lesson, Today, Homework, Debts — published in the app's language), `klas://`
  links (today, schedule, people, homework, reports, debts, book, settings, `lesson/<id>`, `day/<date>`,
  `student/<id>`;
  they only ever open somewhere, never write) and Ctrl-chords on a hardware keyboard (`KeyboardShortcuts`:
  Ctrl+1–5 the tabs, Ctrl+N a new lesson, Ctrl+, Settings, listed under Meta+/). Bare keys work when no
  field has the keyboard (`MainActivity.onKeyDown`): N a new lesson, T Today, / the search of the screen in
  front (`SearchKey`, claimed by `KlasScreen`). ← → page dates on the focused date title only, never on
  rows, where arrows move focus.
- Tabs are Today, Schedule, People, Homework, Reports; Today opens first (`TopDestination.START`),
  Students/Groups share People. Settings is a gear (`SettingsGear`) last among every tab's actions, pushed over
  that tab, not a tab: it is visited, not worked in. Today links out rather than repeating a tab: a lesson
  opens its sheet on the schedule and the debts open on Reports, both the way a tray tap does (`Arrival`).
  Today leads with the lesson now or next («Next · in 55 min», «Open»), then what is over and unmarked, then
  marks made outside the app (widget, reminder) in the last ten minutes with their Undo (`RecentMarks`),
  then the day as one card — count · hours · billed, «Schedule» — whose lessons lead with the round
  mark-done button (`DoneButton`, `RowStart.Done`): named per lesson, off until it has begun, a spent tick
  once taught. Owed lists the three who owe most, each with their last payment, and «See all».
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
- `navigateToTab` ignores reselection to avoid a re-targeted fade; instead a tap on the tab on show sends its
  page back to the top (`TabReselect`: each tab's list claims it with `ScrollsToTopOnReselect`, and
  `KlasScreen` opens a collapsed large title). Navigating to the owning tab closes its pushed screens; another tab's screens are set aside and keep their view models and saved state
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
- A long-press only ever selects (R7), and a right click is a long-press (`rightClickAsLongPress`). On the schedule it picks lessons (`LessonBatch`, `LessonPicking`): the
  bar then counts them and offers Mark taught and Cancel, each undone for the lot as one; a tap picks too and
  the swipe rests while any are picked; Back or the arrow lets go. Only planned lessons are acted on.
  Current homework picks the same way (`HomeworkBatch`, `HomeworkPicking`) for Mark done; the archive and the
  student and group cards do not pick. The bar's title and Back are `titleOrPicked` and `PickBackHandler`.
  Homework slides toward the tab it moves to — current right, done left — the way its tab cannot turn.
- Every swipe has a `swipeAction` on the same semantic node; background actions are not read. Use
  `selected` semantics (`TickedMenuItem`), headings, and `NamedIconButton`/`HoldToName` on bar icons/tabs.

## Feature conventions

- Settings opens an index with current values, ordered Language & appearance, Notifications,
  Lessons & billing, Calendar & invitations, then a second card of Subjects, Backup, About and Diagnostics —
  what the «Ещё» tab held — each saying its own live («3 subjects», «Never · set up weekly», the version,
  «No crashes recorded»; `IndexFacts`). Each settings destination owns its view model, dialogs and permission launchers. Its loaded
  state joins preferences and permission status so the index never shows invented delivery defaults.
- Notification status covers lesson reminders, all summaries and auto-completion reports. A blocked
  feature keeps its chosen switch state and says so as a blocking `Banner` with «Allow» — on the index, the
  Notifications page and under auto-completion; exact-alarm access concerns only lesson reminders. Banners
  come in two kinds (`BannerKind`): blocking (something does not work) and advisory (worth doing: a backup
  missing or over two weeks old, on Today and Backup), each a polite live region. A value row's value trails
  as text (`ValueRow`: «1 hour before», the currency), never a ▾ that reads as a form field. Dependent controls stay visible and explain how to enable them. Shared reminder
  timing is editable when lesson reminders **or** calendar sync is enabled, from either category.
- Status is never colour alone. Month and week cells draw a `StatusMark` per lesson — taught a dot,
  planned a ring, cancelled a cross — up to `CalendarLayout.MAX_MARKS` and «+n», with a `StatusLegend`
  under the grid; each cell still speaks its counts. The report chart's accent bar is named under it
  («Best month»), since the chart itself is not read aloud.
- Schedule Day/Week/Month share `ScheduleViewModel.content`'s week assembly, avoiding reads for switches
  inside the week. Summaries are count·hours·billed money (`DaySummary`) — a day still to come is lessons and
  hours, no money; show “now” only today. Day lessons are cards; Week and Month draw each day as a section
  card whose top slice is the date and summary (`SliceHeader`) and whose lessons are its slices
  (`SurfaceRow`). A Week day with planned lessons offers «Cancel day» (one batch, one Undo); Month's chosen
  day offers «Open day» to the Day view; tapping a strip day scrolls to its section. Completion is
  an undoable green swipe; cancelled lessons recede with a red badge. An ended lesson nobody has marked
  badges «Not marked» in grey (`LessonPhase.OVERDUE`), never «Planned»: unmarked lessons are unbilled. Book
  by duration, starting on the selected schedule day.
- The booking form is cards in the order the tutor answers — Who, When, then a titled «Details» card of
  Length, Repeat (new bookings only), Rate and Place. The day line under the date writes out its lessons and
  the free time a lesson still fits in («2 lessons · free 9:00–11:00, from 19:30», `DayTimeline.freeFor`);
  the time's −/+ are named by where a press goes («Later, to 19:30»), and before a start is set a line says
  the first press takes the first free time. A new booking picks who from one dropdown like the subject's (`TargetChoices`: recently taught
  first, students and groups together, then adding a student or a group), so there is no Student/Group
  switch; an edit keeps its kind. The details card's rows (`DetailsSection`) are folded to one line saying
  what each is set to — Price the rate, or in red that the rate is all that is missing
  (`rateNeeded`); Format in person or remote — pressed with a rounded `outsetRowPress` tint and opened in place by growing downwards
  (`Fold`), one at a time; never a dialog or sheet. Opened, each is a value between tonal −/+ (`StepRow`,
  `Steps`): Length ±15 min, its value tapped to type hours and minutes (`DurationPickerModal`); Repeat as lessons ±1, one being once
  (`ClassForm.withLessons`), the count tapped to type it (`CountPickerModal`), and weekly/fortnightly segments; Price the typed rate
  ± a step that suits its size (5 at 40, 100 at 1 500, so any currency); Format as segments. Like every full-screen form it saves from the bottom `FormSaveBar` (D5;
  dialogs keep their buttons); booking's (`BookingSaveBar`) shows the booking it will make, or names what is
  missing or in the way (`ClassFormState.saveBar`, `Ready` exactly when `canSave`). The student and group
  forms name their gaps the same way (`FormGap`, `StillNeeded`) and, once ready, sum up what they will save
  (`FormSummary`); a new one's button names the act («Add student», «Create group»), an edit's says Save. A
  form of more than three fields is a screen, not a dialog. Adding and correcting a student is one form
  (`StudentFormScreen`, `StudentFormViewModel.edit`) with their subjects: the rate sits on its subject's row
  (under it past 1.3× font), a subject they have lessons in stays ticked (`EnrollmentRepository.setSubjects`),
  and a moved rate is logged as on the profile's rows. When opens on the schedule's day, where bookings
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
  original given time, including historical lessons. A row leads with a tick (empty circle → done, filled →
  not done) — the widget's tick, which Today's lessons to mark lead with too — the swipe's visible twin
  sharing its undo; the task text is the title, whom and when the line
  under it; Send sits behind the row's ⋮, its one trailing slot. The tab's current side is cut into
  sections by the lesson it is for — Past lessons, Today's lessons, Lessons in the next 7 days, Later lessons
  and No lesson booked (`HomeworkSection`) — and a row under them gives the lesson's time alone; nowhere does
  homework say «due», having no date but its lesson's. Its other side is "Done", never "Archive", which
  belongs to retired students and groups.
- Students and groups are one `PeopleCard` anatomy, roomier than a lesson card: avatar; name with a balance or `ArchivedBadge` at its
  end; one line of facts — `LessonLine` first, as the line is cut at its end: the next lesson («Today
  16:00», a weekday within a week, else a date) or, with none booked, the last · subjects (a group's
  subject and head count). Rates and rosters live on the detail pages. The line re-reads per minute,
  so its view-model tests advance time by an amount.
- Settings sections group by subject, not by when a value applies: Lessons (duration, auto-complete)
  and Billing (currency, group rate). Rows are `ListItem`s; a form field never sits in a settings card.
  Backup is built from the same `SettingsGroup` cards: the last-backup row, then export and import.
- People lists share `PeopleSearch`: case-insensitive substrings, terms in any order. Under the tabs, chips
  rather than bar icons (`StudentChips`, `GroupChips`): «Owes · N» narrows students to debtors, «Archived»
  lists the retired too, and the order is a «Sort: …» `MenuChip`; groups add a subject chip once two are taught. Reports use charts
  only when shape helps; CSV delimiter follows decimal separator, and the summary and the ledger can each
  be saved or shared from one Export menu. The hero carries Earned/Received and the period's lessons · hours · ahead; a student's
  income row opens them, with a chevron, as a debt's does. The funnel narrows the report, its chip under
  the period.

## Words

One word per thing, in every screen, notification and file; `GlossaryTest` fails on a retired word coming
back. Russian follows the same table.

| Thing | Say | Not |
|---|---|---|
| A lesson that happened | Taught; «Mark lessons taught automatically» | completed, auto-completion |
| Money per hour | Rate (rate per hour, rate per student) | price, priced |
| Finished homework | the Done side; Restore | Archive, Bring back |
| A retired student or group | Archived; Restore; Show archived | Show archive |
| Making a lesson | Book; screen «New lesson» | Book lesson |
| A student's page | Profile; «Open profile» | card, Open the card, Open student |
| Setting a rate | Set rate | Set price, Set subject price |
| A saved file | Backup saved · Report saved · Crash log saved | Data saved |
| Who homework is for | Assigned to | For |
| A group with no subject | No subject; the count beside «Members» | Not picked, Picked: 4 |
| Where lessons happen | Place: In person / Remote (RU «Где: Очно / Онлайн») | Format, Where lessons happen |
| Taking a change back | Undo; RU «Вернуть», since «Отменено» is a status and «Отменить» would read as cancel | RU «Отменить» on a snackbar |
| The add button | A verb of at most 14 characters: Book, New student, New group, Set homework | Book a lesson, Add a student |
| Search | named by its scope («Search students»), closed with ✕ | Student name, a crossed-out lens |
