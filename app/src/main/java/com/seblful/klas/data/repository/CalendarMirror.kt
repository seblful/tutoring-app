package com.seblful.klas.data.repository

import com.seblful.klas.calendar.CalendarEvent
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.AttendanceDao
import com.seblful.klas.data.local.ClassDao
import com.seblful.klas.data.local.GroupDao
import com.seblful.klas.data.local.StudentDao
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.domain.lesson.CalendarSyncPolicy
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.runCatchingCancellable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Everything the device calendar shows, and every table that decides it.
 *
 * A mirrored event is assembled from **four** tables, not one: the class supplies the time, and then
 * `titleFor` reads the student's, the subject's and (for a group lesson) the group's name, while
 * `guestFor` reads the student's email address. Only the fourth of those — a write to `classes` — used
 * to trigger a mirror, so renaming a student, renaming a group, renaming a subject or correcting an
 * email left every already-mirrored event saying the old thing until something unrelated happened to
 * call `resyncAll`. The tutor's calendar is the one place they look when they are not in the app, and it
 * quietly disagreed with it.
 *
 * The fix is not another `resyncAll` call bolted onto each of those writes — it is that the four reads
 * and the obligation they create live in one module, so a caller changing what an event *shows* asks
 * this to refresh it and does not have to know which of the four tables it happened to touch.
 *
 * Deliberately not a port. There is one behind it ([CalendarSync]); this is the module that knows what
 * to put through it, which is a rule about *this* app's data and not a platform capability. It reads
 * DAOs and writes back one column (`classes.calendarEventId`), so it sits with the repositories rather
 * than in `calendar/`, which stays ports and adapters only.
 *
 * All of it is **best-effort**, like every other piece of calendar work: the adapter no-ops when the
 * permission or the calendar is absent, so the local database is never blocked by sync.
 */
