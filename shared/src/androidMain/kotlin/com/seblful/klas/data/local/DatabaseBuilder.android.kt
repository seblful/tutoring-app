package com.seblful.klas.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

internal fun databaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(
        context.applicationContext,
        context.getDatabasePath(AppDatabase.NAME).absolutePath,
    )

/** Opens the shared database; the host owns the returned lifetime. */
fun openDatabase(
    context: Context,
    allowDestructiveMigration: Boolean = false,
): KlasDatabase = KlasDatabase(buildDatabase(databaseBuilder(context), allowDestructiveMigration))
