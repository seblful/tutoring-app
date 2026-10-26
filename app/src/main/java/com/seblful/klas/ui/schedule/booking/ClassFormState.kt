package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.lesson.RosterChange
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.schedule.DayTimeline
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.ui.common.LeavableForm
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** What, if anything, to say under the subject picker. */
enum class SubjectNotice {
    /** The group is taught one subject, so there is nothing to pick per lesson. */
    FIXED_BY_GROUP,

    /** The chosen group offers no bookable subject. */
    NONE_FOR_GROUP,

    /** The chosen student has no priced subject yet, so nothing can be billed. */
    NONE_FOR_STUDENT,
}

/**
 * Everything the booking screen draws, with every decision already made.
 *
 * The screen used to receive seven separate flows and work out the answers itself — whether the
 * subject picker is live, whether everyone has been unticked, whether the save may proceed. Those are
 * rules about booking a lesson, not about drawing one, and none of them could be tested where they
 * were. Here they are ordinary properties over ordinary data.
 */
// Keep `isNew` in the view model: the app bar needs it before this state has loaded.
data class ClassFormState(
    val form: ClassForm = ClassForm(),
    val ending: ClassForm.Ending? = null,
    /** The time the lesson takes, once it has a date, a start and a length. */
    val slot: Scheduling.Slot? = null,
    /** The chosen date's other lessons and this one, as the When section draws them; null without a date. */
    val timeline: DayTimeline? = null,
    /**
     * Where the timeline offers to put the lesson while it has no start: the first free time from now —
     * drawn as a frame to tap, never booked by itself.
     */
    val suggestion: Scheduling.Slot? = null,
    /** What the three pickers **say** — the chosen row's name, resolved, or blank where nothing is chosen. */
    val studentName: String = "",
    val groupName: String = "",
    val subjectName: String = "",
    val students: List<Student> = emptyList(),
    val groups: List<Group> = emptyList(),
    val members: List<Student> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val subjectFixedByGroup: Boolean = false,
    /**
     * The classes the lesson already has — empty while booking. Carried so the form can say what
     * unticking somebody would cost (see [billedDrops]) rather than only what it would do.
     */
    val booked: List<TutoringClass> = emptyList(),
    /**
     * The lesson already sitting where this one wants to be, and the only thing that can now be wrong
     * about a chosen time: a start and a length cannot describe an impossible one.
     */
    val conflict: TimeConflict? = null,
    /** Free dates in the whole proposed run; a repeating booking skips the occupied ones. */
    val availableOccurrences: Int = 1,
    /** Whether the tutor has changed anything — what leaving without saving would throw away. */
    override val isDirty: Boolean = false,
) : LeavableForm {
    /**
     * Where one press of the When section's stepper puts the lesson: [STEP_MINUTES] [later] or earlier, on
     * past a lesson in the way ([DayTimeline.shifted]), anywhere in the day rather than only the hours a
     * timeline used to draw. With no start yet, the first free time ([suggestion]). Null when that way
     * holds no free time, or before the day's lessons are read.
     */
    fun stepped(later: Boolean): Instant? {
        val day = timeline ?: return null
        val from = slot
        val step = STEP_MINUTES.minutes
        return if (from == null) {
            suggestion?.start
        } else {
            day.copy(start = day.day.start, end = day.day.end)
                .shifted(from.start, if (later) step else -step, from.durationMinutes)
        }
    }

    /** Whether the lesson yet has somebody to be for. Until then most of the form means nothing. */
    val targetChosen: Boolean get() = form.hasTarget

    /**
     * Offering a dropdown that can only be set to what it already says would invite a choice that
     * isn't there, so a group-bound subject reads as fixed rather than as pickable.
     */
    val subjectPickerEnabled: Boolean get() = targetChosen && !subjectFixedByGroup

    val subjectNotice: SubjectNotice?
        get() = when {
            subjectFixedByGroup -> SubjectNotice.FIXED_BY_GROUP
            !targetChosen || subjects.isNotEmpty() -> null
            form.target == ClassTarget.GROUP -> SubjectNotice.NONE_FOR_GROUP
            else -> SubjectNotice.NONE_FOR_STUDENT
        }

    /** Every member unticked: the lesson would be written for nobody and bill no one. */
    val allMembersExcluded: Boolean
        get() = members.isNotEmpty() && form.excludedMemberIds.containsAll(members.map { it.id })

    /**
     * How many unticked members had a class that already **billed**. Saving deletes those classes and
     * with them income the students were charged, so it is asked about first — the same courtesy
     * deleting a completed lesson gets. Zero while booking, where nothing has billed yet.
     */
    val billedDrops: Int
        get() = RosterChange(dropped = booked.filter { it.studentId in form.excludedMemberIds })
            .billedDropped.size

    /**
     * A group is chosen and nobody in it can be booked — every member archived, or gone from it. The
     * roster simply did not draw, the save stayed enabled, and tapping it was refused with «Отметьте хотя
     * бы одного участника» over a form with nobody to tick.
     */
    val groupHasNoMembers: Boolean
        get() = form.target == ClassTarget.GROUP && form.groupId != null && members.isEmpty()

    val conflictBlocksSave: Boolean get() = conflict != null && (!form.repeat.repeats || availableOccurrences == 0)

    /** Single bookings need their slot; a repeating run needs at least one free date. */
    override val canSave: Boolean
        get() = form.isValid && availableOccurrences > 0 && !conflictBlocksSave &&
            !allMembersExcluded && !groupHasNoMembers

    /**
     * What the form still lacks, in the order it is filled in. Empty exactly when [ClassForm.isValid] is
     * true — the same checks, named, so the save bar can say what is missing instead of greying out.
     */
    val gaps: List<BookingGap>
        get() = buildList {
            if (!form.hasTarget) add(BookingGap.WHO)
            if (form.subjectId == null) add(BookingGap.SUBJECT)
            if (form.date == null) add(BookingGap.DATE)
            if (form.timeFrom == null) add(BookingGap.START)
            if ((form.durationMinutes ?: 0) <= 0) add(BookingGap.LENGTH)
            if (!form.price.isValid) add(BookingGap.PRICE)
        }

    /** What the save bar says — [SaveBar.Ready] exactly when [canSave]. */
    val saveBar: SaveBar
        get() = when {
            gaps.isNotEmpty() -> SaveBar.Missing(gaps)
            conflictBlocksSave || availableOccurrences == 0 -> SaveBar.Taken
            groupHasNoMembers -> SaveBar.NoBookableMembers
            allMembersExcluded -> SaveBar.NoneTicked
            else -> SaveBar.Ready
        }

    /**
     * Whether the folded Price row says in red that the rate is wanted: it is the one thing the booking
     * still lacks. Not while anything else is missing too — a rate is filled in from whoever is chosen, so
     * an empty one before that is not the tutor's to type yet.
     */
    val rateNeeded: Boolean get() = gaps == listOf(BookingGap.PRICE)

    /** What tapping save should do: save, or ask about the income it would destroy first. */
    val saveIntent: SaveIntent
        get() = billedDrops.let { if (it > 0) SaveIntent.ConfirmBilledDrops(it) else SaveIntent.Save }
}

