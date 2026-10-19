package com.seblful.klas.data.repository

import com.seblful.klas.data.local.PaymentDao
import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.billing.PaymentDate
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.time.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/**
 * A payment, and **the instant one is recorded at**.
 *
 * That second half is the invariant, and until now it was assigned to this class in the docs and
 * implemented somewhere else. The tutor answers with a *day* — money is entered when they get round to
 * it, and «она заплатила во вторник» has to have a way in — while the database stores an instant, so
 * something has to pick the hour. [PaymentDate] is that rule; it was applied by the **view model**, twice,
 * on the way in. This class took a finished [Payment] and wrote it.
 *
 * Which made it a pass-through: five public methods, five one-line delegates, and a `PaymentDao` of
 * exactly the same shape behind it. Deleting it would have moved complexity rather than concentrating it,
 * which is the sign of a module not earning its keep. It earns it now by owning the one rule it was always
 * supposed to: **a payment's instant is derived here, from the day it was made, against the injected
 * clock** — so no caller can write one stamped with a time nobody chose.
 *
 * [restore] is the deliberate exception, and it is the reason this is three write methods rather than one:
 * putting back a payment that was just deleted must reproduce it exactly, hour included, and re-deriving
 * the instant there would silently move an undone payment to the moment it was undone.
 */
@SingleIn(AppScope::class)
class PaymentRepository @Inject constructor(
    private val dao: PaymentDao,
    private val clock: WallClock,
) {
    fun observeAll(): Flow<List<Payment>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** [observeAll], read once — for a caller with no screen to keep current, such as the debt digest. */
    suspend fun getAll(): List<Payment> = dao.getAll().map { it.toDomain() }

    fun observeForStudent(studentId: Long): Flow<List<Payment>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    /**
     * The day a fresh payment is offered on — today, by the same clock [record] will stamp it against.
     *
     * The two halves of one decision have to read one clock: when they disagree, a payment entered as
     * "today" fails the equality check in [PaymentDate.instantFor], lands at the start of the day, and
     * loses its place among the payments entered beside it.
     */
    fun today(): LocalDate = PaymentDate.today(clock)

    /**
     * Records [amount] from [studentId] as having been made on [day]. Returns the new row's id.
     *
     * The instant is [PaymentDate.instantFor]'s: today keeps the current time, so several payments entered
     * in one sitting stay in the order they were entered; any other day gets the start of it, since there
     * is no hour to know and inventing one would be a precision the tutor never supplied.
     */
    suspend fun record(studentId: Long, amount: Money, day: LocalDate): Long =
        dao.insert(
            Payment(
                studentId = studentId,
                date = PaymentDate.instantFor(day, clock),
                amount = amount,
            ).toEntity(),
        )

    /**
     * Corrects a payment already recorded — its amount, its date, or both — **keeping its id**.
     *
     * Fixing a mistyped amount used to mean deleting the payment and entering it again: two writes, a gap
     * in which the student's balance is wrong, and a new id for what is the same money.
     *
     * The instant is re-derived from [day] rather than carried over, exactly as recording one is. That is
     * visible in one case and it is intended: correcting an amount without touching the date still
     * re-stamps the hour, so two payments made on the same day can swap places.
     */
    suspend fun correct(payment: Payment, amount: Money, day: LocalDate) =
        dao.update(payment.copy(amount = amount, date = PaymentDate.instantFor(day, clock)).toEntity())

    /**
     * Puts [payment] back exactly as it was, id, instant and all — the undo behind a just-deleted row.
     *
     * **The one write here that does not derive an instant.** An undo restores a fact rather than
     * recording one, and re-deriving would move a payment made last Tuesday to the moment the tutor
     * tapped «Вернуть».
     *
     * It is also how a test seeds a payment at a chosen instant — for the same reason, and still through
     * the module that owns the table rather than around it.
     */
    suspend fun restore(payment: Payment): Long = dao.insert(payment.toEntity())

    suspend fun delete(payment: Payment) = dao.delete(payment.toEntity())
}
