package com.seblful.repetit.format

import com.seblful.repetit.domain.model.Money

/**
 * A money text field: what the tutor has typed, and every answer a form or a screen needs about it.
 *
 * Every money input in the app used to restate the same three rules at its own call site — is this
 * parseable ([amount]), should the box go red ([isError]), may I fill it in for them
 * ([prefilledWith]) — in six different spellings, three of them inside composables where no JVM
 * test could reach them. They differ in exactly one respect, which is [kind].
 *
 * Holding the raw [text] rather than a parsed amount is deliberate: half-typed input like `"1,"`
 * is not an amount but must still survive a recomposition, and re-formatting what someone is in the
 * middle of typing moves their cursor.
 */
data class MoneyField(
    val text: String = "",
    val kind: Kind = Kind.RATE,
) {
    /** What this field is *for* — the one thing money inputs actually disagree about. */
    enum class Kind {
        /**
         * An hourly rate: any parseable amount, **zero included** (a student taught for free still
         * needs the subject bookable and billed, at 0). Blank is not an amount.
         */
        RATE,

        /**
         * A payment from a student: a parseable amount that is **not zero**. A zero payment would
         * add a row to their history that moves no money, so it reads as a slip.
         */
        PAYMENT,

        /**
         * A rate that may be left unset, where **blank means 0 — "not set"**. Used by the default
         * group rate in settings: clearing the box is how the tutor withdraws it, rather than being
         * forced to invent an amount.
         */
        OPTIONAL_RATE,
    }

    /** The amount, or null while the text isn't a valid one of this [kind]. */
    val amount: Money?
        get() = when (kind) {
            Kind.RATE -> MoneyInput.rate(text)
            Kind.PAYMENT -> MoneyInput.payment(text)
            Kind.OPTIONAL_RATE -> if (text.isBlank()) Money.ZERO else MoneyInput.rate(text)
        }

    /** Whether the field holds a usable amount. */
    val isValid: Boolean get() = amount != null

    /**
     * Whether to mark the field as wrong. An **empty** field is incomplete, not wrong — nobody
     * should be shown an error for a box they haven't reached yet — so this is true only once
     * something unusable has actually been typed.
     */
    val isError: Boolean get() = text.isNotBlank() && amount == null

    /** The same field with [newText] typed into it. */
    fun typed(newText: String): MoneyField = copy(text = newText)

    /**
     * Seeds the field from [suggested], but **only while it is still empty** — an amount the tutor
     * has already typed is never clobbered by a default arriving a moment later. A null or
     * unset (zero, for [Kind.OPTIONAL_RATE] sources) suggestion leaves the field alone.
     */
    fun prefilledWith(suggested: Money?): MoneyField =
        if (text.isBlank() && suggested != null) copy(text = suggested.toAmountInput()) else this

    companion object {
        /** A field already showing [amount]. */
        fun of(amount: Money, kind: Kind = Kind.RATE): MoneyField =
            MoneyField(amount.toAmountInput(), kind)

        /** A field showing [amount], or empty when it is null. */
        fun ofOrEmpty(amount: Money?, kind: Kind = Kind.RATE): MoneyField =
            if (amount == null) MoneyField(kind = kind) else of(amount, kind)
    }
}
