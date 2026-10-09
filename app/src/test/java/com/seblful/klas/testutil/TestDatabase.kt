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

/**
 * The app's real Room database, in memory, for a JVM test — the seam the repository, backup and
 * view-model tests stand on instead of hand-written fake DAOs.
 *
 * Those fakes re-implemented every query in Kotlin, so they could drift from the SQL and could not
 * catch what only SQLite does: a foreign key refusing a row, a cascade, a rollback, a join. Here every
 * DAO is the generated one, over the production schema — `@TypeConverters` and foreign keys included,
 * since both are declared on the database and its entities rather than on the builder.
 *
 * - **Robolectric** is only what hands Room its `Context`; the test class runs under
 *   `RobolectricTestRunner`. Nothing reaches the platform's SQLite.
 * - **`BundledSQLiteDriver`**: SQLite compiled into the test's own JNI library, so every machine runs the
 *   same SQLite version rather than the host's or Robolectric's.
 * - **Queries run on [Dispatchers.Unconfined]**, i.e. inline on the thread that asked. Every test here
 *   is single-threaded on a `TestDispatcher`; Room's default would answer on its own executor threads,
 *   and an answer arriving on another thread at a real-time moment is one `advanceUntilIdle()` cannot
 *   wait for. Inline, a write — and the invalidation it sends to every open `Flow` — has happened by the
 *   time the call returns, which is the timing the fakes had and the tests were written against. A
 *   `TestDispatcher` here instead would put Room's own connection-acquire timeout on virtual time, where
 *   a test advancing the clock past midnight would fire it.
 * - **Transactions are the production [RoomTransactor]'s**, which is why it is written with
 *   `useWriterConnection` rather than `withTransaction`: the latter is support-library only and throws
 *   once a driver is set. Same thread as everything else, so a rollback is as deterministic as a commit.
 *
 * Closed after each test: as a [TestRule] when it is one (`@get:Rule`), or by [close] (`use {}`) when a
 * test opens a second.
 */
class TestDatabase : TestRule, Closeable {

    val db: AppDatabase = Room
        .inMemoryDatabaseBuilder<AppDatabase>(ApplicationProvider.getApplicationContext())
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Unconfined)
        // Robolectric runs the test on its main looper thread, which is where every query now runs too
        // (inline, above). Room's guard against that is about a phone's frame budget, not correctness.
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
