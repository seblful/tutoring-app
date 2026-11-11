# Calendar and reminders

The database drives both mirrors; neither writes changes back into Klas.

This page describes Android. iOS makes the same decisions ahead of time and leaves them with the
system, and its calendar mirror has no guests or email reminder: see [iOS](ios.md#how-ios-differs).

## Calendar sync

- Opt-in, one-way through `CalendarContract`; the device's provider handles cloud sync. No Google
  Calendar REST API, Cloud project or OAuth flow is needed.
- `ClassRepository.write` mirrors after commit; `CalendarMirror` owns backfill and withdrawal.
- An undoable write through `LessonChanges` holds its lesson's mirror until the workflow settles it
  (the undo window closed, ~12 s) or the caller uses its undo handle,
  so an undone cancel never deletes and re-invites. Each receipt holds and releases its own hold, so a
  lesson a write left as it was holds nothing and a late settlement cannot end a later write's hold. The hold is in memory: a process killed inside the
  window leaves the event as it was until the lesson's next write.
- Completed events are refreshed but never created/re-created (`CalendarSyncPolicy.Action.REFRESH`).
- Event descriptions include current homework attached to that lesson: the student's own assignments
  and homework for that group lesson, oldest first. Other students' individual assignments are excluded.
  A homework change refreshes the event of the lesson it is attached to (both owners' after a
  reassignment). A lesson change also refreshes the first unchanged lesson after each affected
  assignment, the only other event it can move to or from. Empty descriptions clear homework that was
  completed, removed or attached to a different lesson.
- Editing a student, subject or group refreshes their events only when something an event shows
  changed: a name (titles) or a student's email (guest).
- An absent member of a scheduled lesson has no event, billed or not. Marking them present re-creates
  it; attendance no longer affects this policy once the lesson is completed.
- Email invitations are a separate, off-by-default setting; disabling it and re-syncing withdraws them.
  Invitations carry the event description, including homework when present; delivery and update emails
  are handled by the calendar account.
  The device keeps which addresses Klas invited, separately from guests added in the calendar. Changing or
  clearing a student's email retires the old invitation before sending new event content. Failed withdrawals
  stay pending across restarts and block content updates until removed. This ownership stays on the device,
  outside tutoring backups. Legacy events adopt the student's current or pre-edit address; older orphaned
  addresses whose ownership was never recorded cannot be distinguished from manually added guests.
- Settings → Calendar & invitations reports revoked access with a direct link to restore it. Reminder
  timing is shared with phone lesson reminders and stays editable while either feature is enabled;
  changing it refreshes existing calendar email reminders. Invitations are handled by the calendar account.
- Turning sync on backfills every lesson (`CalendarWork.RESYNC`). Changing invitations, reminder timing
  or language re-mirrors only lessons not yet over (`RESYNC_UPCOMING`); past events keep their guest,
  reminder and location label.
- A mirroring pass reads each name once and stores new event ids in batches of 50 per transaction;
  `DeviceCalendarSync` reads the calendar list at most every 30 s and replaces an email reminder in one
  `applyBatch`.

## Lesson reminders

- `AlarmManager` delivers reminders: exact when allowed, otherwise inexact. Changes re-derive the plan
  over a fixed horizon; reboot re-arms it.
- Upkeep runs after every lesson write, at app start, every 6 hours (`ExistingPeriodicWorkPolicy.UPDATE`,
  so installs arranged under an older interval move) and, while auto-completion is on, once at the next
  lesson end (`ReminderScheduler.scheduleNextPass`). The periodic pass only slides the week's window.
- A passed reminder time with a lesson not yet ended is still **owed** (`Reminders.Plan.due`). Do not
  re-arm or cancel it: Doze may be holding its alarm. Read through `notEndedWithin` so running lessons
  keep their alarms. `Reminders.shouldPost` re-checks state and lead time at delivery.
- A reminder's buttons are `TrayNotices`' (`TrayButton`): «Проведено» bills through a broadcast on
  Android and the notification category's action on iOS, both reaching `ReminderActions`. «Перенести» opens the lesson form over the current screen;
  an existing form waits underneath. Both re-check current legality because notifications can be stale.
- `TabArrivals` opens targeted tabs and binds each request to its resulting entry in one operation.
  `TrayNavigation` decodes `klas://` links and older builds' intent actions; the navigator preserves a pushed form's draft for Back.
  `Arrival` consumes each tap once, bound to that destination entry so an older tab cannot replay it:

  | Entry | Destination |
  |---|---|
  | Lesson reminder | Schedule on the lesson's current day, lesson sheet open |
  | Agenda / evening digest | Schedule on the day carried by the notification, even after midnight |
  | Auto-completion report | Latest day settled |
  | Debt digest | Reports, scrolled to debts |
  | Reminders summary, widget title and its lessons | The Today tab |

  A relaunch from Recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) goes nowhere. On Android 11 and
  below, Back finishes the activity, and Recents replays the intent that started the task: an old tap.

