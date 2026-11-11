package com.seblful.klas.testutil

import com.seblful.klas.format.AppLocale
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Runs a test with the app in the language [tag], a BCP 47 tag, and restores the previous one after. */
class AppLocaleRule(private val tag: String) : TestWatcher() {

    private var previous: String = AppLocale.tag

    override fun starting(description: Description) {
        previous = AppLocale.tag
        AppLocale.tag = tag
    }

    override fun finished(description: Description) {
        AppLocale.tag = previous
    }

    companion object {
        const val RUSSIAN = "ru"
        const val ENGLISH = "en"

        /** The app in Russian — what most of the suite's formatted expectations are written in. */
        fun russian() = AppLocaleRule(RUSSIAN)

        /** The app in English. */
        fun english() = AppLocaleRule(ENGLISH)
    }
}
