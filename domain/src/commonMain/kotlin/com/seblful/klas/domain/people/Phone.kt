package com.seblful.klas.domain.people

import com.seblful.klas.domain.model.Currency

/**
 * A student's phone, kept exactly as typed or picked from the contacts — with or without a country code. The
 * tutor's own country is known from the currency, so [international] adds its code only where an app needs the
 * whole number (Telegram, WhatsApp); a number with a `+` code is used as it is.
 *
 * A phone that is only a dialling code is no phone: an earlier build saved the prefilled `+375` as typed,
 * which put a contact row on the card that dialled a country. [stored] and [contactOf] still hide one.
 */
object Phone {

    /**
     * The phone to keep for [typed]: trimmed, and empty when nothing past a dialling code was typed — any
     * code, since an earlier build opened the field on one.
     */
    fun stored(typed: String): String {
        val trimmed = typed.trim()
        val codeOnly = trimmed.startsWith('+') && trimmed.count(Char::isDigit) <= MAX_CODE_DIGITS
        return if (codeOnly) "" else trimmed
    }

    /** A kept phone to call or message, or null when there is no number to reach. */
    fun contactOf(phone: String): String? = stored(phone).ifEmpty { null }

    /**
     * The whole number to hand to an app that needs it, as `+` and digits — or null when there is none to
     * give: a number without a code, for a currency that names no country (the euro's twenty).
     */
    fun international(phone: String, currency: Currency): String? {
        val kept = contactOf(phone) ?: return null
        val digits = kept.filter(Char::isDigit)
        return when {
            digits.isEmpty() -> null
            kept.startsWith("+") -> "+$digits"
            kept.startsWith("00") -> "+${digits.drop(2)}"
            else -> callingCode(currency)?.let { it + digits.withoutTrunk(it) }
        }
    }

    /** A national number's leading trunk prefix (`8 029…`, `0 29…`) is dialled only inside the country. */
    private fun String.withoutTrunk(code: String): String {
        val prefix = (TRUNK_PREFIXES[code] ?: DEFAULT_TRUNK).firstOrNull { startsWith(it) } ?: return this
        return drop(prefix.length)
    }

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

    /** Prefixes for a national number that is not the default `0`; the first that matches is dropped. */
    private val TRUNK_PREFIXES = mapOf(
        "+7" to listOf("8"),
        "+375" to listOf("80", "0"),
        "+1" to listOf("1"),
    )
    private val DEFAULT_TRUNK = listOf("0")

    /** Country calling codes are one to three digits; no reachable number is that short. */
    private const val MAX_CODE_DIGITS = 3
}
