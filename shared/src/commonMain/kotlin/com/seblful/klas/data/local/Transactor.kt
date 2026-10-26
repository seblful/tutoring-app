package com.seblful.klas.data.local

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Runs a block of writes as **one** database transaction: they all commit, or none of them does.
 *
 * A port rather than a direct `AppDatabase` call so a test can stand between a repository and the
 * transaction: the JVM tests run every write through the real [RoomTransactor] over an in-memory
 * database, wrapped in a double that counts transactions, catches one opened inside another, and can
 * make a chosen one fail to commit — which is how a repository's "nothing half-written, nothing
 * mirrored" promise is checked without a device.
 *
 * **Never do calendar or file I/O inside [transaction].** A transaction holds SQLite's write lock,
 * and the calendar is a ContentProvider on the other side of an IPC call that may block for as long
 * as it likes. Row writes go inside; mirroring the result to the device calendar happens after the
 * commit — which is also the correct order, since the calendar is a best-effort mirror of the
 * database and never the other way round.
 */
interface Transactor {

    suspend fun <T> transaction(block: suspend () -> T): T
}

/**
 * The writer connection, in an `IMMEDIATE` transaction; the DAO calls inside [transaction] find that
 * connection in their coroutine context and join it.
 *
 * Not `withTransaction`: that one is built on `SupportSQLiteDatabase.beginTransaction` and throws
 * outright once a `SQLiteDriver` is set — which the app does (`buildDatabase`, the bundled driver),
 * and the JVM tests with it (`testutil/TestDatabase`). `useWriterConnection` + `immediateTransaction` is
 * Room's driver-era form, the one that also compiles for iOS.
 */
@SingleIn(AppScope::class)
class RoomTransactor @Inject constructor(
    private val db: AppDatabase,
) : Transactor {

    override suspend fun <T> transaction(block: suspend () -> T): T =
        db.useWriterConnection { connection -> connection.immediateTransaction { block() } }
}
