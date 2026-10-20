package com.seblful.klas.testutil

import androidx.room.Room
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.KlasDatabase
import com.seblful.klas.data.local.buildDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

@OptIn(ExperimentalForeignApi::class)
internal actual class DatabaseFile actual constructor() : AutoCloseable {
    private val directory = NSTemporaryDirectory() + "klas-tests-" + NSUUID().UUIDString

    init {
        check(NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null))
    }

    actual fun open(): KlasDatabase =
        KlasDatabase(buildDatabase(Room.databaseBuilder<AppDatabase>(name = "$directory/klas.db")))

    actual override fun close() {
        check(NSFileManager.defaultManager.removeItemAtPath(directory, null))
    }
}
