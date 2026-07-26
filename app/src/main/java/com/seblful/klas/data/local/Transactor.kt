package com.seblful.klas.data.local

import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs a block of writes as **one** database transaction: they all commit, or none of them does.
 *
 * A port rather than a direct `AppDatabase.withTransaction` call, for the same reason the calendar
 * and the notifier are ports — the JVM tests run repositories against in-memory fake DAOs and have
 * no Room instance to open a transaction on. Injecting [AppDatabase] into a repository would make
 * every one of those tests need a device.
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

@Singleton
class RoomTransactor @Inject constructor(
    private val db: AppDatabase,
) : Transactor {

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }
}
