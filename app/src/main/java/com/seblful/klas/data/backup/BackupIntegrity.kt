package com.seblful.klas.data.backup

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus

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
 * **Why this exists at all.** The entities store an enum as *text* and a duration as a bare `Int`, and
 * the import writes them verbatim — it deserializes rows, it never interprets them. The interpreting
 * happens much later, on the **read** path, in `data/Mappers.kt`, inside the `Flow`s the screens
 * collect. So a file carrying `"status": "MISSED"`, or a lesson of zero minutes, used to import
 * *successfully* and then break the app: every read of that row threw, the schedule and the reports
 * could not be drawn, and no amount of reopening helped because the bad row was now the saved data.
 * The tutor's own backup had become the thing that broke the app, with nothing on screen to say so.
 *
 * Checking here turns that into an ordinary refusal. It has to happen **before** the transaction
 * rather than inside it, because "the import threw halfway and rolled back" is the same outcome only
 * as long as the rollback is real — and this way the reason survives to be shown, instead of being
 * whatever the first bad row happened to throw.
 *
 * What is checked is exactly the set of columns that a read can *fail* on, no more: this is not a
 * validator for plausible data. A backup is allowed to be strange (a lesson at 4 a.m., a rate of 0, a
 * student with no subjects); it is not allowed to be unreadable. Referential integrity is left to the
 * database's own foreign keys, which the import's transaction already enforces.
 */
object BackupIntegrity {

    /** @throws MalformedBackupException naming the first row that could not be read back. */
    fun check(data: BackupData) {
        data.classes.forEach { checkClass(it) }
        data.students.forEach { checkStudent(it) }
        data.groups.forEach { checkGroup(it) }
        data.rateHistory.forEach { checkRateChange(it) }
    }

    private fun checkClass(c: ClassEntity) {
        if (ClassStatus.parse(c.status) == null) {
            refuse("class ${c.id} has status \"${c.status}\"")
        }
        if (ClassMode.parse(c.mode) == null) {
            refuse("class ${c.id} has mode \"${c.mode}\"")
        }
        // A non-positive duration makes a lesson end at or before it starts, which silently disables
        // the overlap check that stops two lessons sharing an hour — see `Scheduling.Slot`.
        if (c.durationMinutes <= 0) {
            refuse("class ${c.id} lasts ${c.durationMinutes} minutes")
        }
    }

    private fun checkStudent(s: StudentEntity) {
        if (ClassMode.parse(s.defaultMode) == null) {
            refuse("student ${s.id} has default mode \"${s.defaultMode}\"")
        }
    }

    private fun checkGroup(g: GroupEntity) {
        if (ClassMode.parse(g.defaultMode) == null) {
            refuse("group ${g.id} has default mode \"${g.defaultMode}\"")
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

    private fun refuse(reason: String): Nothing = throw MalformedBackupException(reason)
}
