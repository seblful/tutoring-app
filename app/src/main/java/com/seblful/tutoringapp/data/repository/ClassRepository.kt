package com.seblful.tutoringapp.data.repository

import com.seblful.tutoringapp.calendar.CalendarEvent
import com.seblful.tutoringapp.calendar.CalendarSync
import com.seblful.tutoringapp.data.local.ClassDao
import com.seblful.tutoringapp.data.local.PriceDao
import com.seblful.tutoringapp.data.local.StudentDao
import com.seblful.tutoringapp.data.local.SubjectDao
import com.seblful.tutoringapp.data.toDomain
import com.seblful.tutoringapp.data.toEntity
import com.seblful.tutoringapp.domain.CalendarSyncPolicy
import com.seblful.tutoringapp.domain.ClassRules
import com.seblful.tutoringapp.domain.ReminderScheduler
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.TutoringClass
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClassRepository @Inject constructor(
    private val dao: ClassDao,
    private val priceDao: PriceDao,
    private val studentDao: StudentDao,
    private val subjectDao: SubjectDao,
    private val calendarSync: CalendarSync,
    private val reminderScheduler: ReminderScheduler,
) {
    fun observeById(id: Long): Flow<TutoringClass?> = dao.observeById(id).map { it?.toDomain() }

    fun observeAll(): Flow<List<TutoringClass>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeForStudent(studentId: Long): Flow<List<TutoringClass>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    fun observeBetween(from: Instant, to: Instant): Flow<List<TutoringClass>> =
        dao.observeBetween(from.toEpochMilli(), to.toEpochMilli()).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): TutoringClass? = dao.getById(id)?.toDomain()

    suspend fun getAll(): List<TutoringClass> = dao.getAll().map { it.toDomain() }

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

    suspend fun move(classId: Long, newStart: Instant): TutoringClass? {
        val current = dao.getById(classId)?.toDomain() ?: return null
        val moved = ClassRules.move(current, newStart)
        dao.update(moved.toEntity())
        mirrorToCalendar(moved) // still scheduled -> updates the existing event's time
        reminderScheduler.refreshNow()
        return moved
    }

    /** Applies a status change, capturing/voiding the charge per [ClassRules]. */
    suspend fun setStatus(classId: Long, to: ClassStatus): TutoringClass? {
        val current = dao.getById(classId)?.toDomain() ?: return null
        val price = currentPrice(current)
        val updated = ClassRules.applyStatus(current, to, price)
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
     * Auto-completes every scheduled class whose end time has passed, snapshotting its
     * then-current price. Returns the classes that were completed. (Completed classes need no
     * calendar change — their event is already in the past.)
     */
    suspend fun autoCompleteDue(now: Instant): List<TutoringClass> =
        dao.dueForCompletion(now.toEpochMilli())
            .map { it.toDomain() }
            .filter { ClassRules.shouldAutoComplete(it, now) }
            .map { c ->
                val completed = ClassRules.applyStatus(c, ClassStatus.COMPLETED, currentPrice(c))
                dao.update(completed.toEntity())
                completed
            }

    private suspend fun currentPrice(c: TutoringClass): Money =
        priceDao.get(c.studentId, c.subjectId)?.let { Money(it.amountMinor) } ?: Money.ZERO

    /**
     * Reflects [klass]'s status into the device calendar. Best-effort: when the calendar is
     * unavailable the calls no-op, so the local database is never blocked by sync.
     */
    private suspend fun mirrorToCalendar(klass: TutoringClass) {
        when (CalendarSyncPolicy.actionFor(klass.status)) {
            CalendarSyncPolicy.Action.UPSERT -> {
                val eventId = calendarSync.upsert(klass, titleFor(klass))
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
}
