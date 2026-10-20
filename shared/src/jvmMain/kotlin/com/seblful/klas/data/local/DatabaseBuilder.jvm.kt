package com.seblful.klas.data.local

import androidx.room.Room
import androidx.room.RoomDatabase

internal fun databaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(name = path)

/** Opens the shared database; the host owns the returned lifetime. */
fun openDatabase(path: String): KlasDatabase = KlasDatabase(buildDatabase(databaseBuilder(path)))
