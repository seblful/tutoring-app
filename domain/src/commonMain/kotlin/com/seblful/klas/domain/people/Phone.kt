package com.seblful.klas.domain.people

/**
 * A student's phone, as the forms offer it and as it is kept.
 *
 * The field opens on the dialling code, so the tutor types only the number. **A field left as it opened
 * is no phone**, not the phone «+375»: saved as typed, it put a contact row on the card that dialled a
 * country and opened Telegram on `t.me/+375`. Both save paths (a new student, an edited one) keep what
 * [stored] returns, and the card reads a number through [contactOf], which also hides a prefix-only
 * phone an earlier build already saved.
 */
object Phone {

    /** What an empty phone field starts with. */
    const val PREFILL = "+375 "

    /** The phone to keep for [typed]: trimmed, and empty when nothing past the dialling code was typed. */
    fun stored(typed: String): String {
        val trimmed = typed.trim()
        return if (trimmed.filter(Char::isDigit) == PREFILL_DIGITS) "" else trimmed
    }

    /** A kept phone to call or message, or null when there is no number to reach. */
    fun contactOf(phone: String): String? = stored(phone).ifEmpty { null }

    /** What an edit dialog shows for a kept phone: the number, or the dialling code to type one after. */
    fun editable(phone: String): String = stored(phone).ifEmpty { PREFILL }

    private val PREFILL_DIGITS = PREFILL.filter(Char::isDigit)
}
