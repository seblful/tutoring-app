package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.domain.lesson.GroupRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.domain.schedule.Scheduling
import com.seblful.klas.format.MoneyField
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Who a lesson is booked for: one student, or a whole group. */
enum class ClassTarget { STUDENT, GROUP }

/** Which subjects the form may offer: whose they are. */
data class SubjectKey(val target: ClassTarget, val studentId: Long?, val groupId: Long?) {
    companion object {
        fun of(form: ClassForm): SubjectKey = SubjectKey(form.target, form.studentId, form.groupId)
    }
}

/**
 * The class-booking form. A new booking uses the schedule's selected day when opened from there;
 * otherwise the date starts empty. The start is chosen on the day's timeline.
 */
@Serializable
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
     * Members not booked into this lesson. This changes its roster; attendance corrections preserve
     * booked members and are made in the lesson sheet.
     */
    val excludedMemberIds: Set<Long> = emptySet(),
    /** Whether this booking writes the one lesson or a run of them — see [Repeat]. */
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

    /** How many lessons saving this writes: one unless it repeats. What the Repeat row counts. */
    val lessons: Int
        get() = if (repeat.repeats) repeatCount else 1

    /**
     * This booking writing [count] lessons. One is a lesson that does not repeat; more keep the frequency
     * already chosen, weekly when there was none.
     */
    fun withLessons(count: Int): ClassForm =
        if (count <= 1) {
            copy(repeat = Repeat.NONE)
        } else {
            copy(repeat = repeat.takeIf { it.repeats } ?: Repeat.WEEKLY, repeatCount = count)
        }

    /**
     * Every instant this booking would write a lesson at, first one first. One element unless [repeat]
     * says otherwise; empty while the date or the time is still missing.
     */
    fun starts(zone: TimeZone): List<Instant> {
        val day = date ?: return emptyList()
        val at = timeFrom ?: return emptyList()
        return Recurrence.startsFor(day, at, repeat, repeatCount, zone)
    }

    /**
     * Where the lesson ends — for the form to show, not for it to keep. Null until there is a start and
     * a length to add to it.
     */
    fun ends(zone: TimeZone): Ending? {
        val from = timeFrom
        val minutes = durationMinutes?.takeIf { it > 0 }
        if (from == null || minutes == null) return null
        val end = slot(zone)?.end?.toLocalDateTime(zone)
        return if (end != null) {
            Ending(time = end.time, nextDay = date?.let { end.date > it } ?: false)
        } else {
            // Before a date is selected there is no DST transition to resolve yet.
            Ending(
                time = LocalTime.fromSecondOfDay(
                    (from.toSecondOfDay() + minutes * SECONDS_PER_MINUTE).mod(SECONDS_PER_DAY),
                ),
                nextDay = from.toSecondOfDay() + minutes * SECONDS_PER_MINUTE >= SECONDS_PER_DAY,
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
        get() = assignee != null

    /** Who the lesson is for, once chosen — whose other lessons the day's timeline picks out. */
    val assignee: Assignee?
        get() = when (target) {
            ClassTarget.STUDENT -> studentId?.let(Assignee::Student)
            ClassTarget.GROUP -> groupId?.let(Assignee::Group)
        }

    /**
     * The stretch of time this form describes, or null while the date and times are incomplete. The
     * one place the form's wall-clock values become an instant, so the conflict check and the save
     * can never disagree about when the lesson is.
     */
    fun slot(zone: TimeZone): Scheduling.Slot? {
        val day = date ?: return null
        val from = timeFrom ?: return null
        val minutes = durationMinutes?.takeIf { it > 0 } ?: return null
        return Scheduling.Slot(day.atTime(from).toInstant(zone), minutes)
    }
}

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
        fun of(form: ClassForm, zone: TimeZone): ValidForm? = ValidForm(
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

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_DAY = 24 * 60 * SECONDS_PER_MINUTE
