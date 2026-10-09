package com.seblful.klas.domain.homework

import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Homework: set for a student or a group, **attached to their next lesson**, current until it is marked
 * done.
 *
 * ### The rule the feature stands on
 *
 * A piece of homework is checked at **its assignee's first lesson after it was given** — for a student,
 * the next lesson they are in (their own or a group's); for a group, the group's next lesson. A cancelled
 * lesson is skipped, since nothing was checked at an hour that did not happen.
 *
 * That lesson is **derived, never stored**, for the reason `LessonPhase` gives for «Идёт»: the app keeps
 * no series, so when homework is set the next lesson is often not booked yet — and once it is, it can
 * still move, be cancelled, or have another lesson booked in front of it. A stored link would need a
 * writer at each of those and could only ever be late; a derived one follows them for free.
 *
 * Everything here reads a list of classes that has to reach **forward** from when the homework was
 * given, open-ended — the lesson it is attached to is read off what follows.
 */
object HomeworkRules {

    /** The lesson [homework] is attached to, or null while none is booked. See the object's KDoc. */
    fun attachedTo(homework: Homework, classes: List<TutoringClass>): TutoringClass? =
        nextLesson(homework.assignee, homework.givenAt, classes)

    /**
     * [assignee]'s first lesson starting **after** [after], cancelled ones skipped — what homework given at
     * [after] would be attached to. The add dialog asks it with "now", to say which lesson that will be.
     *
     * Strictly after: homework set during a lesson is for the next one, not the one in progress.
     */
    fun nextLesson(assignee: Assignee, after: Instant, classes: List<TutoringClass>): TutoringClass? =
        classes
            .filter { it.start.isAfter(after) && it.status != LessonStatus.CANCELLED && assignee.takes(it) }
            .minByOrNull { it.start }

    /** Where a current piece of homework stands on a given day, as the case rather than two dates. */
    enum class Due {
        /** Its lesson has been and gone, and it is still not marked done. */
        OVERDUE,

        /** Its lesson is today. */
        TODAY,

        /** Its lesson is on a later day. */
        LATER,

        /**
         * No next lesson is booked. Listed rather than dropped, because it is also the one place the app
         * notices that somebody has homework and no lesson to bring it to.
         */
        UNBOOKED,
    }

    /** One current piece of homework with the lesson it is attached to — null while none is booked. */
    data class Current(val homework: Homework, val lesson: TutoringClass?) {
        fun due(today: LocalDate, zone: ZoneId): Due {
            val day = lesson?.start?.atZone(zone)?.toLocalDate() ?: return Due.UNBOOKED
            return when {
                day.isBefore(today) -> Due.OVERDUE
                day.isEqual(today) -> Due.TODAY
                else -> Due.LATER
            }
        }
    }

    /**
     * What a list of homework shows: the [current] ones and the [archive].
     *
     * @param current in the order their lessons come — so what is overdue is on top, then today's, then
     *   later ones — and the unbooked last, the most recently given first.
     * @param archive the done ones, the most recently done first.
     */
    data class Lists(val current: List<Current>, val archive: List<Homework>)

    /** [homework] split and ordered as [Lists] says, each current one beside its lesson. */
    fun lists(homework: List<Homework>, classes: List<TutoringClass>): Lists {
        val (done, current) = homework.partition { it.isDone }
        return Lists(
            current = current
                .map { Current(it, attachedTo(it, classes)) }
                .sortedWith(
                    compareBy<Current> { it.lesson == null }
                        .thenBy { it.lesson?.start }
                        .thenByDescending { it.homework.givenAt },
                ),
            archive = done.sortedByDescending { it.doneAt },
        )
    }

    /**
     * The homework in [homework] whose assignee is still taught — the «Задания» tab's. The archive is how
     * somebody stops being taught, and homework for them is not a thing to do; it stays on their card.
     */
    fun taught(homework: List<Homework>, students: List<Student>, groups: List<Group>): List<Homework> {
        val activeStudents = students.filterNot { it.archived }.mapTo(HashSet()) { it.id }
        val activeGroups = groups.filterNot { it.archived }.mapTo(HashSet()) { it.id }
        return homework.filter { hw ->
            when (val assignee = hw.assignee) {
                is Assignee.Student -> assignee.studentId in activeStudents
                is Assignee.Group -> assignee.groupId in activeGroups
            }
        }
    }

    /**
     * [homework] narrowed to [who] — the «Задания» tab's filter. A group's is the group's own; a student's
     * is their own **and their groups'**, from [rosters] (group id → member ids), since that is what they
     * have to do — the same set their card lists.
     */
    fun narrowedTo(homework: List<Homework>, who: Assignee, rosters: Map<Long, List<Long>>): List<Homework> =
        when (who) {
            is Assignee.Group -> homework.filter { it.assignee == who }
            is Assignee.Student -> {
                val theirGroups = rosters.filterValues { who.studentId in it }.keys
                homework.filter { hw ->
                    when (val assignee = hw.assignee) {
                        is Assignee.Student -> assignee == who
                        is Assignee.Group -> assignee.groupId in theirGroups
                    }
                }
            }
        }

    /**
     * Every piece of homework in [homework] attached to [session] — what its sheet shows, done ones
     * included so a tick stays visible, and what its reminder names, current ones only. Oldest first.
     *
     * [classes] has to reach back to the earliest of them and up to [session] itself.
     */
    fun attachedToLesson(session: ClassSession, homework: List<Homework>, classes: List<TutoringClass>): List<Homework> =
        homework
            .filter { attachedTo(it, classes)?.lessonId == session.lessonId }
            .sortedBy { it.givenAt }

    /**
     * [assignees] with **whoever's lesson is nearest to [now] first** — the add dialog's order.
     *
     * Homework is set for the student just taught, or being taught, so the one to pick is nearly always
     * whoever's lesson is closest to this minute. Closest to the **lesson**, not its start: zero while it is
     * under way, from its end once it is over, to its start before it — so at 19:05, after an 18:00–19:00
     * lesson, that student is above one booked for 19:30. Nearest rather than latest, for a tutor writing
     * homework up the evening before as well as one walking out of the room.
     *
     * **On a group lesson the group comes first**, level with its members: a group's homework is the one
     * set there. Anybody with no lesson in [classes] keeps their place after the rest — [assignees] arrives
     * in name order, and the sort is stable.
     */
    fun byNearestLesson(assignees: List<Assignee>, classes: List<TutoringClass>, now: Instant): List<Assignee> {
        val live = classes.filter { it.status != LessonStatus.CANCELLED }
        val distance = assignees.associateWith { assignee ->
            live.filter { assignee.takes(it) }.minOfOrNull { distanceFrom(now, it) }
        }
        return assignees.sortedWith(
            compareBy<Assignee, Duration?>(nullsLast()) { distance[it] }
                .thenBy { if (distance[it] != null && it is Assignee.Group) 0 else 1 },
        )
    }

    /** How far [now] is from [klass]'s hour — nothing while it is on. */
    private fun distanceFrom(now: Instant, klass: TutoringClass): Duration = when {
        now.isBefore(klass.start) -> Duration.between(now, klass.start)
        now.isAfter(klass.end) -> Duration.between(klass.end, now)
        else -> Duration.ZERO
    }
}

/**
 * Whether [klass] is one of this assignee's lessons — a student's own class, in a lesson of their own or a
 * group's, or any class of the group's.
 *
 * Public because the repository asks it too, narrowing the homework that could be attached to a lesson
 * before reading lessons for it (`HomeworkRepository.candidatesFor`): a second spelling of "whose lesson
 * is this" there could disagree with the rule that attaches it here.
 */
fun Assignee.takes(klass: TutoringClass): Boolean = when (this) {
    is Assignee.Student -> klass.studentId == studentId
    is Assignee.Group -> klass.groupId == groupId
}
