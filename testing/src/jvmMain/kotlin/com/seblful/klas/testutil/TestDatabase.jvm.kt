package com.seblful.klas.testutil

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.seblful.klas.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers

actual fun openTestDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.Unconfined)
    .build()
