package com.seblful.klas.testutil

import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.notifications.AlarmClock
import com.seblful.klas.notifications.ArmedReminders
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RecordingAlarmClock : AlarmClock {

    data class Armed(val classId: Long, val atMillis: Long, val exact: Boolean)

    val armed = mutableListOf<Armed>()
    val cancelled = mutableListOf<Long>()

    override fun setExact(classId: Long, atMillis: Long) {
        armed += Armed(classId, atMillis, exact = true)
    }

    override fun setInexact(classId: Long, atMillis: Long) {
        armed += Armed(classId, atMillis, exact = false)
    }

    override fun cancel(classId: Long) {
        cancelled += classId
    }

    /** Forgets the calls so far, for a test asserting what a *second* arming did. */
    fun clear() {
        armed.clear()
        cancelled.clear()
    }
}

class FakeArmedReminders(private var ids: Set<Long> = emptySet()) : ArmedReminders {
    override fun read(): Set<Long> = ids
    override fun write(ids: Set<Long>) {
        this.ids = ids
    }
}

/**
 * The phone the fixtures assume: Russian, in Belarus — the app's original tutor.
 *
 * Which matters to exactly two answers, both of them about a settings row nobody has written yet: what
 * `AppLanguage.SYSTEM` resolves to, and which currency an amount is captioned with. A test about either
 * hands [TestEnvRule] a different list; every other test wants "the app as it shipped", which is this.
 */
val RussianMinskPhone: List<java.util.Locale> = listOf(java.util.Locale.forLanguageTag("ru-BY"))

/** [UiTestEnv] as a JUnit rule that closes the database after the test, on an Android phone's [deviceLocales]. */
class TestEnvRule(
    database: TestDatabase = TestDatabase(),
    val deviceLocales: List<java.util.Locale> = RussianMinskPhone,
    clock: MutableClock = testClock(),
) : UiTestEnv(database, clock, deviceLocales.toDevicePreferences()),
    TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                close()
            }
        }
    }
}
