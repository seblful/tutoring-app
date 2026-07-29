package com.seblful.klas.ui.schedule

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.format.MoneyField
import java.time.LocalTime

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

/**
 * Every way the booking form can change — the tutor's edits and the answers the app went to look up.
 *
 * The distinction is [isEdit], and it is the reason this is one type. There used to be eleven mutators,
 * four of which were `selectX` one-liners over a private `applyX` differing *only* in setting `touched`:
 * the tutor picking a group has to mark the form unsaved, the form pre-selecting the group it was opened
 * from must not. That is a property of the change, and it is stated here once instead of being encoded
 * in which of two functions the caller reached for.
 */
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
     *
     * A wrapper rather than a second `apply` overload, so "this is setup, not an edit" is said in the
     * same place for every intent instead of being a flag threaded through the call. It is the whole of
     * what the four `selectX`/`applyX` pairs used to encode by existing twice.
     */
    data class AsSetup(val inner: BookingIntent) : BookingIntent {
        override val isEdit: Boolean get() = false
    }

    // ---- Answers, not edits --------------------------------------------------------------------
    // Each carries what it was asked *about*, so it can tell whether the form has moved on since —
    // see the staleness checks in [BookingForm.apply]. These used to be `if` guards inside a
    // `viewModelScope.launch`, where nothing could reach them.

    /** The lesson being edited, loaded from the database. Not the tutor's work. */
    data class Load(val form: ClassForm) : BookingIntent {
        override val isEdit: Boolean get() = false
    }

    /** The group the form was pointed at, with its rate, mode and subject. */
    data class GroupLoaded(val group: Group) : BookingIntent {
        override val isEdit: Boolean get() = false
    }

    /** The student's price for [subjectId], or null where they have none. */
    data class PriceLoaded(val subjectId: Long, val amount: Money?) : BookingIntent {
        override val isEdit: Boolean get() = false
    }

    /**
     * The single subject the current target can be booked for — there was nothing to pick.
     *
     * @param askedFor the target this was looked up for. A fast second choice must not have the first
     *   one's subject land on it.
     */
    data class OnlySubject(val askedFor: SubjectKey, val subjectId: Long) : BookingIntent {
        override val isEdit: Boolean get() = false
    }
}

/**
 * Something the form needs from the database because of a change just made to it.
 *
 * Returned as **data** rather than launched, so the cascade is a value a test can read: "choosing this
 * group means going to look up its rate, and then whether it has a single subject". The view model runs
 * them and feeds each answer back as the matching [BookingIntent]. This is the same shape
 * `ClassRepository.write` uses for its post-commit work, and for the same reason — the module that knows
 * what is owed should not also have to be the one that can run it.
 */
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
 * **Pure.** Every rule about how the form moves lives here — the target/student/group/subject cascade
 * that clears what belonged to the previous choice, the default length a start brings with it, the
 * roster ticks, and the three staleness checks that keep a slow answer from landing on a form that has
 * moved on. None of it needs a dispatcher, a `SavedStateHandle` or six repositories to exercise.
 *
 * It was spread across eleven mutators on a thousand-line view model, half of them inside
 * `viewModelScope.launch` blocks. The rules were the same rules; they were simply somewhere a test could
 * only reach them through the whole machine.
 *
 * @param touched whether the form differs from what it was opened with because *the tutor* changed it.
 *   Loading a lesson, pre-selecting the group the form was opened from, filling in the only student or
 *   the only subject: none of those is the tutor's work, so none may make the form look unsaved.
 */
