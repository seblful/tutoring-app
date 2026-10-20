# Domain rules

Rules Klas enforces for one tutor. See [calendar and reminders](calendar-and-reminders.md) for device
mirrors and [database and backup](database-and-backup.md) for persistence contracts.

## Money and billing

- Money uses integer minor units, never floats. Typed rates/payments stop at `Money.MAX`; duration-based
  charges can exceed that input ceiling.
- Currency is a display label (`BYN|RUB|USD|EUR`), never a conversion; display its symbol. A fresh install
  uses the phone's region. Decimal separators follow app language, not currency.
- Each class books an editable hourly rate, initially the student's/group's. Later rate changes leave
  existing classes untouched.
- Balance = completed-lesson charges − payments; balances/income are derived. Lists, reports and the
  debt digest read them summed by SQLite (`BalanceDao`, held to `Billing` by `BalanceRepositoryTest`); a
  report reads only its period and the one before (`Reports.window`). Partial payments and
  overpayments work. `Billing.billedOrNull` distinguishes a taught zero-rate lesson (show zero) from an
  unbilled absence (show no amount).
- Auto-completion is opt-in: scheduled lessons become completed, and therefore billable, after ending.
  Upkeep and app start catch up; enabling it asks before settling the backlog and reports the result.
- Reports retain archived students' earnings and outstanding debts. Debts use all history; income means
  earned lesson charges within `ReportPeriod`, not cash received. An unfinished period compares with
  the same elapsed days of the preceding period. Weekly debt reminders cover active students only.

## Lessons

- `lessons` holds time, subject, mode and status; `attendance` holds each student's attendance, rate
  and charge. `ABSENT_BILLED` charges an absence; only an explicit absence can stop a completed charge.
- Status is `scheduled → completed | cancelled`; cancellation voids the charge. Moving into the future
  re-plans a lesson; corrections in the past retain its status. `LessonPhase`
  (`UPCOMING|IN_PROGRESS|OVERDUE|SETTLED`) derives from status and the injected clock; it is not persisted.
  There is no `missed` or `moved` status.
- A lesson carries no tutor notes. Homework is attached by rule, below.
- Writes take a whole `ClassSession`, except per-person `setAttendance`. Editing/deleting works in any
  status and recalculates charges; deleting a completed lesson removes income and requires a UI warning.
- The lesson sheet records each student's attendance, including billed absences, without removing their
  history. The booking form's member ticks change who is booked; removing a member deletes their row.
- **One tutor, one lesson at a time:** `Scheduling.conflicts` refuses overlap through
  `LessonWrite.HourTaken`. Back-to-back and past bookings work; cancellation frees the slot. A correction
  that leaves a lesson cancelled reserves no time, even if another lesson has reused its slot.
- Repeating bookings write ordinary lessons in one transaction (`Recurrence`, `bookSeries`). Taken
  slots are skipped, including the first date; Save requires at least one free occurrence. Results are
  reported as `SeriesWrite`; there is no series entity or bulk “cancel the rest”. See
  `Recurrence`'s KDoc for why.

## Homework

- Each assignment belongs to exactly one student or group (`Assignee`), with text, given time and done
  time. Its only states are current/done; no per-student grades or review stage.
- `HomeworkRules` attaches it to the assignee's next non-cancelled lesson **strictly after** it was
  given. A student's next lesson includes group lessons. Attachment is derived, so it follows moves
  and works before the next lesson is booked; editing preserves the given time.
- The tab shows current work in lesson order (overdue first, unbooked last) and done work newest first,
  for active students/groups. Filtering a student includes their groups' work. Swipe moves between
  current/archive; undo restores the exact done state. Tap edits/deletes; share includes the lesson date.
- `HomeworkDao.edit`/`setDone` use targeted updates so stale dialogs cannot overwrite newer changes.
  Lesson sheets show attached work; reminders show current work.
- Not supported: photos/files, grades, independent due dates or student-facing accounts.

## Groups

- One fixed subject and hourly rate per group; charges remain individual. Pre-v3 backups can retain
  differing member rates. Only students priced for that subject may join; save intersects the roster
  with eligible students and the form asks before dropping members.
- Attendance is independent of roster membership and correctable afterwards. Groups with lessons are
  archive-only; unused groups can be deleted. Calendar events are per member, reminders per lesson.
- `Captions.title` names group lessons by group, individual lessons by student, with a student fallback
  for an unknown group name. Booking and reminders use the same rule.

## Students, subjects and history

- A student's individual subjects and priced subjects are the same set. Un-enrolling is refused while
  a class references the subject; otherwise it removes the rate log too.
- Students are archive-only. Subjects are deletable only without lessons, groups or enrolled students;
  names are unique ignoring case (`SubjectNames`). Foreign keys protect lesson, attendance and payment
  history with `ON DELETE RESTRICT`.
- In-person/remote mode defaults from the student, is overridable per lesson and becomes calendar location.
- `price_history` logs student/group rate changes; saving an unchanged rate adds nothing.

## Non-goals (v1)

Multi-user/login, cloud sync across devices, web/iOS, in-app payments and a recurring-series entity.
