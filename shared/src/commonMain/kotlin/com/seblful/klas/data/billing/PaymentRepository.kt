package com.seblful.klas.data.billing

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

/**  payment, and **the instant one is recorded at**. */
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

    /** The day a fresh payment is offered on — today, by the same clock [record] will stamp it against. */
    fun today(): LocalDate = PaymentDate.today(clock)

    /** ecords [amount] from [studentId] as having been made on [day]. Returns the new row's id. */
    suspend fun record(studentId: Long, amount: Money, day: LocalDate): Long =
        dao.insert(
            Payment(
                studentId = studentId,
                date = PaymentDate.instantFor(day, clock),
                amount = amount,
            ).toEntity(),
        )

    /** orrects a payment already recorded — its amount, its date, or both — **keeping its id**. */
    suspend fun correct(payment: Payment, amount: Money, day: LocalDate) =
        dao.update(payment.copy(amount = amount, date = PaymentDate.instantFor(day, clock)).toEntity())

    /** uts [payment] back exactly as it was, id, instant and all — the undo behind a just-deleted row. */
    suspend fun restore(payment: Payment): Long = dao.insert(payment.toEntity())

    suspend fun delete(payment: Payment) = dao.delete(payment.toEntity())
}
