package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.domain.lesson.GroupRules
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.format.MoneyField
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Who a lesson is booked for: one student, or a whole group. */
enum class ClassTarget { STUDENT, GROUP }

/**
 * Which subjects the form may offer: whose they are.
 *
 * A value rather than three parameters because it is also what an in-flight lookup is *about* — asking
 * "does this target have exactly one subject" and then applying the answer are separated by a database
 * round trip, and the tutor can pick twice inside one. Comparing the key is how a stale answer is
 * recognised (see `BookingForm`).
 */
data class SubjectKey(val target: ClassTarget, val studentId: Long?, val groupId: Long?) {
    companion object {
        fun of(form: ClassForm): SubjectKey = SubjectKey(form.target, form.studentId, form.groupId)
    }
}

/**
 * The class-booking form. A new booking uses the schedule's selected day when opened from there;
 * otherwise the date starts empty. The start time is chosen by the user (the picker opens on the
 * next whole hour, see [ClassEditViewModel.startPickerDefault]).
 *
 * **A lesson is a start and a length**, which is what the database row is ([TutoringClass.start] and
 * [TutoringClass.durationMinutes]) and what [Scheduling.Slot] is. The end is derived for display
 * ([ends]) and stored nowhere, so it cannot go stale when the start moves, and a lesson running past
 * midnight is an ordinary lesson rather than a special case.
 *
 * This form used to hold the end as a second wall clock (`timeTo`) on one date, and paid for it: a
 * 23:00 start plus the default hour wrapped to 00:00, which read as *minus* twenty-three hours, so the
 * form refused a booking it had made itself and offered no way out but a shorter lesson. Worse, it
 * refused ones already in the database — «Перенести» moves a lesson to any date and time it likes, and
 * opening one it had moved to 23:00 showed a red error over a perfectly good lesson. There is no
 * negative length to detect here now; `Scheduling.Slot` keeps the `> 0` backstop.
 *
 * The form covers both kinds of booking, and differs between them only in *who* the lesson is for:
 * one student, or a whole group. Everything else — subject, date, time, mode and the single
 * [priceText] — belongs to the lesson. That one rate is pre-filled from the student's price for the
 * subject, or from the **group's own rate**, and every class the lesson writes is booked at it.
 */
data class ClassForm(
    val target: ClassTarget = ClassTarget.STUDENT,
    val studentId: Long? = null,
    val groupId: Long? = null,
    val subjectId: Long? = null,
    val date: LocalDate? = null,
    val timeFrom: LocalTime? = null,
    /** How long the lesson runs. Null until the tutor has said — see [ends] for where it ends up. */
    val durationMinutes: Int? = null,
    val price: MoneyField = MoneyField(kind = MoneyField.Kind.RATE),
    val mode: ClassMode = ClassMode.IN_PERSON,
    /**
     * Members left out of **this one** group lesson. Someone away this week is not off the roster, so
     * the exclusion lives on the form and not on the group: it decides which classes the lesson has
     * and is forgotten afterwards.
     *
     * It means the same thing whether the lesson is being booked or edited, and for a lesson in any
     * status: who was in a lesson is a fact, and a fact can be corrected once the lesson is over.
     * Unticking a member of a saved lesson therefore removes their class ([GroupRules.rosterChange]),
     * which for a completed one takes what they were charged with it.
     */
    val excludedMemberIds: Set<Long> = emptySet(),
    /**
     * Whether this booking writes the one lesson or a run of them — see [Repeat].
     *
     * On the form for a **new** lesson only. Repeating is a property of the act of booking rather than of
     * a lesson, and there is nothing on a lesson already in the database for it to describe: a lesson
     * loaded by [ClassEditViewModel.loadExisting] therefore keeps the default, and the edit path never
     * reads [starts].
     */
    val repeat: Repeat = Repeat.NONE,
    /** How many lessons a repeating booking writes, this one included. Meaningless while [repeat] is NONE. */
    val repeatCount: Int = Recurrence.DEFAULT_COUNT,
) {
    /**
     * The last day this booking reaches — «по 24 сентября», shown beside «Повторять» the way the lesson's
     * end is shown beside «Длительность»: a consequence of the choice, worth confirming and not worth a
     * field. Null while the booking does not repeat or has no date yet.
     */
    val repeatsUntil: LocalDate?
        get() = date?.takeIf { repeat.repeats }?.let { Recurrence.lastDate(it, repeat, repeatCount) }

    /**
     * Every instant this booking would write a lesson at, first one first. One element unless [repeat]
     * says otherwise; empty while the date or the time is still missing.
     *
     * The one place the form's wall-clock values become the *series* of instants, as [slot] is the one
     * place they become the single one — so what the tutor is shown («по 24 сентября») and what the
     * repository is handed are read off the same rule.
     */
    fun starts(zone: ZoneId): List<Instant> {
        val day = date ?: return emptyList()
        val at = timeFrom ?: return emptyList()
        return Recurrence.startsFor(day, at, repeat, repeatCount, zone)
    }

    /**
     * Where the lesson ends — for the form to show, not for it to keep. Null until there is a start and
     * a length to add to it.
     *
     * Carries [Ending.nextDay] rather than only a clock time because "до 00:00" on its own is the one
     * end a tutor could misread as the start of the day they are booking.
     */
    fun ends(zone: ZoneId): Ending? {
        val from = timeFrom
        val minutes = durationMinutes?.takeIf { it > 0 }
        if (from == null || minutes == null) return null
        val end = slot(zone)?.end?.atZone(zone)
        return if (end != null) {
            Ending(time = end.toLocalTime(), nextDay = end.toLocalDate().isAfter(date))
        } else {
            // Before a date is selected there is no DST transition to resolve yet.
            Ending(
                time = from.plusMinutes(minutes.toLong()),
                nextDay = from.toSecondOfDay() + minutes * 60L >= Duration.ofDays(1).seconds,
            )
        }
    }

    /** A lesson's end as a wall clock, and whether reaching it means passing midnight. */
    data class Ending(val time: LocalTime, val nextDay: Boolean)

    private val timingValid: Boolean
        get() = date != null && timeFrom != null && (durationMinutes ?: 0) > 0

    val isValid: Boolean
        get() = subjectId != null && timingValid && price.isValid && hasTarget

    /** Whether the lesson yet has somebody to be for — a student chosen, or a group. */
    val hasTarget: Boolean
        get() = when (target) {
            ClassTarget.STUDENT -> studentId != null
            ClassTarget.GROUP -> groupId != null
        }

    /**
     * The stretch of time this form describes, or null while the date and times are incomplete. The
     * one place the form's wall-clock values become an instant, so the conflict check and the save
     * can never disagree about when the lesson is.
     *
     * A length of zero or less is treated as unset rather than passed on: [Scheduling.Slot] would throw,
     * and "not filled in yet" is what it means from a form.
     */
    fun slot(zone: ZoneId): Scheduling.Slot? {
        val day = date ?: return null
        val from = timeFrom ?: return null
        val minutes = durationMinutes?.takeIf { it > 0 } ?: return null
        return Scheduling.Slot(day.atTime(from).atZone(zone).toInstant(), minutes)
    }
}

