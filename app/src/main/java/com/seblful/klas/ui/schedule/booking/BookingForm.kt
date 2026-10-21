package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.data.settings.AppSettings
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.format.MoneyField
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.minutes

/**
 * Matches `AppSettings.defaultLessonMinutes`, for the window before settings have loaded.
 *
 * Here rather than on the view model because it is the length [BookingForm] gives a lesson that has a
 * start but no length yet — a rule of the form, not of the screen holding it.
 */
const val DEFAULT_LESSON_MINUTES = 60

/**
 * What a form transition may need to know that is not in the form itself.
 *
 * These are the picker contents the view model already holds as flows. They are passed in rather than
 * read, which is what lets every rule below run without a repository, a dispatcher or a scope.
 */
data class BookingContext(
    val students: List<Student> = emptyList(),
    val groups: List<Group> = emptyList(),
    val defaultLessonMinutes: Int = DEFAULT_LESSON_MINUTES,
)

/** Every way the booking form can change — the tutor's edits and the answers the app went to look up. */
sealed interface BookingIntent {

    /** Whether this change is the tutor's work, and so makes the form unsaved. */
    val isEdit: Boolean get() = true

    /** Book for a student, or for a group. */
    data class ChooseTarget(val target: ClassTarget) : BookingIntent

    /**
     * @param known the student's row when the caller already has it. The auto-selection reads it from
     *   the repository at a point where the students list may not have emitted, and their default class
     *   mode has to come from somewhere.
     */
    data class ChooseStudent(val studentId: Long, val known: Student? = null) : BookingIntent

    data class ChooseGroup(val groupId: Long) : BookingIntent

    data class ChooseSubject(val subjectId: Long) : BookingIntent

    data class IncludeMember(val studentId: Long, val included: Boolean) : BookingIntent

    data class StartAt(val time: LocalTime) : BookingIntent

    /**
     * How long the lesson runs — from a chip or from «Другая», which is the only way the form takes a
     * length. There is no `EndAt`: an end time was a second way of saying this, and the way that could
     * say something impossible.
     */
    data class LastsFor(val minutes: Int) : BookingIntent

    /** Anything the form edits directly: the date, the mode, the typed rate. */
    data class Edit(val transform: (ClassForm) -> ClassForm) : BookingIntent

    /**
     * [inner], applied without marking the form unsaved — for a change the app makes on the tutor's
     * behalf: the group the form was opened from, the only student there is.
     */
    data class AsSetup(val inner: BookingIntent) : BookingIntent {
        override val isEdit: Boolean get() = false
    }

    // ---- Answers, not edits --------------------------------------------------------------------

    /** What the app went to find out, coming back — never the tutor's work, so never an edit. */
    sealed interface Answer : BookingIntent {
        override val isEdit: Boolean get() = false
    }

    /**
     * The tutor's usual length, once settings have loaded. A new lesson shows it from the start, so the
     * length it is about to book is on screen before a start is picked.
     */
    data class DefaultLength(val minutes: Int) : Answer

    /** The only student there is, for a new lesson — with several, the tutor picks and nothing is guessed. */
    data class OnlyStudent(val student: Student) : Answer

    /** The lesson being edited, loaded from the database. Not the tutor's work. */
    data class Load(val form: ClassForm) : Answer

    /** The group the form was pointed at, with its rate, mode and subject. */
    data class GroupLoaded(val group: Group) : Answer

    /** This student's price for this subject, or null where they have none; both ids identify the answer. */
    data class PriceLoaded(val studentId: Long, val subjectId: Long, val amount: Money?) : Answer

    /**
     * The single subject the current target can be booked for — there was nothing to pick.
     *
     * @param askedFor the target this was looked up for. A fast second choice must not have the first
     *   one's subject land on it.
     */
    data class OnlySubject(val askedFor: SubjectKey, val subjectId: Long) : Answer
}

/** Something the form needs from the database because of a change just made to it. */
sealed interface Lookup {

    /** Read [groupId]'s rate, mode and subject — answered with [BookingIntent.GroupLoaded]. */
    data class GroupOf(val groupId: Long) : Lookup

    /** Read what this student pays for this subject — answered with [BookingIntent.PriceLoaded]. */
    data class PriceOf(val studentId: Long, val subjectId: Long) : Lookup

