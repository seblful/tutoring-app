package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.lesson.DeleteWarning
import com.seblful.klas.domain.lesson.SessionRules
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.people.Captions
import com.seblful.klas.domain.schedule.CalendarLayout
import com.seblful.klas.domain.schedule.DaySummary
import com.seblful.klas.ui.common.state.Loadable
import com.seblful.klas.ui.common.state.readyOrLoadingAfter
import com.seblful.klas.ui.common.state.valueOrNull
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * One row of the day's schedule: a lesson, which is either one student's class or a whole group
 * session.
 *
 * [members] names a group lesson's students so the row can say who is in the room. Names only, and
 * not a row per member: a lesson is acted on as a whole here, and **who** is in one is changed on
 * the lesson's own form — the schedule's actions are about the lesson's time and status.
 */
data class ScheduleRow(
    val session: ClassSession,
    /** Group name for a group lesson, student name otherwise. */
    val title: String,
    val subjectName: String,
    /** A group lesson's students, in class order. Empty for an individual lesson — [title] is them. */
    val members: List<String> = emptyList(),
) {
    val klass: TutoringClass get() = session.first
    val isGroup: Boolean get() = session.isGroup
    val classes: List<TutoringClass> get() = session.classes

    /** What deleting this lesson has to warn about — see [DeleteWarning]. */
    val deleteWarning: DeleteWarning get() = DeleteWarning.of(session)

    /**
     * The statuses this lesson may be moved to — what the sheet's segmented control enables, and
     * what [canSwipeComplete] is gated on. [SessionRules.sessionTransitions] asked once, here, where
     * a test can reach it; it used to be asked by two composables, so the rule gating the product's
     * highest-frequency gesture could be exercised only by swiping a card on a phone.
     */
    val statusChoices: Set<LessonStatus> get() = SessionRules.sessionTransitions(session)

    /**
     * Whether the swipe-to-complete gesture is offered at all: completing must be legal for some
     * member, so the gesture can never do what the sheet's menu would refuse.
     *
     * It cannot contradict the card's badge, which is worth knowing rather than guarding: only a
     * *scheduled* class may be completed, and a lesson with a scheduled member reads «Запланировано»
     * ([ClassSession.status]). So a lesson badged «Проведено» — including a taught one whose absentee
     * was cancelled afterwards — offers no swipe, because reinstating that member is a correction to
     * their class and not a thing done to the whole lesson.
     */
    val canSwipeComplete: Boolean get() = LessonStatus.COMPLETED in statusChoices

    /**
     * Whether the card recedes — dimmed, its time struck through. A cancelled lesson is a record,
     * not a plan, and the question a glance at the day answers is "how full is it": a lesson whose
     * every class was called off gave its hour back. One whose members disagree keeps full strength,
     * because some of it did happen — which is exactly what [ClassSession.status] answers.
     */
    val recedes: Boolean get() = session.status == LessonStatus.CANCELLED

    companion object {
        /**
         * [session] captioned by [captions] — the whole of turning a stored lesson into a drawable row.
         *
         * Here rather than in the view model's private helper because the rule it carries is small,
         * shared and easy to get subtly wrong: a group lesson is titled by its **group** and names its
         * members, an individual one is titled by its **student** and names nobody. It was reachable only
         * through 1,352 lines of `ScheduleViewModelTest` before, being private to the view model.
         */
        fun of(session: ClassSession, captions: Captions): ScheduleRow = ScheduleRow(
            session = session,
            title = captions.titleOf(session),
            subjectName = captions.subjectOf(session),
            members = captions.membersOf(session),
        )
    }
}

/**
 * One day's section of the week agenda. Only days that actually have classes get one.
 *
 * @param summary what the day adds up to — see [DaySummary]. Carried rather than computed by the header
 *   that prints it, because it sums money over every class of every lesson and belongs in the derivation
 *   that already runs off the main thread.
 */
data class WeekSection(
    val date: LocalDate,
    val rows: List<ScheduleRow>,
    val summary: DaySummary,
)

/**
 * One piece of homework attached to the lesson a sheet is open on — see `HomeworkRules.attachedToLesson`.
 *
 * @param who whom it is for, **only where the sheet does not already say**: a group lesson's sheet is
 *   titled by the group, so a member's own homework attached to it names the member; the group's, and an
 *   individual lesson's, need nobody named.
 */
data class SheetHomework(val homework: Homework, val who: String? = null)

