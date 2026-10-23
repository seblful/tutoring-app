package com.seblful.klas.data.billing

import com.seblful.klas.data.toDomain
import com.seblful.klas.data.toEntity
import com.seblful.klas.domain.billing.PaymentDate
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.time.WallClock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** A payment, and **the instant one is recorded at**. */
@SingleIn(AppScope::class)
class PaymentRepository @Inject constructor(
    private val dao: PaymentDao,
    private val clock: WallClock,
) {
    /** Every payment, read once for balance reconciliation. */
    suspend fun getAll(): List<Payment> = dao.getAll().map { it.toDomain() }

    fun observeForStudent(studentId: Long): Flow<List<Payment>> =
        dao.observeForStudent(studentId).map { list -> list.map { it.toDomain() } }

    /** The day a fresh payment is offered on — today, by the same clock [record] will stamp it against. */
    fun today(): LocalDate = PaymentDate.today(clock)

    /** Records [amount] from [studentId] as made on [day], by [method]. Returns the new row's id. */
    suspend fun record(studentId: Long, amount: Money, day: LocalDate, method: PaymentMethod? = null): Long =
        dao.insert(
            Payment(
                studentId = studentId,
                date = PaymentDate.instantFor(day, clock),
                amount = amount,
                method = method,
            ).toEntity(),
        )

    /**
     * Corrects a payment already recorded — its amount, its date, or both — **keeping its id**. Returns
     * the payment as corrected, which [undoCorrection] checks is still what is stored.
     */
    suspend fun correct(
        payment: Payment,
        amount: Money,
        day: LocalDate,
        method: PaymentMethod? = payment.method,
    ): Payment {
        val corrected = payment.copy(amount = amount, date = PaymentDate.instantFor(day, clock), method = method)
        dao.update(corrected.toEntity())
        return corrected
    }

    /**
     * Takes [correct] back: [corrected] becomes [original] again, but only while nothing has changed or
     * deleted it since. True when it did.
     */
    suspend fun undoCorrection(original: Payment, corrected: Payment): Boolean {
        val from = corrected.toEntity()
        val to = original.toEntity()
        return dao.updateIfUnchanged(
            to.id, from.dateMillis, from.amountMinor, from.method, to.dateMillis, to.amountMinor, to.method,
        ) > 0
    }

    /** Puts [payment] back exactly as it was, id, instant and all — the undo behind a just-deleted row. */
    suspend fun restore(payment: Payment): Long = dao.insert(payment.toEntity())

    suspend fun delete(payment: Payment) = dao.delete(payment.toEntity())
}
