package com.seblful.klas.domain

import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.HomeworkResult
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.TutoringClass
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Homework: set at one lesson, checked at the next.
 *
 * ### The rule the feature stands on
 *
 * Homework is **due at the next lesson on its track** — the group's next lesson in that subject, or, for
 * an individual lesson, the same student's next individual lesson *in the same subject*: maths homework is
 * checked at maths, not at Tuesday's physics. A cancelled lesson is skipped, since nothing was checked at
 * an hour that did not happen.
 *
 * That lesson is **derived, never stored**, for the reason `LessonPhase` gives for «Идёт». The app keeps
 * no series, so when homework is set the next lesson is often not booked yet — and once it is, it can
 * still move, be cancelled, or have another lesson booked in front of it. A stored link would need a
 * writer at each of those and could only ever be late; a derived one cannot go stale. What *is* stored is
 * the text (`lessons.homework`) and each student's verdict (`attendance.homeworkResult`), both on the
 * lesson it was set at.
 */
object Homework {

    /**
     * How far back homework is still followed up: the tab lists nothing set before it, and the check a
     * lesson's sheet and reminder offer looks no further back than this for the lesson before it.
     *
     * Two months is several lessons on any track a tutor keeps, so the cut only ever falls on homework
     * nobody is going to check now — a student who stopped coming without being archived, a track the
     * tutor stopped setting homework on. Without it both would sit in the list for good, and a to-do list
     * that never empties is a backlog. It also bounds the read: the tab queries a window, not the history.
     */
    val FOLLOWED_UP_FOR: Duration = Duration.ofDays(60)

    /** Whose next lesson checks a piece of homework — see the object's KDoc. */
    private data class Track(val groupId: Long?, val studentId: Long?, val subjectId: Long)

    private fun trackOf(session: ClassSession): Track =
        if (session.isGroup) Track(session.groupId, null, session.first.subjectId)
        else Track(null, session.first.studentId, session.first.subjectId)

    /**
     * One lesson's homework, and the lesson it is checked at.
     *
     * @param set the lesson it was set at — every member's class, each carrying their own
     *   [TutoringClass.homeworkResult]. The text is the lesson's, so every class of it carries the same.
     * @param due the next lesson on its track that is not cancelled, or **null while none is booked**.
     */
    data class Assignment(val set: ClassSession, val due: ClassSession?) {
        val text: String get() = set.first.homework

        /** Somebody it was set for has not been marked yet. */
        val open: Boolean get() = set.classes.any { it.homeworkResult == null }

        /** Where it stands on [today] — see [Stage]. */
        fun stage(today: LocalDate, zone: ZoneId): Stage {
            if (!open) return Stage.CHECKED
            val dueDay = due?.start?.atZone(zone)?.toLocalDate() ?: return Stage.UNBOOKED
            return when {
                dueDay.isAfter(today) -> Stage.WAITING
                dueDay.isEqual(today) -> Stage.TODAY
                else -> Stage.OVERDUE
            }
        }
    }

    /**
     * Where one piece of homework stands, as the case rather than the dates it is read from — the tab's
     * two sides and their sections are exactly these, so no screen compares a due day with today itself.
     */
    enum class Stage {
        /** Its lesson is today: the check the tab and the sheet exist for. */
        TODAY,

        /** Its lesson has been and gone, and not everybody was marked. */
        OVERDUE,

        /** Its lesson is booked, on a later day. */
        WAITING,

        /**
         * No next lesson is booked on its track. Listed rather than dropped, because it is also the one
         * place the app notices that a student has homework and nothing to bring it to.
         */
        UNBOOKED,

        /** Everybody it was set for has been marked. */
        CHECKED,
    }

    /**
     * Every lesson in [classes] that set homework, paired with the lesson it is due at, by track.
     *
     * [classes] has to reach **forward** from the lessons asked about — the due lesson is read off what
     * follows — so a caller passes a window that starts where it wants to look from and runs open-ended.
     * A cancelled lesson sets nothing: homework given at an hour that did not happen is a contradiction the
     * app does not try to follow up.
     */
    private fun byTrack(classes: List<TutoringClass>): Collection<List<Assignment>> =
        GroupRules.sessions(classes)
            .groupBy(::trackOf)
            .values
            .map { timeline ->
                // `sessions` orders by start and `groupBy` keeps that order, so each track is a timeline
                // and "the next lesson" is simply the next index that was not called off.
                timeline.mapIndexedNotNull { index, session ->
                    if (session.first.homework.isBlank() || session.status == LessonStatus.CANCELLED) {
                        return@mapIndexedNotNull null
                    }
                    val due = timeline.drop(index + 1).firstOrNull { it.status != LessonStatus.CANCELLED }
                    Assignment(session, due)
                }
            }

    /** Every piece of homework in [classes] — see [byTrack] for what the list has to contain. */
    fun assignments(classes: List<TutoringClass>): List<Assignment> = byTrack(classes).flatten()

    /**
     * The homework checked at [session]: set at the lesson before it on its track, or null when that
     * lesson set none (or there is none in [classes]). What the lesson's sheet offers to mark and what its
     * reminder says to check.
     *
     * Only ever the one lesson immediately before: homework set two lessons back was due at the lesson in
     * between, and moves on only if that lesson is cancelled — which [assignments] already accounts for.
     */
    fun dueAt(session: ClassSession, classes: List<TutoringClass>): Assignment? =
        assignments(classes).firstOrNull { it.due?.lessonId == session.lessonId }