@SingleIn(AppScope::class)
class CalendarMirror @Inject constructor(
    private val classDao: ClassDao,
    /**
     * The event id belongs to **one student's** row, a group lesson mirroring as an event each, so the
     * one thing this writes is an attendance row — never a lesson.
     */
    private val attendanceDao: AttendanceDao,
    private val studentDao: StudentDao,
    private val subjectDao: SubjectDao,
    private val groupDao: GroupDao,
    private val calendarSync: CalendarSync,
    private val settings: SettingsRepository,
) {

    /**
     * Reflects [classes] into the calendar under one snapshot of the settings.
     *
     * Handed the whole lesson at once rather than a class at a time, because the settings row governs
     * all of them identically: read per class it was one query per member to answer the same question,
     * and a batch also makes the [AppSettings] a single snapshot, so a lesson cannot mirror half under
     * the old invite setting and half under a new one.
     */
    suspend fun mirror(classes: List<TutoringClass>) {
        if (classes.isEmpty()) return
        mirror(classes, settings.get())
    }

    private suspend fun mirror(classes: List<TutoringClass>, settings: AppSettings) {
        if (!settings.calendarSyncEnabled) return
        classes.forEach { mirrorOne(it, settings) }
    }

    /**
     * Re-mirrors every class. Run after the tutor turns calendar sync on, to backfill events for
     * lessons booked while it was off.
     */
    suspend fun resyncAll() {
        val settings = settings.get()
        // Checked before the read, so a resync with sync off doesn't walk the whole history to decide
        // it has nothing to do.
        if (!settings.calendarSyncEnabled) return
        mirror(classDao.getAll().map { it.toDomain() }, settings)
    }

    /**
     * A replace-all restore trusts only bindings read from this database, never ids supplied by a file.
     * Wait for the replacement to succeed before touching anything; its failure must remain visible.
     *
     * An event this database made is handed back to the restored row that is **the same class** — same
     * row id, lesson, student and start — rather than withdrawn. Retiring every binding was safe and
     * cost the tutor's history: a taught lesson's event is only ever kept true, never created
     * ([CalendarSyncPolicy.Action.REFRESH]), so restoring this phone's own backup deleted every past
     * lesson from their calendar for good, and re-invited each student to every upcoming one. Re-binding
     * stays safe for the reason the file's ids are dropped: the id came from this device's calendar.
     * Only the bindings no restored class claims are withdrawn.
     */
    suspend fun remirrorAfter(replace: suspend () -> Unit) {
        val previous = classDao.withCalendarEvent().map { it.toDomain() }
        replace()
        runCatchingCancellable {
            val restored = classDao.getAll().map { it.toDomain() }.associateBy { it.binding() }
            val kept = mutableSetOf<String>()
            previous.forEach { old ->
                val eventId = old.calendarEventId ?: return@forEach
                val same = restored[old.binding()]
                if (same != null && same.calendarEventId == null && eventId !in kept) {
                    attendanceDao.setCalendarEventId(same.id, eventId)
                    kept += eventId
                }
            }
            previous.mapNotNull { it.calendarEventId }.distinct().filterNot { it in kept }
                .forEach { calendarSync.delete(it) }
            resyncAll()
        }
    }

    /** What makes a restored class the one an event of this database was mirroring. */
    private fun TutoringClass.binding() = listOf(id, lessonId, studentId, start)

    /**
     * Removes every mirrored event. Run when the tutor turns calendar sync off. Only the classes that
     * actually carry an event id are read: the rest have nothing to withdraw.
     */
    suspend fun clear() {
        classDao.withCalendarEvent().forEach { detach(it.toDomain()) }
    }

    /** Refreshes the events naming [studentId] — their name titles them and their email is invited. */
    suspend fun refreshForStudent(studentId: Long) = refreshWhere { it.studentId == studentId }

    /** Refreshes the events naming [subjectId], whose name is part of every title. */
    suspend fun refreshForSubject(subjectId: Long) = refreshWhere { it.subjectId == subjectId }

    /** Refreshes the events naming [groupId], whose name is what makes n overlapping events one lesson. */
    suspend fun refreshForGroup(groupId: Long) = refreshWhere { it.groupId == groupId }

    /**
     * The classes carrying an event that [matches] — which is exactly the set whose *content* can now
     * be wrong.
     *
     * Filtered from the one existing `withCalendarEvent` query rather than through three new per-column
     * ones: an event that does not exist yet cannot be stale, and backfilling those is what [resyncAll]
     * is for. A class whose event is present but whose status has since moved on is handled by the
     * policy in [mirrorOne] like any other, so a cancelled one has its event withdrawn here too.
     */
    private suspend fun refreshWhere(matches: (TutoringClass) -> Boolean) {
        val settings = settings.get()
        if (!settings.calendarSyncEnabled) return
        mirror(classDao.withCalendarEvent().map { it.toDomain() }.filter(matches), settings)
    }

    /**
     * Deletes the event mirroring a class whose row has **already gone**. Not [detach]: that nulls the
     * row's event id, and there is no longer a row to null it on.
     */
    suspend fun drop(klass: TutoringClass) {
        klass.calendarEventId?.let { calendarSync.delete(it) }
    }

    /**
     * Deletes [klass]'s mirrored event and forgets its id, or does nothing when there isn't one.
     *
     * The two halves belong together: an event id left behind after the event is gone would make the
     * next mirror try to update something that no longer exists. Turning sync off and cancelling a
     * lesson both owe exactly this, so they both say it here.
     */
    private suspend fun detach(klass: TutoringClass) {
        val eventId = klass.calendarEventId ?: return
        calendarSync.delete(eventId)
        attendanceDao.setCalendarEventId(klass.id, null)
    }

    /**
     * Reflects [klass]'s state into the calendar. Only reached through [mirror], which is what has
     * already established that sync is on.
     *
     * Which of the three arms a lesson takes is `CalendarSyncPolicy`'s and not this module's: what is
     * here is the four reads each one needs. The completed arm needs only the student's name, having
     * no guest to invite.
     */
    private suspend fun mirrorOne(klass: TutoringClass, settings: AppSettings) {
        when (CalendarSyncPolicy.actionFor(klass.status, klass.attended)) {
            CalendarSyncPolicy.Action.UPSERT -> {
                // One read of the student, not two: their name titles the event and their address is
                // what invites them to it, and the title and the guest each used to fetch the row.
                val student = studentDao.getById(klass.studentId)
                val eventId = calendarSync.upsert(
                    klass = klass,
                    title = titleFor(klass, student?.name.orEmpty()),
                    guest = guestFor(student, settings),
                    reminderLeadMinutes = settings.reminderLeadMinutes,
                )
                if (eventId != null && eventId != klass.calendarEventId) {
                    attendanceDao.setCalendarEventId(klass.id, eventId)
                }
            }
            // A lesson already taught: its event is kept true, and none is brought into being. No id
            // can come back, so there is nothing to write to `classes` either — see `CalendarSync.refresh`.
            CalendarSyncPolicy.Action.REFRESH -> calendarSync.refresh(
                klass = klass,
                title = titleFor(klass, studentDao.getById(klass.studentId)?.name.orEmpty()),
            )
            CalendarSyncPolicy.Action.DELETE -> detach(klass)
        }
    }

    /**
     * A group lesson mirrors as one event **per member** — each student keeps their own invite and
     * email reminder, and per-member corrections (one absentee cancelled) stay expressible. The group's
     * name goes in the title so those overlapping events read as one lesson.
     */
    private suspend fun titleFor(klass: TutoringClass, studentName: String): String =
        CalendarEvent.title(
            studentName = studentName,
            subjectName = subjectDao.getById(klass.subjectId)?.name.orEmpty(),
            groupName = klass.groupId?.let { groupDao.getById(it)?.name },
        )

    /**
     * [student] as a calendar guest: their email, if set, plus whether the tutor wants students invited.
     * Null when the student has no address, since there is then nobody to invite *or* withdraw.
     *
     * The invited flag travels with every mirror rather than being read once at toggle time, so turning
     * invites off and re-syncing withdraws the invitations already sent — the setting governs the
     * tutor's whole calendar, not just the lessons booked after it.
     */
    private fun guestFor(student: StudentEntity?, settings: AppSettings): Guest? =
        student?.email
            ?.takeIf { it.isNotBlank() }
            ?.let { Guest(email = it, invited = settings.emailInvitesEnabled) }
}
