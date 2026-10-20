package com.seblful.klas.settings

/** Device defaults resolved by the host; shared code receives tags and ISO currency codes. */
data class DevicePreferences(
    val languageTags: List<String>,
    val currencyCodes: List<String?>,
)
