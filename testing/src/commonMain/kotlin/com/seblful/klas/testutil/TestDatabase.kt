package com.seblful.klas.testutil

import androidx.room.useReaderConnection
import androidx.sqlite.SQLiteStatement
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.RoomTransactor
import kotlinx.coroutines.flow.first

/** A fresh in-memory database over the bundled driver; queries run unconfined so writes and flows settle together. */
expect fun openTestDatabase(): AppDatabase

class TestDatabase : AutoCloseable {
    val db: AppDatabase = openTestDatabase()

    /** The production transactor over [db], counted — see [RecordingTransactor]. */
    val transactor: RecordingTransactor = RecordingTransactor(RoomTransactor(db))

    /**
     * The tables some open `Flow` query is watching right now.
     *
     * Room learns of a write through temporary triggers on each watched table and drops them lazily, at the
     * next observer added or removed. So this adds and removes one of its own first, and what is listed is
     * what is watched now rather than what was watched at the last sync.
     */
    suspend fun watchedTables(): Set<String> {
        db.invalidationTracker.createFlow("settings", emitInitialState = true).first()
        val triggers = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT name FROM sqlite_temp_master WHERE type = 'trigger'") { statement ->
                statement.texts()
            }
        }
        return triggers.mapNotNullTo(HashSet()) { ROOM_TRIGGER.matchEntire(it)?.groupValues?.get(1) }
    }

    override fun close() = db.close()

    private companion object {
        val ROOM_TRIGGER = Regex("room_table_modification_trigger_(.+)_(?:INSERT|UPDATE|DELETE)")

        fun SQLiteStatement.texts(): List<String> = buildList { while (step()) add(getText(0)) }
    }
}
