# Domain rules

What Klas enforces about money, lessons, homework, groups and students, and why. What reaches the device
calendar and the tray is in [calendar-and-reminders.md](calendar-and-reminders.md); how it is stored, in
[database-and-backup.md](database-and-backup.md).

## Money and billing

- Money is integer minor units (kopecks), no floats, bounded by `Money.MAX`.
- Currency (`BYN|RUB|USD|EUR`) is a **display** setting: changing it re-labels stored amounts, never
  converts. Always printed as ISO code, never a symbol. Fresh install defaults from phone region.
- Decimal separator follows the app **language**, not the currency.
- Price is an hourly rate booked per class, pre-filled from student/group rate, editable per lesson;
  re-pricing never touches existing classes.
- Balance = Σ(charges on completed lessons) − Σ(payments). Balances and income are always derived, never
  stored. Partial payments and over-payment («Переплата») both work.
- Status: `scheduled → completed | cancelled`. The app cannot detect attendance, only the clock, so a
  lesson that didn't happen is **cancelled**, which voids its charge. «Идёт» is derived, not a fourth
  status (see [Lessons](#lessons)).
- Auto-completion (opt-in): a `scheduled` lesson becomes `completed` once its end time passes, which is
  what bills it — in the periodic upkeep and in-process at app start. Turning it on sweeps the whole
  backlog, asks first, reports what it settled; the background pass never asks.
- Reports cover active students only, over a period (`domain/ReportPeriod`) — debts are always the whole
  history. `Reports.of` is the only entry point. A period still in progress is compared with the same
  number of days of the one before (`ReportPeriod.daysElapsed`), not with the whole of it.

## Lessons

- A lesson is a row; attendance is another (`lessons` + `attendance`, joined by the `classes` view). The
  lesson holds hour/subject/one status; each member's row holds attendance, rate, charge.
  `ABSENT_BILLED` = absent but charged; only an explicit absence stops a charge.
- A lesson's phase is derived, never stored (`domain/LessonPhase`: `UPCOMING|IN_PROGRESS|OVERDUE|SETTLED`),
  from status + injected clock — not a fourth `LessonStatus`.
- A lesson carries none of the tutor's text: the note was removed at the tutor's request (v9), and
  homework is its own table, attached by rule.
- A lesson is the unit of change — every write takes a `ClassSession` (except `setAttendance`, per person).
- **One tutor, one lesson at a time**: overlap refused, not warned; back-to-back fine; cancelled frees the
  slot; past booking allowed. Pure rule in `Scheduling.conflicts`, enforced by the repository
  (`LessonWrite.HourTaken`).
- Editing/deleting allowed in any status; charge recomputed in place; deleting a completed lesson removes
  its income (UI warns).
- A repeating booking writes ordinary lessons, not a series (`domain/Recurrence`, `ClassRepository.bookSeries`)
  — no series table, no expand-on-read, no "cancel the rest" in one tap. A taken hour is *reported*, not
  refused (`SeriesWrite`), and the whole run is one transaction.

## Homework

- Its own table (`homework`): for **one student or one group** (exactly one of `studentId`/`groupId`,
  read back as a sealed `Assignee`), text, when it was given, and when it was done. **Current or done,
  nothing in between** — v8's per-student verdicts and «Проверить»/«Выдано» split were more than the tutor
  wanted.
- **Attached to its assignee's next lesson** after it was given — a student's next lesson of any kind
  (a group's included), a group's next lesson; cancelled ones skipped; strictly after, so homework set
  during a lesson is for the next one. **Derived, never stored** (`domain/HomeworkRules`), because with
  no series the next lesson is often not booked yet, and it can move.
- Set from the «Задания» tab's add button (nobody preselected; the list puts whoever's lesson is nearest
  now first, and the dialog names the lesson it will go to) or from a student's or group's card (fixed
  to them). Edits keep when it was given, so they never move it to another lesson.
- The tab: «Текущие» (in lesson order, overdue on top, unbooked last) and «Архив» (done, newest first),
  for active students and groups, narrowable to one student (with their groups') or one group. A swipe
  moves a piece between the two, with an undo that restores its done state exactly; a tap corrects or
  deletes it. The lesson's sheet ticks off what is attached to it; the reminder names what is current.
- Writes are targeted `UPDATE`s (`HomeworkDao.edit`/`setDone`), so a stale list or dialog cannot undo a
  newer swipe or edit. Sent through the share sheet, worded with the lesson's date. Not in v1:
  photos/files, grades, due dates independent of lessons, anything student-facing.

## Groups

- A group prices its lessons; students still owe individually. One rate per lesson (pre-v3 backups excepted).
- A group is taught one fixed subject; only priced students may be members; roster is intersected on save,
  and the form asks first when that would take somebody off it (`GroupFormViewModel.UiState.leavingOnSave`).
- Attendance is separate from roster membership and correctable afterwards.
- A lesson is captioned by one module (`domain/Captions`): group lesson titled by group, individual by student;
  a group with no known name falls back to a student. Reminders and the booking form name a lesson by the
  same rule (`Captions.title`).
- Groups are archive-only once they have lessons; mirror as one calendar event per member; reminders per lesson.

## Students, subjects, history

- A student's subjects == their priced subjects (individual lessons only); un-enrolling refused while a
  class references it.
- Class mode (in person/remote): student default, overridable per class, mirrored into the calendar location.
- History is structurally protected: `classes`/`payments` use `ON DELETE RESTRICT`; students archive-only;
  subjects/groups deletable only while unreferenced — for a subject, by lessons, a group or a student who
  takes it. A subject's name is unique whatever its case (`domain/SubjectNames`).
- A rate's past is a log (`price_history`, one table for student price + group rate). Unchanged rate logs
  nothing; un-enrolling forgets its log.

## Non-goals (v1)

Multi-user/login, cloud sync across devices, web/iOS, in-app payments, a recurring-series entity.

## Rejected on purpose

Kept because the reasoning still gets re-proposed:

- **No `missed` or `moved` status.** The lifecycle is `scheduled → completed | cancelled`. A "missed"
  lesson is a cancelled one (charge voided); a "moved" one is the same lesson with a new time.
- **No series entity.** A repeating booking writes ordinary one-off lessons (see [Lessons](#lessons));
  why a series table would fight the rest of the app is in `domain/Recurrence`'s KDoc.
