package com.seblful.klas.domain.model

/**
 * The language chosen in Settings and setup. [SYSTEM] follows the phone's preferred languages.
 * Constant names are persisted in `settings.language`; renames or removals require a migration.
 * [tag] is the BCP 47 tag shared by resources and formatters, or null for [SYSTEM].
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    RUSSIAN("ru"),
    ENGLISH("en"),
    UKRAINIAN("uk"),
    POLISH("pl"),
    GERMAN("de"),
    FRENCH("fr"),
    SPANISH("es"),
    PORTUGUESE_BRAZIL("pt-BR"),
    TURKISH("tr"),
    ITALIAN("it"),
    BELARUSIAN("be"),
    PORTUGUESE_PORTUGAL("pt-PT"),
    DUTCH("nl"),
    GREEK("el"),
    FINNISH("fi"),
    DANISH("da"),
    NORWEGIAN_BOKMAL("nb"),
    SWEDISH("sv"),
    ARABIC("ar"),
    KAZAKH("kk"),
    HINDI("hi"),
    CHINESE_SIMPLIFIED("zh-CN"),
    CHINESE_TRADITIONAL("zh-TW"),
    JAPANESE("ja"),
    KOREAN("ko"),
    ;

    /** Resolve resources and formatters through the same choice. */
    fun tagFor(preferred: List<String>): String = tag ?: of(preferred).tag!!

    companion object {
        /** Matches Android's default `values/` resources. */
        val FALLBACK = ENGLISH

        val translated: List<AppLanguage> = entries.filter { it.tag != null }

        /** First supported preference; Chinese script takes precedence over its region. */
        fun of(preferred: List<String>): AppLanguage = preferred.firstNotNullOfOrNull(::match) ?: FALLBACK

        private fun match(tag: String): AppLanguage? {
            // Ignore Unicode/private extensions so their subtags cannot be mistaken for a region.
            val parts = tag.lowercase().split('-').takeWhile { it.length != 1 }
            val language = parts.firstOrNull() ?: return null
            val script = parts.drop(1).firstOrNull { it.length == 4 }
            val region = parts.drop(1).firstOrNull { it.length == 2 || (it.length == 3 && it.all(Char::isDigit)) }
            return when (language) {
                "zh" -> when {
                    script == "hant" -> CHINESE_TRADITIONAL
                    script == "hans" -> CHINESE_SIMPLIFIED
                    region in setOf("tw", "hk", "mo") -> CHINESE_TRADITIONAL
                    else -> CHINESE_SIMPLIFIED
                }
                // Bare Portuguese follows Android's Brazilian default; other regions use Portugal.
                "pt" -> if (region == null || region == "br") PORTUGUESE_BRAZIL else PORTUGUESE_PORTUGAL
                "no" -> NORWEGIAN_BOKMAL
                else -> translated.firstOrNull { it.tag == language }
            }
        }

        /** Unknown stored values safely follow the phone. */
        fun parse(stored: String?): AppLanguage = entries.firstOrNull { it.name == stored } ?: SYSTEM
    }
}