/** What one press of the When section's stepper moves a lesson by. */
const val STEP_MINUTES = 15

/** One thing a booking cannot be saved without — see [ClassFormState.gaps]. */
enum class BookingGap { WHO, SUBJECT, DATE, START, LENGTH, PRICE }

/**
 * The save bar under the booking form: the lesson as it will be booked, or the one reason it cannot be
 * yet. A greyed-out button alone left the tutor to find which field it was waiting for.
 */
sealed interface SaveBar {
    data class Missing(val gaps: List<BookingGap>) : SaveBar

    /** The hour is another lesson's — [ClassFormState.conflict] says whose. */
    data object Taken : SaveBar

    /** The group's members are all archived. */
    data object NoBookableMembers : SaveBar

    /** Every member was unticked. */
    data object NoneTicked : SaveBar

    data object Ready : SaveBar
}

/**
 * What saving this lesson means right now.
 *
 * Unticking a member whose class already **billed** deletes that class and the income with it, so the
 * save asks first — the same courtesy deleting a completed lesson gets.
 */
sealed interface SaveIntent {

    /** Nothing would be lost that the tutor has not already seen. */
    data object Save : SaveIntent

    /**
     * Ask first.
     *
     * @param billedLessons how many billed classes the save would delete — what the question counts.
     */
    data class ConfirmBilledDrops(val billedLessons: Int) : SaveIntent
}

/** What a repeating booking came to, when it could not book all of it. */
data class SeriesOutcome(val booked: Int, val skipped: List<LocalDate>)