/**
 * A [ClassForm] that has passed validation, carrying everything a save needs **non-null**.
 *
 * The save used to re-assert the form's own validity field by field (`f.subjectId!!`,
 * `f.price.amount!!`, `f.studentId!!`) several hundred lines away from the [ClassForm.isValid] that
 * had actually proved it. The proof is worth keeping rather than restating: [of] is the one place
 * the checks happen, and what it returns cannot be a form that is missing a subject or a price.
 *
 * Being a snapshot matters as much as being non-null — the write runs outside `viewModelScope`
 * (see [ClassEditViewModel.save]), so it must not read a form the screen may still be editing.
 */
internal data class ValidForm(
    val target: Target,
    val subjectId: Long,
    val slot: Scheduling.Slot,
    val price: Money,
    val mode: ClassMode,
    val excludedMemberIds: Set<Long>,
    /**
     * Every start this booking writes a lesson at — [slot]'s first, then the rest of the series. A single
     * booking is the one-element list, so the save has one shape rather than a branch.
     *
     * Only a **new** booking reads it: editing a lesson moves the one lesson, and [ClassForm.repeat] is
     * NONE for anything loaded from the database.
     */
    val starts: List<Instant>,
) {
    /** Who the lesson is for — the one thing the two kinds of booking genuinely disagree about. */
    sealed interface Target {
        data class Individual(val studentId: Long) : Target
        data class Group(val groupId: Long) : Target
    }

    /** Null unless the lesson is a group's — the shape [ClassRepository] takes for `groupId`. */
    val groupId: Long? get() = (target as? Target.Group)?.groupId

    /** Null for a group lesson, whose members are reconciled by a roster instead. */
    val studentId: Long? get() = (target as? Target.Individual)?.studentId

    companion object {
        /**
         * The valid form [form] describes, or null when it is still incomplete. Mirrors
         * [ClassForm.isValid] — every check there is a `?: return null` here — so a form the screen
         * lets the tutor save is a form this accepts.
         */
        fun of(form: ClassForm, zone: ZoneId): ValidForm? = ValidForm(
            target = when (form.target) {
                ClassTarget.STUDENT -> Target.Individual(form.studentId ?: return null)
                ClassTarget.GROUP -> Target.Group(form.groupId ?: return null)
            },
            subjectId = form.subjectId ?: return null,
            slot = form.slot(zone) ?: return null,
            price = form.price.amount ?: return null,
            mode = form.mode,
            excludedMemberIds = form.excludedMemberIds,
            // Non-empty exactly when `slot` is: both are null-guarded on the same date and time, so a form
            // that produced a slot produced a series with that slot's start at the head of it.
            starts = form.starts(zone),
        )
    }
}
