package com.seblful.klas.format

import com.seblful.klas.domain.model.Money

/**
 * What a money field in this app is *for*, named once per meaning. Every amount the tutor types is
 * either a **rate** or a **payment**, and the two differ on exactly one point — whether zero is
 * allowed — so each gets a name instead of the difference living as a subtly different condition at
 * each text field.
 *
 * Both return null for input that isn't a valid amount of that kind, so a caller reads as
 * `MoneyInput.rate(text) ?: return` or `isError = MoneyInput.rate(text) == null`.
 *
 * Lives beside [parseMoneyOrNull] rather than in `domain/` because turning user text into [Money] is
 * this module's job and `format` already depends on `domain` — putting it the other way would invert
 * that dependency.
 */
object MoneyInput {

    /**
     * An hourly rate for a subject: any parseable amount, **zero included**. A tutor may teach
     * somebody for free and still needs that subject bookable and billed — at 0.
     */
    fun rate(text: String): Money? = parseMoneyOrNull(text)

    /**
     * A payment from a student: a parseable amount that is **not zero**. A zero payment would add a
     * row to the student's history that moves no money, so it reads as a slip rather than a record.
     */
    fun payment(text: String): Money? = parseMoneyOrNull(text)?.takeUnless { it.isZero }
}
