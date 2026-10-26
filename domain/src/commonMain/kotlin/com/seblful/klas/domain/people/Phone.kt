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
     * give: a number without a code, for a currency that names no one country ([SHARED_ACROSS_CODES]).
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

    /** The ITU calling code of the country issuing [currency]; null for one shared across several codes. */
    fun callingCode(currency: Currency): String? = CALLING_CODES[currency]

    /**
     * Every currency issued for one calling code; `PhoneTest` holds a new currency to adding its code here.
     * Not the euro, the CFA francs or the CFP franc, which span several ([SHARED_ACROSS_CODES]).
     */
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
        Currency.AFN to "+93",
        Currency.ALL to "+355",
        Currency.AMD to "+374",
        Currency.AOA to "+244",
        Currency.ARS to "+54",
        Currency.AWG to "+297",
        Currency.AZN to "+994",
        Currency.BAM to "+387",
        Currency.BBD to "+1",
        Currency.BDT to "+880",
        Currency.BHD to "+973",
        Currency.BIF to "+257",
        Currency.BMD to "+1",
        Currency.BND to "+673",
        Currency.BOB to "+591",
        Currency.BSD to "+1",
        Currency.BTN to "+975",
        Currency.BWP to "+267",
        Currency.BZD to "+501",
        Currency.CDF to "+243",
        Currency.CLP to "+56",
        Currency.COP to "+57",
        Currency.CRC to "+506",
        Currency.CUP to "+53",
        Currency.CVE to "+238",
        Currency.DJF to "+253",
        Currency.DOP to "+1",
        Currency.DZD to "+213",
        Currency.EGP to "+20",
        Currency.ERN to "+291",
        Currency.ETB to "+251",
        Currency.FJD to "+679",
        Currency.FKP to "+500",
        Currency.GEL to "+995",
        Currency.GHS to "+233",
        Currency.GIP to "+350",
        Currency.GMD to "+220",
        Currency.GNF to "+224",
        Currency.GTQ to "+502",
        Currency.GYD to "+592",
        Currency.HNL to "+504",
        Currency.HTG to "+509",
        Currency.HUF to "+36",
        Currency.IDR to "+62",
        Currency.ILS to "+972",
        Currency.IQD to "+964",
        Currency.IRR to "+98",
        Currency.ISK to "+354",
        Currency.JMD to "+1",
        Currency.JOD to "+962",
        Currency.KES to "+254",
        Currency.KGS to "+996",
        Currency.KHR to "+855",
        Currency.KMF to "+269",
        Currency.KWD to "+965",
        Currency.KYD to "+1",
        Currency.LAK to "+856",
        Currency.LBP to "+961",
        Currency.LKR to "+94",
        Currency.LRD to "+231",
        Currency.LSL to "+266",
        Currency.LYD to "+218",
        Currency.MAD to "+212",
        Currency.MDL to "+373",
        Currency.MGA to "+261",
        Currency.MKD to "+389",
        Currency.MMK to "+95",
        Currency.MNT to "+976",
        Currency.MOP to "+853",
        Currency.MRU to "+222",
        Currency.MUR to "+230",
        Currency.MVR to "+960",
        Currency.MWK to "+265",
        Currency.MYR to "+60",
        Currency.MZN to "+258",
        Currency.NAD to "+264",
        Currency.NGN to "+234",
        Currency.NIO to "+505",
        Currency.NPR to "+977",
        Currency.OMR to "+968",
        Currency.PAB to "+507",
        Currency.PEN to "+51",
        Currency.PGK to "+675",
        Currency.PHP to "+63",
        Currency.PKR to "+92",
        Currency.PYG to "+595",
        Currency.QAR to "+974",
        Currency.RON to "+40",
        Currency.RSD to "+381",
        Currency.RWF to "+250",
        Currency.SBD to "+677",
        Currency.SCR to "+248",
        Currency.SDG to "+249",
        Currency.SHP to "+290",
        Currency.SLE to "+232",
        Currency.SOS to "+252",
        Currency.SRD to "+597",
        Currency.SSP to "+211",
        Currency.STN to "+239",
        Currency.SYP to "+963",
        Currency.SZL to "+268",
        Currency.THB to "+66",
        Currency.TJS to "+992",
        Currency.TMT to "+993",
        Currency.TND to "+216",
        Currency.TOP to "+676",
        Currency.TTD to "+1",
        Currency.TWD to "+886",
        Currency.TZS to "+255",
        Currency.UGX to "+256",
        Currency.UYU to "+598",
        Currency.UZS to "+998",
        Currency.VES to "+58",
        Currency.VND to "+84",
        Currency.VUV to "+678",
        Currency.WST to "+685",
        Currency.XCD to "+1",
        Currency.YER to "+967",
        Currency.ZMW to "+260",
        Currency.ZWG to "+263",
    )

    /** Prefixes for a national number that is not the default `0`; the first that matches is dropped. */
    private val TRUNK_PREFIXES = mapOf(
        "+7" to listOf("8"),
        "+375" to listOf("80", "0"),
        "+1" to listOf("1"),
    )
    private val DEFAULT_TRUNK = listOf("0")

    /** The currencies of several countries with different calling codes, so no code to assume. */
    val SHARED_ACROSS_CODES: Set<Currency> = setOf(Currency.EUR, Currency.XAF, Currency.XOF, Currency.XPF)

    /** Country calling codes are one to three digits; no reachable number is that short. */
    private const val MAX_CODE_DIGITS = 3
}
