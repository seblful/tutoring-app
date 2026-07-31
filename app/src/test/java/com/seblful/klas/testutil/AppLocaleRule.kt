package com.seblful.klas.testutil

import com.seblful.klas.format.AppLocale
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.Locale

/**
 * Pins the app's display locale for the length of a test, and puts it back afterwards.
 *
 * **Every assertion about a formatted date, month, weekday or amount needs this.** `AppLocale.current` is
 * seeded from `Locale.getDefault()` — the *phone's* answer, which in a JVM test is the build machine's — so
 * without it a test asserting «6 – 12 июля» or `-0,50` passes on a Russian workstation and fails on an
 * English CI agent, having asserted nothing about the app either way. It is the same argument the injected
 * `Clock` is there for: the shipped path reads an ambient value, so a fixture has to be able to state it.
 *
 * Restores rather than clears, because the object is process-wide and JUnit runs classes in one JVM: a test
 * that left English behind would decide what the next class formatted in.
 */
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
