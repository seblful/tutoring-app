package com.seblful.klas.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/**
 * A [MigrationTestHelper] over a fresh database file called [name], in the mode the app runs in: the bundled
 * SQLite driver (`di/DatabaseModule`), so what runs is each step's `migrate(SQLiteConnection)` — the
 * overload Room calls on a phone.
 *
 * Two things the support-library helper did and this one does not: it starts from whatever file is there,
 * so the file is deleted here first; and it does not look for tables a step left behind, which the tests
 * of a step that sets tables aside check themselves, by comparing every table.
 */
internal fun migrationTestHelper(name: String): MigrationTestHelper {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    context.deleteDatabase(name)
    return MigrationTestHelper(
        instrumentation = instrumentation,
        file = context.getDatabasePath(name),
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )
}

/** Every row [sql] returns, each as column → value; a whole number is a [Long], a blob a list of bytes. */
internal fun SQLiteConnection.rows(sql: String): List<Map<String, Any?>> = prepare(sql).use { row ->
    buildList {
        while (row.step()) add(row.getColumnNames().withIndex().associate { (i, column) -> column to row.value(i) })
    }
}

/** The first row [sql] returns, failing the test with [missing] when there is none. */
internal fun SQLiteConnection.row(sql: String, missing: String = "no row for: $sql"): Map<String, Any?> =
    rows(sql).also { assertTrue(missing, it.isNotEmpty()) }.first()

/** The first column of the first row [sql] returns, as a number — a `count(*)`, a `max(id)`, a pragma. */
internal fun SQLiteConnection.long(sql: String): Long = prepare(sql).use { row ->
    assertTrue("no row for: $sql", row.step())
    row.getLong(0)
}

private fun SQLiteStatement.value(i: Int): Any? = when (getColumnType(i)) {
    SQLITE_DATA_NULL -> null
    SQLITE_DATA_INTEGER -> getLong(i)
    SQLITE_DATA_FLOAT -> getDouble(i)
    SQLITE_DATA_BLOB -> getBlob(i).toList()
    else -> getText(i)
}
