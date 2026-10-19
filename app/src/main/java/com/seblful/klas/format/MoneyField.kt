package com.seblful.klas.format

import com.seblful.klas.domain.model.Money

/**
 * A money text field: what the tutor has typed, and every answer a form or a screen needs about it.
 *
 * Every money input in the app used to restate the same three rules at its own call site — is this
 * parseable ([amount]), should the box go red ([isError]), may I fill it in for them
 * ([prefilledWith]) — in six different spellings, three of them inside composables where no JVM
 * test could reach them. They differ in exactly one respect, which is [kind].
 *
 * Holding the raw [text] rather than a parsed amount is deliberate: input like `"1,"` already
 * parses as one major unit, but the tutor may still be typing the fraction. Its spelling must
 * survive recomposition because re-formatting it mid-typing moves the cursor.
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

    /**
     * The amount, or null while the text isn't a valid one of this [kind] — each kind's rule is
     * its constant's KDoc, and this is the one place they are applied.
     */
    val amount: Money?
        get() = when (kind) {
            Kind.RATE -> parseMoneyOrNull(text)
            Kind.PAYMENT -> parseMoneyOrNull(text)?.takeUnless { it.isZero }
            Kind.OPTIONAL_RATE -> if (text.isBlank()) Money.ZERO else parseMoneyOrNull(text)
        }

    /** Whether the field holds a usable amount. */
    val isValid: Boolean get() = amount != null

    /** What is wrong with what was typed. The wording is the screen's; which case it is, is not. */
    enum class Error {
        /** Not a number this app can read — letters, two separators, an empty minus. */
        UNREADABLE,

        /** Readable, and zero, in a field where zero moves no money ([Kind.PAYMENT]). */
        ZERO_PAYMENT,
    }

    /**
     * Which rule the typed text breaks, or null while there is nothing to say.
     *
     * The field knew it was wrong (`isError`) long before it could say *why*, so every money input in
     * the app turned red and explained nothing — leaving a tutor with a disabled «Сохранить» to work
     * out which of six boxes it meant. The two cases are genuinely different advice: one is "that is
     * not a number", the other is "a payment of nothing is not a payment".
     *
     * An **empty** field is incomplete, not wrong: nobody should be shown an error for a box they have
     * not reached yet.
     */
    val error: Error?
        get() {
            if (text.isBlank()) return null
            val parsed = parseMoneyOrNull(text) ?: return Error.UNREADABLE
            return if (kind == Kind.PAYMENT && parsed.isZero) Error.ZERO_PAYMENT else null
        }

    /** Whether to mark the field as wrong — the same question [error] answers, as a boolean. */
    val isError: Boolean get() = error != null

    /** The same field with [newText] typed into it. */
    fun typed(newText: String): MoneyField = copy(text = newText)

    /**
     * Seeds the field from [suggested], but **only while it is still empty** — an amount the tutor
     * has already typed is never clobbered by a default arriving a moment later. Null leaves it
     * unchanged; zero is a valid suggestion. Callers represent an unset source default as null.
     */
    fun prefilledWith(suggested: Money?): MoneyField =
        if (text.isBlank() && suggested != null) copy(text = suggested.toAmountInput()) else this

    companion object {
        /** A field already showing [amount]. */
        fun of(amount: Money, kind: Kind = Kind.RATE): MoneyField =
            MoneyField(amount.toAmountInput(), kind)
    }
}
