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
- `ReminderDelivery` (`OFF|ON_TIME|BLOCKED|LATE`) reports the two permission grants (post-notifications,
  exact-alarm) as one settings row with a link to the right system page.

## Digests

- Three digests (morning agenda, evening unmarked lessons, weekly debts), opt-in, scheduled by the upkeep
  pass, are workers (lateness is fine). A run already due is left alone rather than skipped; a digest
  with nothing to say sends nothing.
- The evening digest is auto-completion's counterpart (asks vs. assumes), counts lessons only, today only,
  no action button.