data class BookingForm(
    val form: ClassForm = ClassForm(),
    val touched: Boolean = false,
) {
    /**
     * The form after [intent], and anything it now needs looked up.
     *
     * Named `change` rather than `apply`: every type already has `apply` from the standard library, and a
     * caller writing `form.apply { … }` would silently get the scope function instead — the same trap
     * `LessonFilter.accepted` was renamed away from.
     */
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
        is BookingIntent.Edit -> settled(intent.transform(form))
        is BookingIntent.Load -> settled(intent.form)
        is BookingIntent.GroupLoaded -> groupLoaded(intent.group)
        is BookingIntent.PriceLoaded -> priceLoaded(intent.subjectId, intent.amount)
        is BookingIntent.OnlySubject -> onlySubject(intent.askedFor, intent.subjectId)
        is BookingIntent.AsSetup -> transform(intent.inner, context)
    }

    private fun settled(form: ClassForm) = Applied(copy(form = form))

    /**
     * Switching between a student and a group clears the subject and the price, because both belonged to
     * the other target — a group's rate and a student's price for a subject have nothing to do with each
     * other.
     */
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
            return copy(form = cleared).chooseGroup(onlyGroup.id)
        }
        // The subject was just cleared, so whatever the new target offers may be a single choice that
        // needs no picking.
        return Applied(copy(form = cleared), listOf(Lookup.OnlySubjectFor(SubjectKey.of(cleared))))
    }

    /**
     * Choosing a different student clears the subject and price (they belonged to the previous student)
     * and pre-fills the class mode from the new student's default.
     */
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
        return Applied(copy(form = next), listOf(Lookup.OnlySubjectFor(SubjectKey.of(next))))
    }

    /**
     * Choosing a different group clears the subject and the roster ticks — a different group is a
     * different set of people, so the last one's exclusions mean nothing — and then goes to read the
     * group, because its rate, mode and subject all come from the row.
     */
    private fun chooseGroup(groupId: Long): Applied {
        if (form.groupId == groupId) return Applied(this)
        val next = form.copy(
            groupId = groupId,
            subjectId = null,
            price = MoneyField(kind = MoneyField.Kind.RATE),
            excludedMemberIds = emptySet(),
        )
        return Applied(copy(form = next), listOf(Lookup.GroupOf(groupId)))
    }

    /**
     * The group's own rate, mode and subject.
     *
     * Ignored when the form has moved to another group since it was asked for — the read is a database
     * round trip, and the tutor can pick twice inside one.
     */
    private fun groupLoaded(group: Group): Applied {
        if (form.groupId != group.id) return Applied(this)
        val next = form.copy(
            mode = group.defaultMode,
            // Never clobber an amount the tutor has already typed for this lesson.
            price = form.price.prefilledWith(group.price),
            // A group that has a subject decides it — there is nothing to pick per lesson.
            subjectId = group.subjectId ?: form.subjectId,
        )
        // A group with no subject of its own offers all of them, and one of those may be the only one.
        val lookups = if (group.subjectId == null) listOf(Lookup.OnlySubjectFor(SubjectKey.of(next)))
        else emptyList()
        return Applied(copy(form = next), lookups)
    }

    /**
     * Selecting a subject pre-fills the rate from the student's price for it. A group lesson's rate is
     * the group's and does not depend on the subject, so there is nothing to pre-fill there.
     */
    private fun chooseSubject(subjectId: Long): Applied {
        val next = form.copy(subjectId = subjectId)
        val studentId = next.studentId
        val lookups = if (next.target == ClassTarget.STUDENT && studentId != null) {
            listOf(Lookup.PriceOf(studentId, subjectId))
        } else {
            emptyList()
        }
        return Applied(copy(form = next), lookups)
    }

    /** Only auto-fills an empty field, and only while the subject it was asked about is still chosen. */
    private fun priceLoaded(subjectId: Long, amount: Money?): Applied {
        if (form.subjectId != subjectId) return Applied(this)
        return settled(form.copy(price = form.price.prefilledWith(amount)))
    }

    /**
     * The single subject the target offered.
     *
     * Applies only while the form still has that target and still has no subject — a target chosen in
     * quick succession must not receive the previous one's answer.
     */
    private fun onlySubject(askedFor: SubjectKey, subjectId: Long): Applied {
        if (SubjectKey.of(form) != askedFor) return Applied(this)
        if (!form.hasTarget || form.subjectId != null) return Applied(this)
        return chooseSubject(subjectId)
    }

    /**
     * Includes or excludes one member for the lesson. Excluding writes no class for them at all, which
     * is different from booking and then cancelling: there is no cancelled lesson in their history,
     * because the lesson was never theirs.
     */
    private fun includeMember(studentId: Long, included: Boolean): Applied = settled(
        form.copy(
            excludedMemberIds =
                if (included) form.excludedMemberIds - studentId else form.excludedMemberIds + studentId,
        ),
    )

    /**
     * Picking or changing the start keeps whatever length the lesson has, and gives a lesson that has
     * none yet the tutor's default. Moving the start no longer has to move anything else — the end is
     * derived from the length ([ClassForm.ends]) — so this cannot leave the two disagreeing, which is
     * what it existed to prevent.
     */
    private fun startAt(time: LocalTime, context: BookingContext): Applied = settled(
        form.copy(
            timeFrom = time,
            durationMinutes = form.durationMinutes ?: context.defaultLessonMinutes,
        ),
    )
}

/** [this], applied as setup rather than as the tutor's edit — see [BookingIntent.AsSetup]. */
fun BookingIntent.asSetup(): BookingIntent = BookingIntent.AsSetup(this)
