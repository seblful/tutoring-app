package com.seblful.klas.testutil

import androidx.room.InvalidationTracker
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.RoomTransactor
import kotlinx.coroutines.Dispatchers
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.Closeable

class TestDatabase : TestRule, Closeable {

    val db: AppDatabase = Room
        .inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        .allowMainThreadQueries()
        .build()

    /** The production transactor over [db], counted — see [RecordingTransactor]. */
    val transactor: RecordingTransactor = RecordingTransactor(RoomTransactor(db))

    /**
     * The tables some open `Flow` query is watching right now.
     *
     * Room learns of a write through temporary triggers it keeps on each table an observer is watching,
     * named `room_table_modification_trigger_<table>_<op>`, and drops them once the last observer of the
     * table is gone. So a table listed here is one a query is still live on — what a test asserting that a
     * screen nobody watches has let go of its queries reads.
     *
     * Room drops those triggers lazily, though: a cancelled `Flow` only lowers a count, and the triggers
     * follow at the next sync, which comes with the next observer added or removed. So this adds and removes
     * one of its own first — the same sync a device would get from the next screen opening — and what is
     * listed is what is watched now rather than what was watched when the triggers were last synced.
     */
    suspend fun watchedTables(): Set<String> {
        val probe = object : InvalidationTracker.Observer("settings") {
            override fun onInvalidated(tables: Set<String>) = Unit
        }
        db.invalidationTracker.addObserver(probe)
        db.invalidationTracker.removeObserver(probe)
        val triggers = db.useReaderConnection { connection ->
            connection.usePrepared("SELECT name FROM sqlite_temp_master WHERE type = 'trigger'") { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }
        return triggers.mapNotNullTo(HashSet()) { ROOM_TRIGGER.matchEntire(it)?.groupValues?.get(1) }
    }

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                close()
            }
        }
    }

    override fun close() {
        db.close()
    }

    private companion object {
        val ROOM_TRIGGER = Regex("room_table_modification_trigger_(.+)_(?:INSERT|UPDATE|DELETE)")
    }
}
