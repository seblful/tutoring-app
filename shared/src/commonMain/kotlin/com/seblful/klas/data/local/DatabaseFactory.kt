package com.seblful.klas.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

/** All hosts share the same driver, schema and migration chain. */
fun buildDatabase(
    builder: RoomDatabase.Builder<AppDatabase>,
    allowDestructiveMigration: Boolean = false,
): AppDatabase = builder
    .setDriver(BundledSQLiteDriver())
    .addMigrations(*Migrations.ALL)
    .apply { if (allowDestructiveMigration) fallbackToDestructiveMigration(dropAllTables = true) }
    .build()
