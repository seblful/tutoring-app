package com.seblful.klas.ui.schedule.booking

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Recurrence
import com.seblful.klas.domain.time.MINUTES_PER_DAY

/**
 * What one press of a −/+ in the booking form's Length, Repeat and Price rows moves a value to. Null where
 * that way is already as far as it goes, so the button can say so by being disabled.
 */
object Steps {

    /** What one press of Length's −/+ changes a lesson by. */
    const val LENGTH_MINUTES = 15

    private const val SHORTEST = LENGTH_MINUTES

    /**
     * A minute short of a day: one occurrence of a weekly series then never reaches the next — `Recurrence`
     * relies on it, and `DurationPickerModal` bounds the same way.
     */
    private const val LONGEST = MINUTES_PER_DAY - 1

    private const val RATE_STEPS_PER_RATE = 5
    private const val MINOR_PER_MAJOR = 100L

    /** [minutes] a step longer, or shorter, kept between [SHORTEST] and [LONGEST]. */
    fun length(minutes: Int, longer: Boolean): Int? = when {
        longer && minutes < LONGEST -> minOf(minutes + LENGTH_MINUTES, LONGEST)
        !longer && minutes > SHORTEST -> maxOf(minutes - LENGTH_MINUTES, SHORTEST)
        else -> null
    }

    /** [count] lessons one more, or one fewer: one is a lesson that does not repeat. */
    fun lessons(count: Int, more: Boolean): Int? = when {
        more && count < Recurrence.MAX_COUNT -> count + 1
        !more && count > 1 -> count - 1
        else -> null
    }

    /** [rate] a [rateStep] higher, or lower — never below zero, never past [Money.MAX]. */
    fun rate(rate: Money, higher: Boolean): Money? {
        val step = rateStep(rate)
        return when {
            higher && rate < Money.MAX -> minOf(rate + step, Money.MAX)
            !higher && rate > Money.ZERO -> maxOf(rate - step, Money.ZERO)
            else -> null
        }
    }

    /**
     * The largest of 1, 5, 10, 50, 100… whole units within a fifth of [rate]: 5 at 40, 100 at 1 500 — a
     * step that suits a rate in any currency, which a fixed one could not.
     */
    fun rateStep(rate: Money): Money {
        val fifth = rate.minor / MINOR_PER_MAJOR / RATE_STEPS_PER_RATE
        val step = generateSequence(1L) { if (it.toString().startsWith('1')) it * 5 else it * 2 }
            .takeWhile { it <= maxOf(fifth, 1L) }
            .last()
        return Money.ofMajor(step)
    }
}
