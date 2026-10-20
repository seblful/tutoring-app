package com.seblful.klas.testutil

import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
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

class RecordingReminderScheduler : ReminderScheduler {
    var refreshCount = 0
        private set

    val schedules = mutableListOf<List<Reminders.Plan>>()

    var periodicCount = 0
        private set

    override fun refreshNow() {
        refreshCount++
    }

    override fun ensurePeriodicMaintenance() {
        periodicCount++
    }

    override fun schedule(plans: List<Reminders.Plan>) {
        schedules += plans
    }
}
