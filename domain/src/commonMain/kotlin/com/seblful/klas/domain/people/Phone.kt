package com.seblful.klas.domain.people

import com.seblful.klas.domain.model.Currency

/**
 * A student's phone, as the forms offer it and as it is kept.
 *
 * The field opens on the dialling code of the tutor's currency ([prefill]), so the tutor types only the
 * number. **A field left as it opened is no phone**, not the phone «+375»: saved as typed, it put a contact
 * row on the card that dialled a country and opened Telegram on `t.me/+375`. Both save paths (a new
 * student, an edited one) keep what [stored] returns, and the card reads a number through [contactOf],
 * which also hides a prefix-only phone an earlier build already saved.
 */
object Phone {

    /**
     * What an empty phone field starts with for a tutor paid in [currency]: its country's dialling code,
     * or nothing for a currency no one country issues.
     */
    fun prefill(currency: Currency): String = callingCode(currency)?.let { "$it " }.orEmpty()

    /**
     * The phone to keep for [typed]: trimmed, and empty when nothing past a dialling code was typed — any
     * code, since the currency may have changed since the field opened.
     */
    fun stored(typed: String): String {
        val trimmed = typed.trim()
        val codeOnly = trimmed.startsWith('+') && trimmed.count(Char::isDigit) <= MAX_CODE_DIGITS
        return if (codeOnly) "" else trimmed
    }

    /** A kept phone to call or message, or null when there is no number to reach. */
    fun contactOf(phone: String): String? = stored(phone).ifEmpty { null }

    /** What an edit dialog shows for a kept phone: the number, or the dialling code to type one after. */
    fun editable(phone: String, currency: Currency): String = stored(phone).ifEmpty { prefill(currency) }

    /** The ITU calling code of the country issuing [currency]; null for the euro's twenty countries. */
    fun callingCode(currency: Currency): String? = CALLING_CODES[currency]

    /** Every currency but [Currency.EUR]; `PhoneTest` holds a new currency to adding its code here. */
    private val CALLING_CODES = mapOf(
        Currency.BYN to "+375",
        Currency.RUB to "+7",
        Currency.USD to "+1",
        Currency.AED to "+971",
        Currency.AUD to "+61",
        Currency.BRL to "+55",
        Currency.CAD to "+1",
        Currency.CHF to "+41",
        Currency.CNY to "+86",
        Currency.CZK to "+420",
        Currency.DKK to "+45",
        Currency.GBP to "+44",
        Currency.HKD to "+852",
        Currency.INR to "+91",
        Currency.JPY to "+81",
        Currency.KRW to "+82",
        Currency.KZT to "+7",
        Currency.MXN to "+52",
        Currency.NOK to "+47",
        Currency.NZD to "+64",
        Currency.PLN to "+48",
        Currency.SAR to "+966",
        Currency.SEK to "+46",
        Currency.SGD to "+65",
        Currency.TRY to "+90",
        Currency.UAH to "+380",
        Currency.ZAR to "+27",
    )

    /** Country calling codes are one to three digits; no reachable number is that short. */
    private const val MAX_CODE_DIGITS = 3
}
