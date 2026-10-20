package com.seblful.klas.testutil

import com.seblful.klas.format.AppLocale
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

class AppLocaleRule(private val locale: Locale) : TestWatcher() {

    private var previous: Locale = Locale.ROOT

    override fun starting(description: Description) {
        previous = AppLocale.current
        AppLocale.current = locale
    }

    override fun finished(description: Description) {
        AppLocale.current = previous
    }

    companion object {
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
        val ENGLISH: Locale = Locale.forLanguageTag("en")

        /** The app in Russian — what most of the suite's formatted expectations are written in. */
        fun russian() = AppLocaleRule(RUSSIAN)

        /** The app in English. */
        fun english() = AppLocaleRule(ENGLISH)
    }
}
