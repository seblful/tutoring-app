package com.seblful.klas.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun databaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> =
    Room.databaseBuilder<AppDatabase>(
        context.applicationContext,
        context.getDatabasePath(AppDatabase.NAME).absolutePath,
    )