    /**
     * Find out whether [askedFor] has exactly one bookable subject — answered with
     * [BookingIntent.OnlySubject], or with nothing at all when there is more than one.
     */
    data class OnlySubjectFor(val askedFor: SubjectKey) : Lookup
}

/** The form after one transition, and what the transition left to be looked up. */
data class Applied(val form: BookingForm, val lookups: List<Lookup> = emptyList())

/**
 * The booking form: what has been filled in, and whether the tutor filled it in.
 *
 * @param touched whether the form differs from what it was opened with because *the tutor* changed it.
 * Loading a lesson, pre-selecting the group the form was opened from, filling in the only student or
 * the only subject: none of those is the tutor's work, so none may make the form look unsaved.
 */
data class BookingForm(
    val form: ClassForm = ClassForm(),
    val touched: Boolean = false,
    /** Suggestions follow the subject; typed and already-booked amounts belong to this lesson. */
    val automaticPrice: Boolean = true,
) {
    /** The form after [intent], and anything it now needs looked up. */
    fun change(intent: BookingIntent, context: BookingContext): Applied {
        val moved = transform(intent, context)
        // The tutor's edit marks the form unsaved — but only when it moved something. An intent that
        // changes nothing (re-picking the target already chosen, the length it already has) leaves the
        // form exactly as it was, and back must not then ask about work nobody did.
        //
        // The old mutators disagreed with each other here: `setDuration` checked for a start *before*
        // marking, while `setTarget` marked and only then found it had nothing to do. `touched` never
        // goes back to false, so a form the tutor has edited stays edited even if a lookup answers after.
        val changed = moved.form.form != form
        val marked = if (intent.isEdit && changed) moved.form.copy(touched = true) else moved.form
        return moved.copy(form = marked)
    }

    private fun transform(intent: BookingIntent, context: BookingContext): Applied = when (intent) {
        is BookingIntent.ChooseTarget -> chooseTarget(intent.target, context)
        is BookingIntent.ChooseStudent -> chooseStudent(intent.studentId, intent.known, context)
        is BookingIntent.ChooseGroup -> chooseGroup(intent.groupId)
        is BookingIntent.ChooseSubject -> chooseSubject(intent.subjectId)
        is BookingIntent.IncludeMember -> includeMember(intent.studentId, intent.included)
        is BookingIntent.StartAt -> startAt(intent.time, context)
        is BookingIntent.LastsFor -> settled(form.copy(durationMinutes = intent.minutes))
        is BookingIntent.Edit -> {
            val edited = intent.transform(form)
            Applied(copy(form = edited, automaticPrice = automaticPrice && edited.price == form.price))
        }
        is BookingIntent.Answer -> answer(intent, context)
        is BookingIntent.AsSetup -> transform(intent.inner, context)
    }

    private fun answer(answer: BookingIntent.Answer, context: BookingContext): Applied = when (answer) {
        is BookingIntent.Load -> Applied(copy(form = answer.form, automaticPrice = false))
        is BookingIntent.DefaultLength -> defaultLength(answer.minutes)
        is BookingIntent.OnlyStudent -> onlyStudent(answer.student, context)
        is BookingIntent.GroupLoaded -> groupLoaded(answer.group)
        is BookingIntent.PriceLoaded -> priceLoaded(answer.studentId, answer.subjectId, answer.amount)
        is BookingIntent.OnlySubject -> onlySubject(answer.askedFor, answer.subjectId)
    }

    private fun settled(form: ClassForm) = Applied(copy(form = form))

    private fun chooseTarget(target: ClassTarget, context: BookingContext): Applied {
        if (form.target == target) return Applied(this)
        val cleared = form.copy(
            target = target,
            studentId = null,
            groupId = null,
            subjectId = null,
            price = MoneyField(kind = MoneyField.Kind.RATE),
        )
        // Auto-select the only group there is, mirroring the single-student case.
        val onlyGroup = context.groups.singleOrNull()
        if (target == ClassTarget.GROUP && onlyGroup != null) {
            return copy(form = cleared, automaticPrice = true).chooseGroup(onlyGroup.id)
        }
        // The subject was just cleared, so whatever the new target offers may be a single choice that
        // needs no picking.
        return Applied(
            copy(form = cleared, automaticPrice = true),
            listOf(Lookup.OnlySubjectFor(SubjectKey.of(cleared))),
        )
    }

    private fun chooseStudent(studentId: Long, known: Student?, context: BookingContext): Applied {
        if (form.studentId == studentId) return Applied(this)
        val student = known ?: context.students.firstOrNull { it.id == studentId }
        val next = form.copy(
            studentId = studentId,
            subjectId = null,
            price = MoneyField(kind = MoneyField.Kind.RATE),
            mode = student?.defaultMode ?: ClassMode.IN_PERSON,
        )
        // A different student prices different subjects; theirs may be a single one.
        return Applied(copy(form = next, automaticPrice = true), listOf(Lookup.OnlySubjectFor(SubjectKey.of(next))))
    }

    private fun chooseGroup(groupId: Long): Applied {
        if (form.groupId == groupId) return Applied(this)
        val next = form.copy(
            groupId = groupId,
            subjectId = null,
            price = MoneyField(kind = MoneyField.Kind.RATE),
            excludedMemberIds = emptySet(),
        )
        return Applied(copy(form = next, automaticPrice = true), listOf(Lookup.GroupOf(groupId)))
    }

    private fun groupLoaded(group: Group): Applied {
        if (form.groupId != group.id) return Applied(this)
        val next = form.copy(
            mode = group.defaultMode,
            // Never clobber an amount the tutor has already typed for this lesson.
            price = if (automaticPrice) form.price.prefilledWith(group.price) else form.price,
            // A group that has a subject decides it — there is nothing to pick per lesson.
            subjectId = group.subjectId ?: form.subjectId,
        )
        // A group with no subject of its own offers all of them, and one of those may be the only one.
        val lookups = if (group.subjectId == null) listOf(Lookup.OnlySubjectFor(SubjectKey.of(next)))
        else emptyList()
        return Applied(copy(form = next), lookups)
    }

    private fun chooseSubject(subjectId: Long): Applied {
        if (form.subjectId == subjectId) return Applied(this)
        val next = form.copy(
            subjectId = subjectId,
            price = if (form.target == ClassTarget.STUDENT && automaticPrice) MoneyField() else form.price,
        )
        val studentId = next.studentId
        val lookups = if (next.target == ClassTarget.STUDENT && studentId != null) {
            listOf(Lookup.PriceOf(studentId, subjectId))
        } else {
            emptyList()
        }
        return Applied(copy(form = next), lookups)
    }

    private fun defaultLength(minutes: Int): Applied =
        if (form.durationMinutes == null) settled(form.copy(durationMinutes = minutes)) else Applied(this)

    private fun onlyStudent(student: Student, context: BookingContext): Applied =
        if (form.target == ClassTarget.STUDENT && form.studentId == null) {
            chooseStudent(student.id, student, context)
        } else {
            Applied(this)
        }

    private fun priceLoaded(studentId: Long, subjectId: Long, amount: Money?): Applied {
        if (form.target != ClassTarget.STUDENT || form.studentId != studentId || form.subjectId != subjectId) {
            return Applied(this)
        }
        return if (automaticPrice) settled(form.copy(price = form.price.prefilledWith(amount))) else Applied(this)
    }

    private fun onlySubject(askedFor: SubjectKey, subjectId: Long): Applied {
        if (SubjectKey.of(form) != askedFor) return Applied(this)
        if (!form.hasTarget || form.subjectId != null) return Applied(this)
        return chooseSubject(subjectId)
    }

    private fun includeMember(studentId: Long, included: Boolean): Applied {
        val excluded = if (included) form.excludedMemberIds - studentId else form.excludedMemberIds + studentId
        return settled(form.copy(excludedMemberIds = excluded))
    }

    private fun startAt(time: LocalTime, context: BookingContext): Applied = settled(
        form.copy(
            timeFrom = time,
            durationMinutes = form.durationMinutes ?: context.defaultLessonMinutes,
        ),
    )
}

/** [this], applied as setup rather than as the tutor's edit — see [BookingIntent.AsSetup]. */
fun BookingIntent.asSetup(): BookingIntent = BookingIntent.AsSetup(this)
