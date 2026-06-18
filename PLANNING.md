# Tutoring App — Planning / Goal

> Status: planning. Not yet started. Single open decision flagged below (no-show handling).

## Purpose

A native Android app for a **single** private tutor to manage students, subjects,
schedule, payments, and debts, with Google Calendar sync. Russian UI, Material 3,
**fully offline / local-only** — no backend.

## Stack

Kotlin · Jetpack Compose · Material 3 · Room · WorkManager (notifications) ·
Google Calendar API (on-device OAuth) · MVVM with a unit-tested domain layer.

Currency: **BYN** (Belarusian ruble), displayed as e.g. `1500 BYN`.

## Domain model

- **Subject** — create/edit/list.
- **Student** — add/edit/archive; takes one or more subjects.
- **Price** — one price per (student, subject), *versioned*: editing it never alters
  classes already completed.
- **Class** — (student, subject, start, duration, status).
  Statuses: `scheduled → completed | cancelled | missed | moved`.
  Recurring weekly + one-tap add.
- **Payment** — date + amount per student.
- **Balance** = Σ(price of completed unpaid classes) − Σ(payments).

## Behavior

- **Auto-completion:** a `scheduled` class becomes `completed` once its end time passes
  (WorkManager job). It then adds its then-current price to the student's debt.
- **Missed vs completed:** the app cannot detect attendance — it only knows the clock.
  When a class's end time passes it is assumed to have happened and is auto-completed
  (Option A). If the student didn't show, the tutor manually marks the class `missed`,
  which voids its charge. A `missed` class costs **0₽**.
- **Payments** reduce balance; handle partial and over-payment (credit).
- **Notifications** before next class: time, subject, student, location if set.
- **Calendar:** push/update/cancel events to mirror class status.

## Production-ready (v1) means

- Polished Material 3 UI, works fully offline, no crashes, no data loss.
- **Backup/export:** export & import all data (JSON/file or Google Drive) so a dead
  phone isn't a total loss.
- **Reports:** income by month / student / subject, and a debt overview.

## Non-goals (v1)

Multi-user/login, cloud sync across devices, web/iOS, in-app payments.

## Success criteria (write as tests)

1. A class whose end time passes auto-completes and adds its *then-current* price to debt.
2. Editing a price changes only future classes, never completed ones.
3. Recording a payment reduces balance; partial and over-payment behave correctly.
4. Cancelled/missed classes never add debt.
5. Moving a class updates its time and the synced calendar event.
6. Export then import on an empty DB reproduces identical data.
7. A reminder notification fires before a `scheduled` class.

## Open decisions

_None — all resolved._

## Consequences worth knowing

- **Auto-complete forces a no-show decision** — without one, a student who didn't show
  still gets charged.
- **Local-only + Google Calendar still needs OAuth setup** — a Google Cloud project,
  consent screen, and (for unrestricted use) a privacy policy URL. Setup step, not code.

## Suggested build order (each step independently verifiable)

1. Domain layer + Room (Subject, Student, Price, Class, Payment) → unit tests for
   balance & price-versioning.
2. UI: students, subjects, pricing → manual flows work offline.
3. Scheduling + one-tap/recurring add, status transitions.
4. WorkManager auto-complete + reminders.
5. Reports.
6. Backup/export & import.
7. Google Calendar sync (last — most external setup).
