package com.seblful.klas.testutil

import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.seblful.klas.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers

/**
 * Room keeps the context only to name database files and to choose a journal mode; this database has no file
 * and states its mode, so it needs no application. On the host JVM it still needs Robolectric (`UiTest`):
 * `android.jar`'s stub `SQLException` drops the message Room's upsert reads to recognise a unique-key clash.
 */
actual fun openTestDatabase(): AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>(NoContext())
    .setDriver(BundledSQLiteDriver())
    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
    .setQueryCoroutineContext(Dispatchers.Unconfined)
    .build()

private class NoContext : ContextWrapper(null)
