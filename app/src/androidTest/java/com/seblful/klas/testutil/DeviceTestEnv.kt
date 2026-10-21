package com.seblful.klas.testutil

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.RoomTransactor
import kotlinx.coroutines.Dispatchers

class DeviceTestEnv(
    private val database: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(
        ApplicationProvider.getApplicationContext(),
    ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .build(),
) : RepositoryTestEnv(database, RecordingTransactor(RoomTransactor(database))), AutoCloseable {
    override fun close() = database.close()
}
