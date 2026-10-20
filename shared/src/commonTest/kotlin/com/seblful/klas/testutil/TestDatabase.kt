package com.seblful.klas.testutil

import com.seblful.klas.data.local.RoomTransactor
import com.seblful.klas.data.openTestDatabase

class TestDatabase : AutoCloseable {
    val db = openTestDatabase()
    val transactor = RecordingTransactor(RoomTransactor(db))
    override fun close() = db.close()
}
