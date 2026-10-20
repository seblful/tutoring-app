package com.seblful.klas.data.local

import com.seblful.klas.testutil.ProjectFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WholeTableWritesTest {

    private val unqualifiedDelete = Regex("""DELETE FROM (\w+)\s*"""")

    @Test
    fun `no DAO but SnapshotDao empties a whole table`() {
        val sources = ProjectFiles.daoSources()
        assertTrue("No feature DAO sources found", sources.isNotEmpty())
        val daos = sources.joinToString("\n") { it.readText() }

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
