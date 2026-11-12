package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.domain.lesson.ClassRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.schedule.DayBounds
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.format.zones
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * What is already booked around the lesson a form describes: whether its hour, or any of its repeats, is
 * taken ([clashes]), and the rest of its day as the timeline draws it ([day]). The lesson being edited
 * ([editing]) never stands in its own way. [students] and [groups] name the lessons found.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BookingConflicts(
    private val classes: ClassRepository,
    private val students: Flow<List<Student>>,
    private val groups: Flow<List<Group>>,
    private val clock: WallClock,
) {
    /** The first taken occurrence, named, and how many of the form's occurrences are free. */
    data class Conflicts(val first: TimeConflict? = null, val available: Int = 1)

    fun clashes(form: Flow<ClassForm>, editing: Flow<ClassSession?>): Flow<Conflicts> =
        combine(form, editing, clock.zones()) { f, edited, zone ->
            val slot = f.slot(zone) ?: return@combine null
            // A correction that leaves a past lesson cancelled reserves no time.
            if (edited != null && !ClassRules.move(edited.first, slot.start, clock.now()).status.occupiesTime) {
                return@combine null
            }
            f.starts(zone).map { Scheduling.Slot(it, slot.durationMinutes) } to edited
        }.distinctUntilChanged().flatMapLatest { check ->
            if (check == null) {
                flowOf(Conflicts())
            } else {
                val (slots, edited) = check
                val window = Scheduling.windowFor(slots)
                combine(
                    classes.observeBetween(window.from, window.toExclusive),
                    students,
                    groups,
                ) { existing, studentList, groupList ->
                    val clashes = slots.map { Scheduling.conflicts(it, existing, edited?.ids.orEmpty()).firstOrNull() }
                    Conflicts(
                        first = clashes.firstNotNullOfOrNull { it }?.let { describe(it, studentList, groupList) },
                        available = clashes.count { it == null },
                    )
                }
            }
        }

    /** The form's date as the timeline draws it, without the edited lesson in it; null until there is a date. */
    fun day(form: Flow<ClassForm>, editing: Flow<ClassSession?>): Flow<DayTimeline?> =
        combine(form.map { it.date }.distinctUntilChanged(), editing, clock.zones()) { date, edited, zone ->
            date?.let { DayKey(it, edited?.ids.orEmpty(), zone) }
        }.distinctUntilChanged()
            .flatMapLatest { key -> if (key == null) flowOf(null) else dayTimeline(key) }

    private data class DayKey(val date: LocalDate, val ignoring: Set<Long>, val zone: TimeZone)

    private fun dayTimeline(key: DayKey): Flow<DayTimeline> {
        val bounds = DayBounds.of(key.date, key.zone)
        val wholeDay = Scheduling.Slot(bounds.start, (bounds.end - bounds.start).inWholeMinutes.toInt())
        val window = Scheduling.windowFor(wholeDay)
        return combine(
            classes.observeBetween(window.from, window.toExclusive),
            students,
            groups,
        ) { existing, studentList, groupList ->
            val booked = Scheduling.conflicts(wholeDay, existing, key.ignoring).map { session ->
                DayTimeline.Lesson.of(
                    session,
                    groupName = session.groupId?.let { id -> groupList.firstOrNull { it.id == id }?.name },
                    studentName = studentList.firstOrNull { it.id == session.first.studentId }?.name,
                )
            }
            DayTimeline.of(key.date, key.zone, booked)
        }
    }

    private fun describe(clash: ClassSession, students: List<Student>, groups: List<Group>): TimeConflict =
        TimeConflict.of(
            clash = clash,
            groupName = clash.groupId?.let { id -> groups.firstOrNull { it.id == id }?.name },
            studentName = students.firstOrNull { it.id == clash.first.studentId }?.name,
        )
}
