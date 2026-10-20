package com.seblful.klas.data.local

import androidx.room.Room
import androidx.room.RoomDatabase

fun databaseBuilder(path: String): RoomDatabase.Builder<AppDatabase> = Room.databaseBuilder<AppDatabase>(name = path)
