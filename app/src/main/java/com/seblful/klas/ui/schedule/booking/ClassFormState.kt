package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.lesson.RosterChange
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.schedule.TimeConflict
import com.seblful.klas.ui.common.LeavableForm
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

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
    /** hether the tutor has changed anything — what leaving without saving would throw away. */
    override val isDirty: Boolean = false,
) : LeavableForm {
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

    /** A group was chosen but there are none to choose — worded differently from "none picked". */
    val hasNoGroups: Boolean get() = form.target == ClassTarget.GROUP && groups.isEmpty()

    /**
     * A group is chosen and nobody in it can be booked — every member archived, or gone from it. The
     * roster simply did not draw, the save stayed enabled, and tapping it was refused with «Отметьте хотя
     * бы одного участника» over a form with nobody to tick.
     */
    val groupHasNoMembers: Boolean
        get() = form.target == ClassTarget.GROUP && form.groupId != null && members.isEmpty()

    /**
     * Whether saving may be attempted. A taken slot is as disqualifying as a missing field: the
     * lesson could not happen. The save re-checks against the database regardless, since the slot
     * can be taken between here and there.
     */
    override val canSave: Boolean
        get() = form.isValid && conflict == null && !allMembersExcluded && !groupHasNoMembers

    /** What tapping save should do: save, or ask about the income it would destroy first. */
    val saveIntent: SaveIntent
        get() = billedDrops.let { if (it > 0) SaveIntent.ConfirmBilledDrops(it) else SaveIntent.Save }
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
