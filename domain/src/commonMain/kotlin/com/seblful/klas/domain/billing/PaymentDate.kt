package com.seblful.klas.domain.billing

import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.domain.time.today
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

/**
 * When a payment entered for a given **day** is recorded as having happened.
 *
 * The tutor answers with a date, because that is what they know: money is entered when they get round
 * to it, and «она заплатила во вторник» has to have a way in. The database stores an instant, so
 * something has to pick the hour — and which hour it picks is visible, because the payment list is
 * ordered by instant.
 *
 * Uses [WallClock] so the recorded instant and the payment dialog share the same notion of today.
 */
object PaymentDate {

    /**
     * The instant to store a payment dated [day] at.
     *
     * **Today keeps the current time**, so several payments entered in one sitting stay in the order
     * they were entered — a row of identical midnights would shuffle. Any other day gets the start of
     * it: there is no hour to know, and inventing one would be a precision the tutor never supplied.
     */
    fun instantFor(day: LocalDate, clock: WallClock): Instant =
        if (day == today(clock)) clock.now() else day.atStartOfDayIn(clock.zone)

    /**
     * The default day for a new payment dialog, using the same clock [instantFor] uses to record it.
     */
    fun today(clock: WallClock): LocalDate = clock.today()
}