/**
 * How the schedule presents its classes: one day at a time, a whole week's agenda, or a month grid
 * over the selected day's list.
 */
enum class ScheduleViewMode { DAY, WEEK, MONTH }

/**
 * Where a tap in the tray asks the schedule to be — see [ScheduleViewModel.arrive]. The view the tutor
 * left it in is kept; what moves is the day, and for a reminder the lesson's sheet.
 */
sealed interface ScheduleTarget {

    /** Today — the reminders' summary, and an evening digest posted before digests carried their day. */
    data object Today : ScheduleTarget

    /** A digest's day, or the latest day auto-completion settled. */
    data class Day(val date: LocalDate) : ScheduleTarget

    /**
     * The day the lesson [classId] belongs to is on **now**, with its sheet open — a tap on its reminder.
     * An id rather than a day, because a lesson can be moved after its reminder was posted.
     */
    data class Lesson(val classId: Long) : ScheduleTarget
}

/**
 * The schedule narrowed to one student — who, and the name the chip saying so is drawn from.
 *
 * **The name is carried rather than looked up by the screen**, and that is the point of the type: it is
 * read from every student rather than the active ones, so archiving somebody the schedule is currently
 * filtered by leaves the chip naming them instead of going blank. The same value is what the lesson
 * queries narrow on, so the chip and the list cannot disagree about whether a filter is applied — a
 * filtered calendar looking empty for no visible reason is the failure this whole feature has to avoid.
 *
 * Resolving to null when the id matches nobody is deliberate and is why both read this and not the id: a
 * restored backup can take a student away, and an empty schedule filtered by a person who no longer
 * exists would have no way out.
 */
data class StudentFilter(val studentId: Long, val name: String)

/**
 * The lessons the schedule body shows — the [Week] the view model assembles, and the [Day] read off it.
 *
 * There is one assembly rather than a flow per view: the view model reads the week that holds what is on
 * screen (see [ScheduleViewModel.content]), and the day and month views read their day out of it.
 */
sealed interface ScheduleContent {

    /**
     * The selected day's rows and what they add up to — what the day and month views both show.
     *
     * [date] is the day these rows *are*, which is not always the day selected: when a tap lands in
     * another week, the previous week stays up for the frames the new one takes to read (see
     * [readyOrLoadingAfter]), and a heading over it must name the day it shows rather than the one asked
     * for.
     */
    data class Day(
        val date: LocalDate,
        val rows: List<ScheduleRow>,
        val summary: DaySummary,
    ) : ScheduleContent

    /**
     * The week's sections, earliest day first; each day carries its own summary. [start] is the week's
     * first day, so a day the week holds no section for is known to be free *in this week* — see [day].
     */
    data class Week(val start: LocalDate, val sections: List<WeekSection>) : ScheduleContent

    /**
     * Every lesson row this content holds, whichever shape it is — asked by [ScheduleViewModel.running],
     * which is about the clock and not about the view, and by a reminder's tap looking for its lesson.
     */
    val rowsShown: List<ScheduleRow>
        get() = when (this) {
            is Day -> rows
            is Week -> sections.flatMap { it.rows }
        }
}

/**
 * **[date]'s day, read off the week that holds it** — its section, or a free day if it has none.
 *
 * The day and month views used to run a query of their own, re-keyed on every tap: each day tap blinked
 * the list out and back while the next day was read, and «Неделя» → «День» stood empty for the frames
 * that took although every lesson on that day was already in hand. Reading the day out of its week makes
 * both a lookup.
 *
 * A week that does not cover [date] answers [Loadable.Loading]: it is the previous week, still up while
 * [date]'s is read, and says nothing about this day. That is what keeps yesterday's lessons from being
 * listed under today's date, and an empty Tuesday from saying «Нет занятий» about a busy Wednesday.
 */
fun Loadable<ScheduleContent.Week>.day(date: LocalDate): Loadable<ScheduleContent.Day> {
    val week = valueOrNull() ?: return Loadable.Loading
    if (date < week.start || date >= week.start.plus(CalendarLayout.DAYS_IN_WEEK.toLong(), DateTimeUnit.DAY)) {
        return Loadable.Loading
    }
    val section = week.sections.firstOrNull { it.date == date }
    return Loadable.Ready(
        ScheduleContent.Day(
            date = date,
            rows = section?.rows.orEmpty(),
            summary = section?.summary ?: DaySummary.NONE,
        ),
    )
}
