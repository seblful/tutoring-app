# Calendar and reminders

What Klas pushes to the device calendar and the notification tray, and when. Both are mirrors of the
database, never a source (see [architecture.md](architecture.md)).

## Calendar sync

- Sync via `CalendarContract`, opt-in; the OS syncs the device calendar to Google itself. **Not the
  Google Calendar REST API** — that would need a Cloud project, an OAuth consent screen and extra
  privacy-policy surface, for nothing the OS doesn't already do.
- Mirror runs **one way**, app → calendar, through `ClassRepository.write`'s post-commit phase; nothing
  reads the calendar back.
- A completed lesson's event is kept true but never (re)created (`Action.REFRESH`). Its description is
  **always written empty** (`CalendarEvent.DESCRIPTION`): a lesson carries no text of the tutor's, and the
  write clears the note (v7) or homework (v8) an older build put in front of an invited student.
- Email invites are a separate, off-by-default setting; turning it off and re-syncing withdraws invites.

## Reminders

- Reminders are alarms (`AlarmManager`), not workers — exact where the OS allows, inexact fallback
  otherwise; re-derived from scratch over a fixed horizon on every change; re-armed after reboot.
- Tray actions: «Проведено» bills in place via a broadcast; «Перенести» opens the lesson. Both re-check
  legality against the current lesson state (a notification can be stale).
- **Every tray entry opens what it names** (`notifications/TrayNavigation`), never just the app: a reminder →
  the schedule on the lesson's *current* day with its sheet open; the agenda and the evening digest → the
  schedule on the day they are about (carried in the intent, so a tap after midnight still lands there);
  the auto-completion report → the latest day it settled; the debt digest → Reports, scrolled to the debts.
  A tab acts on each tap once, however often it is returned to (`ui/navigation/Arrival`).
- The notification permission is **asked for where reminders are**: the Settings row, and once at the
  tutor's first booking (`ClassEditViewModel.notificationsToAskFor`) — never over first launch, when there
  is no lesson to be reminded of.
- `ReminderDelivery` (`OFF|ON_TIME|BLOCKED|LATE`) reports the two permission grants (post-notifications,
  exact-alarm) as one settings row with a link to the right system page. `CalendarDelivery`
  (`OFF|ON|BLOCKED`) does the same for the sync row: on, with the calendar permission revoked in system
  settings, is a report rather than a silent dead mirror.

## Digests

- Three digests (morning agenda, evening unmarked lessons, weekly debts), opt-in, scheduled by the upkeep
  pass, are workers (lateness is fine). A run already due is left alone rather than skipped; a digest
  with nothing to say sends nothing.
- The evening digest is auto-completion's counterpart (asks vs. assumes), counts lessons only, today only,
  no action button.
