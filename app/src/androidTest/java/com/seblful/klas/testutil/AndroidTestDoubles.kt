package com.seblful.klas.testutil

import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.domain.Reminders
import java.util.Locale

/**
 * The phone the fixtures assume: Russian, in Belarus — the app's original tutor.
 *
 * `SettingsRepository` reads it for the two answers a settings row that does not exist yet cannot give: what
 * `AppLanguage.SYSTEM` resolves to, and which currency an amount is captioned with. None of these tests is
 * about either, so they all want "the app as it shipped".
 *
 * A near-copy of the unit-test value of the same name, for the reason [RecordingReminderScheduler] is: the
 * two source sets share no fixtures module.
 */
val RussianMinskPhone: List<Locale> = listOf(Locale.forLanguageTag("ru-BY"))

/**
 * A [ReminderScheduler] that only records. Instrumented tests build repositories by hand and each
 * needs *some* scheduler; before this they each wrote their own no-op lambda, which stopped
 * compiling the moment the port gained a second method. One double, four call sites.
 *
 * Deliberately a near-copy of the unit-test double of the same name: `src/test` and `src/androidTest`
 * are separate source sets with no shared fixtures module, so neither can see the other's.
 */
class RecordingReminderScheduler : ReminderScheduler {
    var refreshCount = 0
        private set

    val schedules = mutableListOf<List<Reminders.Plan>>()

    override fun refreshNow() {
        refreshCount++
    }

    override fun schedule(plans: List<Reminders.Plan>) {
        schedules += plans
    }
}
