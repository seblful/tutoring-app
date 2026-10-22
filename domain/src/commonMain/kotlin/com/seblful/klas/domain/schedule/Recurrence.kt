package com.seblful.klas.domain.schedule

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * How often a booking repeats — the whole vocabulary of a lesson series.
 *
 * Three values and no rule object, because **a series is not a thing this app stores**. Booking a term
 * of lessons writes the lessons: twelve ordinary rows, each independently movable, cancellable and
 * deletable, exactly as if they had been booked one at a time. Nothing afterwards remembers they were
 * arranged together.
 *
 * That is a deliberate reading of «recurring lessons», and the alternative is worth naming because it is
 * the obvious one: a `series` table holding a rule, expanded on read. Every read path in the app would
 * then have to learn to expand it — [Scheduling.conflicts], [CalendarLayout.dayLoads], `Reports`,
 * `Digests`, `Reminders`, the calendar mirror and the backup format — and the exceptions a real schedule
 * is made of ("skip the third", "the fifth moved to Thursday") would need a table of their own. All of
 * that fights what the app already is: Room is the single source of truth, and **a lesson is the unit of
 * change**. Materialising keeps both true, and it keeps `docs/domain-rules.md`'s "no series entity"
 * honest at the data layer — what exists here is a booking *act*, not an entity.
 *
 * The price of it is that there is no "cancel the rest of the series" in one tap. The case that would
 * actually be reached for is already covered: archiving a student calls off every lesson still planned
 * for them (`ClassRepository.cancelScheduledFor`).
 *
 * @param weeks how far apart two consecutive lessons are. [NONE] is zero because it produces one date
 *   and never adds anything to it.
 */
enum class Repeat(val weeks: Int) {

    /** Book the one lesson, as the form has always done. */
    NONE(0),

    WEEKLY(1),

    FORTNIGHTLY(2),
    ;

    /** Whether this writes more than the single lesson the form describes. */
    val repeats: Boolean get() = this != NONE
}

/**
 * The dates and instants a repeating booking covers.
 *
 * **Every occurrence is the same weekday at the same wall-clock time**, anchored on the lesson the tutor
 * filled in. A tutor teaching Monday 16:00 and Thursday 18:00 books two series, which is the deliberate
 * simplification: multi-weekday recurrence with a time per day is what turns a booking form into a
 * calendar app, and since the occurrences are plain lessons afterwards there is no series integrity to
 * preserve by expressing it in one go.
 *
 * It also buys a real guarantee. Occurrences are at least a week apart and a lesson is at most 23 h 59 m
 * long (`DurationPickerModal` bounds both its fields), so **no two occurrences of one series can overlap
 * each other**. That is why `ClassRepository.bookSeries` checks the schedule as it stands and never has
 * to fold rows it is itself about to write back into the check.
 */
object Recurrence {

    /** How many lessons «Повторять» starts at — a month of weekly ones. */
    const val DEFAULT_COUNT = 4

    /** The counts offered as chips: a month, two months, a term. The booking form's stepper reaches the rest. */
    val COMMON_COUNTS: List<Int> = listOf(4, 8, 12)

    /**
     * The most lessons one booking may write — a year of weekly ones.
     *
     * A ceiling rather than a judgement about how far ahead a tutor may plan: this is the number of rows
     * a single «Сохранить» can put in the database, each with a calendar event and a place in the
     * reminder plan, and a mistyped count in a free-text field is the only way to reach it.
     */
    const val MAX_COUNT = 52

    /**
     * The dates [count] lessons fall on, starting at [first] — which is always the first of them, so a
     * booking that does not repeat is the one-element list containing the day the tutor picked.
     *
     * [count] is clamped rather than refused: it arrives from a chip or from a bounded number field, so
     * there is no out-of-range value a screen can actually submit, and the one thing this must not do is
     * return an empty list — that would silently book nothing.
     */
    fun dates(first: LocalDate, repeat: Repeat, count: Int): List<LocalDate> {
        if (!repeat.repeats) return listOf(first)
        val total = count.coerceIn(1, MAX_COUNT)
        return (0 until total).map { first.plus(it.toLong() * repeat.weeks, DateTimeUnit.WEEK) }
    }

    /**
     * The same occurrences as instants: each date at [at], resolved in [zone].
     *
     * **The wall clock is the promise, not the elapsed time.** A lesson at 16:00 stays at 16:00 across a
     * daylight-saving change, which adding seven times twenty-four hours to an `Instant` would not give —
     * it would slide the autumn half of a term to 15:00. The same argument `Digests` makes for firing a
     * morning agenda at nine.
     */
    fun startsFor(
        first: LocalDate,
        at: LocalTime,
        repeat: Repeat,
        count: Int,
        zone: TimeZone,
    ): List<Instant> = dates(first, repeat, count).map { it.atTime(at).toInstant(zone) }

    /** The last day the booking reaches — what the form prints under «Повторять» as «по 24 сентября». */
    fun lastDate(first: LocalDate, repeat: Repeat, count: Int): LocalDate =
        dates(first, repeat, count).last()
}
