package com.seblful.klas.data.backup

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.HomeworkEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.domain.model.Money

/**
 * Thrown when a file parses as a backup but carries a row this build cannot read back. Classified as
 * [BackupFailure.Malformed], because that is what it is from the tutor's side: the file is not a Klas
 * backup this app can use, whatever it says in its `version`.
 */
class MalformedBackupException(val reason: String) :
    IllegalArgumentException("Backup contains a row this build cannot read: $reason")

/**
 * What a parsed [BackupData] has to be true of before a single row of it is written.
 *
 * **Why this exists at all.** The import writes rows verbatim — it deserializes them, it never
 * interprets them. The interpreting happens much later, on the **read** path, inside the `Flow`s the
 * screens collect. So a file carrying a lesson of zero minutes, or (before the entities' enum columns were
 * typed) `"status": "MISSED"`, used to import *successfully* and then break the app: every read of that
 * row threw, the schedule and the reports could not be drawn, and no amount of reopening helped because
 * the bad row was now the saved data. The tutor's own backup had become the thing that broke the app,
 * with nothing on screen to say so.
 *
 * Checking here turns that into an ordinary refusal. It has to happen **before** the transaction
 * rather than inside it, because "the import threw halfway and rolled back" is the same outcome only
 * as long as the rollback is real — and this way the reason survives to be shown, instead of being
 * whatever the first bad row happened to throw.
 *
 * What is checked is exactly the set of columns that a read can *fail* on, no more: this is not a
 * validator for plausible data. **An enum column is not among them**: it is typed, so a name this build
 * has no constant for is refused as the file is parsed (`BackupManager.parse` — classified as
 * [BackupFailure.Malformed] all the same), and no row that reaches this check can hold one. A backup is allowed to be strange (a lesson at 4 a.m., a rate of 0, a
 * student with no subjects); it is not allowed to be unreadable. Referential integrity is left to the
 * database's own foreign keys, which the import's transaction already enforces.
 *
 * "Unreadable" includes an amount the money arithmetic cannot survive, which is not obvious and is
 * why [checkAmount] argues it: a rate does not fail to *load*, it fails when something multiplies it.
 */
object BackupIntegrity {

    /** @throws MalformedBackupException naming the first row that could not be read back. */
    fun check(data: BackupData) {
        data.lessons.forEach { checkLesson(it.toEntity()) }
        data.attendance.forEach { checkAttendance(it.toEntity()) }
        // A v1-v5 file carries its lessons flat, and they are checked as they will be read: through the
        // split, so one function answers for both shapes rather than a copy of it answering for the old.
        data.classes.splitIntoLessons().let { (lessons, attendance) ->
            lessons.forEach { checkLesson(it) }
            attendance.forEach { checkAttendance(it) }
        }
        data.groups.forEach { checkGroup(it) }
        data.prices.forEach { checkAmount(it.amountMinor, "price for student ${it.studentId}") }
        data.payments.forEach { checkAmount(it.amountMinor, "payment ${it.id}") }
        data.rateHistory.forEach { checkRateChange(it) }
        data.homework.forEach { checkHomework(it) }
    }

    private fun checkLesson(l: LessonEntity) {
        // A non-positive duration makes a lesson end at or before it starts, which silently disables
        // the overlap check that stops two lessons sharing an hour — see `Scheduling.Slot`.
        if (l.durationMinutes <= 0) {
            refuse("lesson ${l.id} lasts ${l.durationMinutes} minutes")
        }
    }

    private fun checkAttendance(a: AttendanceEntity) {
        checkAmount(a.priceMinor, "class ${a.id}'s rate")
        checkAmount(a.chargeMinor, "class ${a.id}'s charge")
    }

    private fun checkGroup(g: GroupEntity) {
        checkAmount(g.priceMinor, "group ${g.id}'s rate")
    }

    /**
     * An amount has to be one the app's arithmetic survives: within `0..`[Money.MAX], the same bound
     * every typed amount is held to by `parseMoneyOrNull`.
     *
     * This is the second way in, and until it was checked the only one left open. A rate is not read
     * back through a parser — it is a bare `Long` of kopecks, so nothing about it can fail to *load*.
     * What fails is the arithmetic on it: a charge is `rate × minutes`, which wraps past
     * `Long.MAX_VALUE` and comes back **negative**, and a negative charge reads as the tutor owing the
     * student. A negative amount is refused for the same reason from the other direction — the money
     * fields reject one, so a stored one can only have come from a hand-edited or corrupt file.
     *
     * It belongs here with the duration check because it is the same kind of fact: a column
     * this build cannot make sense of, caught before it becomes the saved data.
     */
    private fun checkAmount(minor: Long, what: String) {
        if (minor < 0 || minor > Money.MAX.minor) {
            refuse("$what is $minor kopecks, outside 0..${Money.MAX.minor}")
        }
    }

    /**
     * A rate change is about a (student, subject) price or a group's rate. A row that is about neither
     * has no [com.seblful.klas.domain.model.RateOwner] to come back as, and the mapper says so with an
     * `error(...)` — which, on the read path, is the crash this check exists to move forward in time.
     */
    private fun checkRateChange(r: RateChangeEntity) {
        val isStudentPrice = r.studentId != null && r.subjectId != null
        val isGroupRate = r.groupId != null
        if (!isStudentPrice && !isGroupRate) {
            refuse("rate change ${r.id} belongs to neither a student's subject nor a group")
        }
    }

    /**
     * Homework is for a student or a group, never neither and never both. Neither has no `Assignee` to
     * come back as (the mapper fails, as for [checkRateChange]'s row); both would read back as the
     * student's, quietly taking it away from the group — a guess this build will not make on a restore.
     */
    private fun checkHomework(h: HomeworkEntity) {
        if ((h.studentId == null) == (h.groupId == null)) {
            refuse("homework ${h.id} is for neither a student nor a group, or for both")
        }
    }

    private fun refuse(reason: String): Nothing = throw MalformedBackupException(reason)
}
