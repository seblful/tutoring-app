package com.seblful.klas.domain.model

/**
 * Where a student stands: settled, owing, or in credit — the **case**, not the sign it was picked from.
 *
 * A balance is one [Money] whose sign carries a meaning ([Billing]: charges minus payments, so positive is
 * debt and negative is over-payment). Every surface that shows one has to ask the same three-way question,
 * and asking it per surface out of `isZero` and `isNegative` — in a `@Composable`, the one place this app
 * cannot unit-test — lets the answers drift (a two-way ask files a settled balance under «Долг»). It is
 * decided once, here.
 *
 * The sign flip that turns a negative balance into a positive «Переплата» happens once, in [of] — so an
 * over-payment cannot be printed as a negative debt by a caller that forgot, and **the amount every arm
 * carries is already the amount to print.**
 *
 * The shape is `DeleteWarning`'s and `ArchiveIntent`'s: a sealed answer naming which case it is, leaving the
 * screen holding only words and a colour. The `when` over it is exhaustive, so a fourth case — if a balance
 * ever grows one — is a compile error at every reader rather than a silent fall-through to «Долг».
 */
sealed interface Balance {

    /**
     * The amount to display: zero for [Settled], positive for balances returned by [of].
     *
     * The interface includes zero so a settled balance can use the same display as credit and debt.
     */
    val amount: Money

    /** Nothing is owed either way. */
    data object Settled : Balance {
        override val amount: Money get() = Money.ZERO
    }

    /**
     * The student has paid ahead: the tutor owes them [amount] of teaching.
     *
     * [amount] is **positive** — the flip from the stored negative happens in [of] and nowhere else.
     */
    data class Credit(override val amount: Money) : Balance

    /** The student owes the tutor [amount]. Positive, like [Credit]'s. */
    data class Owed(override val amount: Money) : Balance

    companion object {
        /**
         * Which case [balance] is, with the amount already made positive.
         *
         * @param balance charges minus payments, as `Billing` computes it: positive is debt.
         */
        fun of(balance: Money): Balance = when {
            balance.isZero -> Settled
            balance.isNegative -> Credit(Money.ZERO - balance)
            else -> Owed(balance)
        }
    }
}
