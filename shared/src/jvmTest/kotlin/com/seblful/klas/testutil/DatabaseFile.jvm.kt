package com.seblful.klas.testutil

import com.seblful.klas.data.local.KlasDatabase
import com.seblful.klas.data.local.openDatabase
import java.nio.file.Files

internal actual class DatabaseFile actual constructor() : AutoCloseable {
    private val directory = Files.createTempDirectory("klas-tests-")
    actual fun open(): KlasDatabase = openDatabase(directory.resolve("klas.db").toString())
    actual override fun close() {
        check(directory.toFile().deleteRecursively()) { "Could not remove the test database directory" }
    }
}
