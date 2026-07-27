package com.seblful.klas.data.local

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Emptying a table happens in one file.** `SnapshotDao` is that file.
 *
 * Backup and restore need what nothing else does: whole tables, ids preserved, no rule applied. Those
 * 25 methods used to live on the same eight DAOs the repositories use, so every repository that was
 * handed a DAO to hold an invariant with was also handed the `deleteAll()` that voids it. Nothing but
 * the *type* of the injected parameter stood between `SubjectRepository` and an empty subjects table —
 * no calendar events withdrawn, no reminders re-planned, no foreign key considered.
 *
 * The split is the fix; this is what keeps it split. A wipe added back to `Daos.kt` is invisible in
 * review — it is two lines that look like every other query — and it silently widens the surface again.
 * Room's own annotations are not retained at runtime, so the source is read, exactly as
 * `StoredEnumNamesTest` reads it for the status literals.
 */
class WholeTableWritesTest {

    /** `DELETE FROM x` with no `WHERE` — every row in the table, whatever it was. */
    private val unqualifiedDelete = Regex("""DELETE FROM (\w+)\s*"""")

    @Test
    fun `no DAO but SnapshotDao empties a whole table`() {
        val daos = ProjectFiles.sourceFile("data/local/Daos.kt").readText()

        val wipes = unqualifiedDelete.findAll(daos).map { it.groupValues[1] }.toList()
        assertEquals(
            "A whole-table delete appeared in Daos.kt, which every repository is injected from. It " +
                "belongs on SnapshotDao, where the only caller that legitimately wants one — a " +
                "restore — already is: $wipes",
            emptyList<String>(),
            wipes,
        )
    }

    /**
     * And the other half of the same claim: they are all still *there*. A restore that stopped deleting
     * one table would import into a database that still holds the old rows for it, and the insert would
     * fail on a primary key — or worse, succeed, and leave the tutor a merge of two databases.
     */
    @Test
    fun `SnapshotDao empties every table it fills`() {
        val snapshot = ProjectFiles.sourceFile("data/local/SnapshotDao.kt").readText()

        val wiped = unqualifiedDelete.findAll(snapshot).map { it.groupValues[1] }.toSet()
        val read = Regex("""SELECT \* FROM (\w+)""").findAll(snapshot).map { it.groupValues[1] }.toSet()

        assertTrue(
            "No whole-table deletes found in SnapshotDao.kt — did the wipes move? This test guards " +
                "them, so it must be pointed at wherever they now live.",
            wiped.isNotEmpty(),
        )
        assertEquals(
            "SnapshotDao reads a table it never empties, so a restore would import on top of the old " +
                "rows instead of replacing them.",
            emptySet<String>(),
            read - wiped,
        )
    }
}