    /**
     * One lesson's two pieces of homework: [checks], set at the lesson before and checked at this one, and
     * [sets], given at this one — what its sheet shows, the first to mark and the second to send.
     */
    data class AtLesson(val checks: Assignment?, val sets: Assignment?)

    /**
     * [AtLesson] for [session], from [classes] reaching back to the lesson before it and **forward** past it
     * — the homework it sets is due at whatever follows, and saying which day is what makes it sendable.
     */
    fun atLesson(session: ClassSession, classes: List<TutoringClass>): AtLesson {
        val all = assignments(classes)
        return AtLesson(
            checks = all.firstOrNull { it.due?.lessonId == session.lessonId },
            sets = all.firstOrNull { it.set.lessonId == session.lessonId },
        )
    }

    /**
     * What the «Задания» tab lists, one list per [Stage] it shows.
     *
     * @param today due today, in the order the lessons come.
     * @param overdue due at a lesson already past, the most recent first.
     * @param waiting due at a later day's lesson, soonest first.
     * @param unbooked with no next lesson booked, the most recently set first.
     */
    data class Worklist(
        val today: List<Assignment>,
        val overdue: List<Assignment>,
        val waiting: List<Assignment>,
        val unbooked: List<Assignment>,
    ) {
        /** The «Проверить» side: today's checks, then the ones nobody got round to. */
        val toCheck: List<Assignment> get() = today + overdue

        /** The «Выдано» side: set, and not due yet. */
        val handedOut: List<Assignment> get() = waiting + unbooked
    }

    /**
     * The open homework worth listing at [now]: **the latest on each track**, set within
     * [FOLLOWED_UP_FOR], for students and groups still taught.
     *
     * **Latest on each track, because newer homework replaces older.** At the lesson the old homework is
     * due, the tutor usually sets the next — and from then on "what Аня has for maths" is the new one. An
     * older assignment left unmarked is a check the tutor chose not to make; it stays on the student's card,
     * where the record lives, and leaves the list, which is about what to do next.
     *
     * Archived students and groups are left out, and so are archived members of a group's homework: the
     * archive is how somebody stops being taught, and a to-do about them is not one. A group's homework
     * nobody active was set is dropped whole.
     */
    fun worklist(
        classes: List<TutoringClass>,
        now: Instant,
        zone: ZoneId,
        students: List<Student>,
        groups: List<Group>,
    ): Worklist {
        val today = now.atZone(zone).toLocalDate()
        val since = now.minus(FOLLOWED_UP_FOR)
        val active = students.filterNot { it.archived }.mapTo(mutableSetOf()) { it.id }
        val retiredGroups = groups.filter { it.archived }.mapTo(mutableSetOf()) { it.id }

        val listed = byTrack(classes)
            .mapNotNull { track -> track.maxByOrNull { it.set.start } }
            .filterNot { it.set.start.isBefore(since) }
            .filterNot { it.set.groupId in retiredGroups }
            .mapNotNull { assignment ->
                val taught = assignment.set.classes.filter { it.studentId in active }
                if (taught.isEmpty()) null
                else assignment.copy(set = assignment.set.copy(classes = taught))
            }
            .groupBy { it.stage(today, zone) }

        fun of(stage: Stage) = listed[stage].orEmpty()
        return Worklist(
            today = of(Stage.TODAY).sortedBy { it.due?.start },
            overdue = of(Stage.OVERDUE).sortedByDescending { it.due?.start },
            waiting = of(Stage.WAITING).sortedBy { it.due?.start },
            unbooked = of(Stage.UNBOOKED).sortedByDescending { it.set.start },
        )
    }

    /**
     * How one student has done their homework, over [classes] — their own classes, every lesson that set
     * them some (a cancelled one set nothing).
     *
     * Counted per student and not per lesson, which is the difference from everything above: a group's
     * homework is one assignment to follow up and four verdicts to read, and this is a reading.
     */
    data class Record(val done: Int, val partial: Int, val notDone: Int, val unchecked: Int) {
        /** How many have a verdict at all — what «сделано N из M» is out of. */
        val checked: Int get() = done + partial + notDone

        /**
         * Whether there is anything to read yet. «0 из 0» about a student the tutor never marks homework
         * for would be a number about how the app is used, not about them — so the card shows no tile.
         */
        val hasVerdicts: Boolean get() = checked > 0

        companion object {
            fun of(classes: List<TutoringClass>): Record {
                val set = setFor(classes)
                return Record(
                    done = set.count { it.homeworkResult == HomeworkResult.DONE },
                    partial = set.count { it.homeworkResult == HomeworkResult.PARTIAL },
                    notDone = set.count { it.homeworkResult == HomeworkResult.NOT_DONE },
                    unchecked = set.count { it.homeworkResult == null },
                )
            }
        }
    }

    /**
     * The classes among [classes] whose lesson set homework, newest first — a student's homework history,
     * which is what their card lists. Cancelled lessons are left out, as everywhere here.
     */
    fun setFor(classes: List<TutoringClass>): List<TutoringClass> =
        classes
            .filter { it.homework.isNotBlank() && it.status != LessonStatus.CANCELLED }
            .sortedByDescending { it.start }
}
