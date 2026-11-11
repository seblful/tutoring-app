package com.seblful.klas.format

/** The languages most expectations are written in. */
internal const val RUSSIAN = "ru"
internal const val ENGLISH = "en"

/** [body]'s result with the app in the language [tag], the previous language restored after. */
internal fun <T> inLanguage(tag: String, body: () -> T): T {
    val previous = AppLocale.tag
    AppLocale.tag = tag
    try {
        return body()
    } finally {
        AppLocale.tag = previous
    }
}
