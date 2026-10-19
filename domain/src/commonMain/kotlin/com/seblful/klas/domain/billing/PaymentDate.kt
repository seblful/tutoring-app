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
 * This lived in `StudentDetailScreen` as a `LocalDate.asPaymentInstant()` extension, which is the one
 * place in the app that could not be given the injected [WallClock]: it called `LocalDate.now()` and
 * `Instant.now()` directly. So the app's one rule about what time "now" is had an exception exactly
 * where money is recorded, and no test could move the clock past it.
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
     * The day a fresh payment dialog opens on — today, by the **same clock** [instantFor] will
     * compare the answer against. The dialog used to ask the platform directly (`LocalDate.now()` in
     * a composable), so the two halves of one decision read two clocks: whenever they disagree, a
     * payment entered as "today" lands at `atStartOfDay` and loses its entry order — exactly the
     * shuffle [instantFor] exists to prevent — and no test could reach the composable's default to
     * say so.
     */
    fun today(clock: WallClock): LocalDate = clock.today()
}
