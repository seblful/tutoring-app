package com.seblful.klas.data.billing

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.StudentBalance
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Student balances over their whole history, read as sums — see [BalanceDao]. */
@SingleIn(AppScope::class)
class BalanceRepository @Inject constructor(private val dao: BalanceDao) {

    /** Keyed by student. A student with no class or payment yet has no entry: see [balanceOf]. */
    fun observe(): Flow<Map<Long, StudentBalance>> = dao.observe().map { it.toBalances() }

    suspend fun get(): Map<Long, StudentBalance> = dao.get().toBalances()

    private fun List<BalanceRow>.toBalances(): Map<Long, StudentBalance> =
        associate { it.studentId to StudentBalance(it.studentId, Money(it.chargedMinor), Money(it.paidMinor)) }
}

/** [studentId]'s balance — zero for a student with no class or payment yet. */
fun Map<Long, StudentBalance>.balanceOf(studentId: Long): StudentBalance =
    this[studentId] ?: StudentBalance(studentId, Money.ZERO, Money.ZERO)
