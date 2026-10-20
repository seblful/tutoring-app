# Calendar and reminders

The database drives both mirrors; neither writes changes back into Klas.

## Calendar sync

- Opt-in, one-way through `CalendarContract`; the device's provider handles cloud sync. No Google
  Calendar REST API, Cloud project or OAuth flow is needed.
- `ClassRepository.write` mirrors after commit; `CalendarMirror` owns backfill and withdrawal.
- Completed events are refreshed but never created/re-created (`CalendarSyncPolicy.Action.REFRESH`).
  Descriptions are always overwritten with empty text, clearing notes/homework from older versions.
- An absent member of a scheduled lesson has no event, billed or not. Marking them present re-creates
  it; attendance no longer affects this policy once the lesson is completed.
- Email invitations are a separate, off-by-default setting; disabling it and re-syncing withdraws them.

## Lesson reminders

- `AlarmManager` delivers reminders: exact when allowed, otherwise inexact. Changes re-derive the plan
  over a fixed horizon; reboot re-arms it.
- A passed reminder time with a lesson not yet ended is still **owed** (`Reminders.Plan.due`). Do not
  re-arm or cancel it: Doze may be holding its alarm. Read through `notEndedWithin` so running lessons
  keep their alarms. `Reminders.shouldPost` re-checks state and lead time at delivery.
- «Проведено» bills through a broadcast. «Перенести» opens the lesson form over the current screen;
  an existing form waits underneath. Both re-check current legality because notifications can be stale.
- `TrayNavigation` opens the named destination; `Arrival` consumes each tap once:

  | Entry | Destination |
  |---|---|
  | Lesson reminder | Schedule on the lesson's current day, lesson sheet open |
  | Agenda / evening digest | Schedule on the day carried by the notification, even after midnight |
  | Auto-completion report | Latest day settled |
  | Debt digest | Reports, scrolled to debts |

## Permissions and digests

- Ask for notifications in Settings and once at first booking (`shouldAskForNotifications`), never at
  first launch. Permission ports report grants/`canAsk`; Compose permission-request helpers show prompts.
  View models carry no Android permission names.
- `ReminderDelivery` combines notification/exact-alarm grants (`OFF|ON_TIME|BLOCKED|LATE`);
  `CalendarDelivery` reports sync permission (`OFF|ON|BLOCKED`). Settings links to the relevant system page.
- Morning agenda, evening unmarked lessons and weekly debts are opt-in workers: lateness is acceptable.
  Upkeep schedules them, leaves due runs alone and sends nothing for an empty digest.
- Digest times follow the device's wall clock/zone. `ClockChangeReceiver` runs upkeep immediately on a
  clock/timezone change. The evening digest asks about today's lessons, with no action button;
  auto-completion instead assumes they were taught.
