package com.seblful.klas.testutil

import com.seblful.klas.HostServices
import com.seblful.klas.createKlasStore
import com.seblful.klas.data.local.KlasDatabase
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.settings.DevicePreferences

fun TestEnv.store(
    database: KlasDatabase = KlasDatabase(this.database.db),
    hostClock: WallClock = clock,
) = createKlasStore(
    database,
    object : HostServices {
        override val preferences = DevicePreferences(listOf("ru-BY"), listOf("BYN"))
        override val clock = hostClock
        override val calendar = this@store.calendar
        override val reminders = this@store.reminders
        override val digests = this@store.digestScheduler
        override val notifier = this@store.notifier
        override val restoreSnapshots = this@store.restoreSnapshots
    },
)

internal expect class DatabaseFile() : AutoCloseable {
    fun open(): KlasDatabase
    override fun close()
}
