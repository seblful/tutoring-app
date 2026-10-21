# Calendar and reminders

The database drives both mirrors; neither writes changes back into Klas.

## Calendar sync

- Opt-in, one-way through `CalendarContract`; the device's provider handles cloud sync. No Google
  Calendar REST API, Cloud project or OAuth flow is needed.
- `ClassRepository.write` mirrors after commit; `CalendarMirror` owns backfill and withdrawal.
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
- «Проведено» bills through a broadcast. «Перенести» opens the lesson form over the current screen;
  an existing form waits underneath. Both re-check current legality because notifications can be stale.
- `TrayNavigation` opens the named destination above any pushed form, preserving its draft for Back.
  `Arrival` consumes each tap once, bound to that destination entry so an older tab cannot replay it:

  | Entry | Destination |
  |---|---|
  | Lesson reminder | Schedule on the lesson's current day, lesson sheet open |
  | Agenda / evening digest | Schedule on the day carried by the notification, even after midnight |
  | Auto-completion report | Latest day settled |
  | Debt digest | Reports, scrolled to debts |

  A relaunch from Recents (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) goes nowhere. On Android 11 and
  below, Back finishes the activity, and Recents replays the intent that started the task: an old tap.

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
