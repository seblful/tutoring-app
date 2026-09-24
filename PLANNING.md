# Klas — Product Goal

> Status: **v1 scope is built and shipping** (`0.3.0`). This file holds the *goal* and the *success
> criteria*; `CONTEXT.md` holds the architecture and the decisions.

## Purpose

A native Android app for a **single** private tutor to manage students, groups, subjects, schedule,
payments and debts, mirrored into the device calendar. Russian and English UI, Material 3, **fully
offline / local-only** — no backend, no account, no cloud.

The **language** is Russian, English, or whichever of the two the phone asks for (the default), chosen in
first-run setup and in Settings. Money is displayed in **BYN**, **RUB**, **USD** or **EUR** — chosen the
same two places, and starting from the phone's own region. The currency only *labels* amounts; nothing is
converted at an exchange rate.

## Domain model

- **Subject** — create/edit/list; deletable only while nothing references it.
- **Student** — add/edit/**archive** (never delete — history is protected); takes one or more subjects.
- **Group** — a named roster taught one subject together, with its own hourly rate.
- **Price** — one price per (student, subject), or one rate per group. Editing it never alters classes
  already booked: the rate is copied onto the class at booking time. Past rates are kept as a log.
- **Class** — (student, subject, start, duration, mode, status), one-off. Booking for a group writes
  one class per member at the group's rate; the classes sharing a `(group, start)` pair *are* the
  lesson.
- **Payment** — date + amount per student, correctable in place.
- **Balance** = Σ(charges on completed classes) − Σ(payments).
- **Homework** — text set at a lesson, plus each student's verdict (done / partly / not done). Due at the
  next lesson on its track — the group's, or the same student's in the same subject — which is derived,
  never stored.

## Behavior

- **Auto-completion** (opt-in): a `scheduled` class becomes `completed` once its end time passes,
  which is what bills it. Runs both as periodic upkeep and in-process at app start.
- **No-show handling:** the app cannot detect attendance, only the clock. A class that didn't happen
  is **cancelled**, which voids its charge. For a group lesson the tutor unticks the member instead,
  and that member simply has no class.
- **Payments** reduce the balance; partial payments and over-payment (shown as «Переплата») both work.
- **Reminders** before the next lesson: time, subject, student, location if set — as alarms, so they
  arrive on time.
- **Calendar:** push/update/cancel events to mirror class status; a group lesson mirrors one event per
  member, each with their own email invite (a separate opt-in).

## Production-ready (v1) means

- Polished Material 3 UI, works fully offline, no crashes, no data loss.
- **Backup/export:** export & import all data as a JSON file via SAF, so a dead phone isn't a total loss.
- **Reports:** income by period / student / subject / group / lesson kind, plus a debt overview and
  CSV export.

## Non-goals (v1)

Multi-user/login, cloud sync across devices, web/iOS, in-app payments, recurring lesson series.

## Success criteria (each written as a test)

| # | Criterion | Pinned by |
|---|---|---|
| 1 | A class whose end time passes auto-completes and adds its *booked* price to the debt | `work/MaintenanceTest`, `androidTest .../AutoCompleteTest` |
| 2 | Editing a price changes only future classes, never booked ones | `domain/BillingTest`, `data/repository/RateHistoryTest` |
| 3 | Recording a payment reduces the balance; partial and over-payment behave correctly | `domain/BillingTest` |
| 4 | Cancelled classes never add debt | `domain/ClassRulesTest` |
| 5 | Moving a class updates its time and its synced calendar event | `androidTest .../ClassCalendarSyncTest` |
| 6 | Export then import on an empty DB reproduces identical data | `androidTest .../BackupRoundTripTest` |
| 7 | A reminder fires ahead of a `scheduled` class, and never on booking | `domain/RemindersTest`, `notifications/DueReminderTest` |
| 8 | Two lessons cannot claim the same hour, by any route in | `domain/SchedulingTest`, `ui/schedule/ScheduleViewModelTest` |
| 9 | A multi-table write lands whole or not at all | `data/repository/*AtomicityTest` |
| 10 | Homework is due at the next non-cancelled lesson on its track, and the tab lists only the latest open one per track | `domain/HomeworkTest`, `ui/homework/HomeworkViewModelTest` |
| 11 | A homework verdict is one student's and touches nothing else on their row | `data/repository/ClassRepositoryHomeworkTest` |
| 12 | A lesson's calendar event carries its homework and never its private note | `calendar/CalendarEventTest`, `androidTest .../ClassCalendarSyncTest` |

## Decisions superseded since planning

Kept because the reasoning still gets re-proposed:

- **`missed` and `moved` statuses were dropped.** The lifecycle is `scheduled → completed | cancelled`.
  A "missed" class is a cancelled one (charge voided); a "moved" one is the same class with a new time.
- **Recurring weekly series were dropped** from v1 — classes are one-off. Booking is fast enough
  (duration chips, pre-filled rate and mode) that a series would mostly add edit-scope questions.
- **Calendar sync uses `CalendarContract`, not the Google Calendar REST API.** The OS syncs the device
  calendar to Google itself, so there is no Cloud project, no OAuth consent screen and no extra
  privacy-policy surface — the setup step the original plan flagged simply disappeared.
- **Google Drive is not a backup destination**; SAF covers "put the file wherever you like", including
  Drive, without the app holding a cloud credential.

## What v1 still owes

- An instrumented Room **migration** test, before the first schema bump after v1 — see *Testing* in
  `CONTEXT.md`.
- Play publication under the new `com.seblful.klas` id — see `docs/publishing-to-google-play.md`.
