package com.seblful.klas.testutil

import com.seblful.klas.settings.DevicePreferences

/**
 * The repository graph over a fresh [TestDatabase], which [close] closes.
 *
 * [preferences] is the phone the fixtures assume: Russian, in Belarus — the app's original tutor. It matters
 * only to what `AppLanguage.SYSTEM` resolves to and which currency an amount is captioned with.
 */
open class TestEnv(
    val database: TestDatabase = TestDatabase(),
    clock: MutableClock = testClock(),
    preferences: DevicePreferences = RussianMinskPhone,
) : RepositoryTestEnv(database.db, database.transactor, clock, preferences), AutoCloseable {
    override fun close() = database.close()
}

val RussianMinskPhone = DevicePreferences(listOf("ru-BY"), listOf("BYN"))
