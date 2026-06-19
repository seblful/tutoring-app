package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.calendar.CalendarEvent
import com.seblful.tutoringapp.calendar.CalendarSync
import com.seblful.tutoringapp.data.local.ClassDao
import com.seblful.tutoringapp.data.local.StudentDao
import com.seblful.tutoringapp.data.local.SubjectDao
import com.seblful.tutoringapp.data.toDomain
import com.seblful.tutoringapp.data.toEntity
import com.seblful.tutoringapp.domain.CalendarSyncPolicy
import com.seblful.tutoringapp.domain.ClassRules
import com.seblful.tutoringapp.domain.ReminderScheduler
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.TutoringClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassRepository @Inject constructor(
    private val dao: ClassDao,
    private val studentDao: StudentDao,
    private val subjectDao: SubjectDao,
    private val calendarSync: CalendarSync,
    private val reminderScheduler: ReminderScheduler,
    private val settings: SettingsRepository,
) {
    fun observeById(id: Long): Flow<TutoringClass?> = dao.observeById(id).map { it?.toDomain() }

    fun observeAll(): Flow<List<TutoringClass>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForStudent(studentId: Long): Flow<List<TutoringClass>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    fun observeBetween(from: Instant, to: Instant): Flow<List<TutoringClass>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): TutoringClass? = dao.getById(id)?.toDomain()

    suspend fun getAll(): List<TutoringClass> = dao.getAll().map { it.toDomain() }

    /**
     * Re-mirrors every class to the device calendar. Run after the user turns calendar sync on, to
     * backfill events for classes created while it was off. Best-effort, like all calendar work.
     */
    suspend fun resyncCalendar() {
        if (!syncEnabled()) return
        dao.getAll().forEach { mirrorToCalendar(it.toDomain()) }
    }

    /** Removes every mirrored event. Run when the user turns calendar sync off. */
    suspend fun clearCalendar() {
        dao.getAll().forEach { entity ->
            entity.calendarEventId?.let {
                calendarSync.delete(it)
                dao.update(entity.copy(calendarEventId = null))
            }
        }
    }

    suspend fun add(klass: TutoringClass): Long {
        val id = dao.upsert(klass.toEntity())
        mirrorToCalendar(klass.copy(id = id))
        reminderScheduler.refreshNow()
        return id
    }

    /** Inserts a generated recurring series and returns the number of occurrences. */
    suspend fun addAll(classes: List<TutoringClass>): Int {
        classes.forEach { c -> mirrorToCalendar(c.copy(id = dao.upsert(c.toEntity()))) }
        reminderScheduler.refreshNow()
        return classes.size
    }

    /**
     * Reschedules a class. Moving it to the future re-plans it (scheduled, charge voided); moving
     * it to the past keeps its status. The calendar event and reminders follow the new state.
     */
    suspend fun move(classId: Long, newStart: Instant, now: Instant = Instant.now()): TutoringClass? {
        val current = dao.getById(classId)?.toDomain() ?: return null
        val moved = ClassRules.move(current, newStart, now)
        dao.update(moved.toEntity())
        mirrorToCalendar(moved)
        reminderScheduler.refreshNow()
        return moved
    }

    /** Applies a status change, capturing/voiding the charge per [ClassRules]. */
    suspend fun setStatus(classId: Long, to: ClassStatus): TutoringClass? {
        val current = dao.getById(classId)?.toDomain() ?: return null
        val updated = ClassRules.applyStatus(current, to)
        dao.update(updated.toEntity())
        mirrorToCalendar(updated)
        reminderScheduler.refreshNow()
        return updated
    }

    suspend fun delete(klass: TutoringClass) {
        klass.calendarEventId?.let { calendarSync.delete(it) }
        dao.delete(klass.toEntity())
        reminderScheduler.refreshNow()
    }

    suspend fun nextScheduledAfter(now: Instant): TutoringClass? =
        dao.nextScheduledAfter(now.toEpochMilli())?.toDomain()

    /**
     * Auto-completes every scheduled class whose end time has passed, billing its booked price.
     * Returns the classes that were completed. (Completed classes need no calendar change — their
     * event is already in the past.)
     */
    suspend fun autoCompleteDue(now: Instant): List<TutoringClass> =
        dao.dueForCompletion(now.toEpochMilli())
            .map { it.toDomain() }
            .filter { ClassRules.shouldAutoComplete(it, now) }
            .map { c ->
                val completed = ClassRules.applyStatus(c, ClassStatus.COMPLETED)
                dao.update(completed.toEntity())
                completed
            }

    private suspend fun syncEnabled(): Boolean = settings.get().calendarSyncEnabled

    /**
     * Reflects [klass]'s status into the device calendar. Best-effort: no-ops when calendar sync is
     * off or the calendar is unavailable, so the local database is never blocked by sync.
     */
    private suspend fun mirrorToCalendar(klass: TutoringClass) {
        if (!syncEnabled()) return
        when (CalendarSyncPolicy.actionFor(klass.status)) {
            CalendarSyncPolicy.Action.UPSERT -> {
                val eventId = calendarSync.upsert(klass, titleFor(klass), guestEmailFor(klass))
                if (eventId != null && eventId != klass.calendarEventId) {
                    dao.update(klass.copy(calendarEventId = eventId).toEntity())
                }
            }
            CalendarSyncPolicy.Action.DELETE -> {
                klass.calendarEventId?.let {
                    calendarSync.delete(it)
                    dao.update(klass.copy(calendarEventId = null).toEntity())
                }
            }
            CalendarSyncPolicy.Action.NONE -> Unit
        }
    }

    private suspend fun titleFor(klass: TutoringClass): String {
        val student = studentDao.getById(klass.studentId)?.name.orEmpty()
        val subject = subjectDao.getById(klass.subjectId)?.name.orEmpty()
        return CalendarEvent.title(student, subject)
    }

    /**
     * The student's email, if set — added to the event as a guest so the calendar account (e.g.
     * Google) emails them, including an email reminder an hour before. Blank when unset.
     */
    private suspend fun guestEmailFor(klass: TutoringClass): String? =
        studentDao.getById(klass.studentId)?.email?.takeIf { it.isNotBlank() }
}