## Lock screen

- Private unless chosen (`Privacy`). Every notification is `VISIBILITY_PRIVATE` with a public version
  that keeps its neutral title and says «unlock to see details». Its channel redacts on a secure lock
  screen whatever the phone's own setting is: an app can set that only when it creates a channel, so
  each kind has a `.private` and a `.shown` channel and only the one in force exists. Choosing details
  swaps the set (`SettingsChange.movesChannels`), carrying over the importance, sound and vibration the
  tutor gave the old one; before the settings row is read, the set in place is kept.
- «Taught» from the tray bills the lesson, so it asks for the unlock first (`setAuthenticationRequired`).
- On a keyguard host the Today widget shows only its title and «unlock», unless
  `Privacy.lockScreenWidgets` is on (`listsLessons`). iOS redacts its rows on a locked phone under the
  same setting (`TodaySnapshot.privateWhenLocked`).
- The app lock (`AppLock`, `AppLockGate`) covers the app until `BiometricPrompt` succeeds with
  biometrics or the screen lock (`UNLOCK_WITH`), and again after `relockAfterMinutes` in the background
  on a monotonic clock. The app stays composed beneath, hidden from touch and screen readers, so the tutor
  returns where they were. Dialogs, sheets, menus and tooltips close their separate windows while locked;
  saveable dialog input returns after unlocking. New window wrappers use `UnlockedWindow`, placing its
  saveable provider inside the popup body because Android replaces the parent registry there.
  A phone with no screen lock is never locked out. Hiding in recents — on with
  the lock too — is `setRecentsScreenshotEnabled(false)` from Android 13 and `FLAG_SECURE` before it.

## Permissions and digests

- Ask for notifications in Settings and once at first booking (`shouldAskForNotifications`), never at
  first launch. Permission ports report grants/`canAsk`; Compose permission-request helpers show prompts.
  View models carry no Android permission names.
- After one refusal, Settings explains before asking again: a snackbar whose «Разрешить» shows the prompt
  (`PermissionPrompt.rationaleDue`, `Messages.ask`). The first-booking ask is skipped then, as that
  screen is leaving and cannot explain; Settings remains the way back.
- Settings → Notifications groups lesson reminders and the three summaries. `NotificationDelivery`
  reports global notification permission for every enabled type, including auto-completion reports,
  even when lesson reminders are off. `ReminderDelivery` adds exact-alarm status only for lesson reminders
  (`OFF|ON_TIME|BLOCKED|LATE`); `CalendarDelivery` reports sync permission (`OFF|ON|BLOCKED`). Recovery
  links open the relevant system page or request a grant, and status refreshes on return.
- Morning agenda, evening unmarked lessons and weekly debts are opt-in workers: lateness is acceptable.
  Upkeep schedules them, leaves due runs alone and sends nothing for an empty digest. No worker runs in
  the foreground: the manifest removes WorkManager's foreground service and `FOREGROUND_SERVICE`, and
  detekt forbids `ForegroundInfo` until both are restored.
- Digest times follow the device's wall clock/zone. `ClockChangeReceiver` runs upkeep immediately on a
  clock/timezone change. The evening digest asks about today's lessons, with no action button;
  auto-completion instead assumes they were taught.
