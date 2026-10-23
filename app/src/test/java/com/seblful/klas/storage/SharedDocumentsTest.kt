package com.seblful.klas.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SharedDocumentsTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun `sharing the same filename never replaces the previous recipient's document`() {
        val documents = SharedDocuments(temporary.newFolder())
        val first = documents.write("report.csv", "first report".encodeToByteArray())
        val second = documents.write("report.csv", "second report".encodeToByteArray())

        assertNotEquals("An old URI must not address the next report", first, second)
        assertEquals("first report", first.readText())
        assertEquals("second report", second.readText())
        assertEquals("report.csv", second.name)
    }

    @Test
    fun `expired shares are removed without touching recent documents`() {
        val root = temporary.newFolder()
        val documents = SharedDocuments(root)
        val old = documents.write("old.csv", "old".encodeToByteArray())
        old.parentFile.setLastModified(1)
        val recent = documents.write("recent.csv", "recent".encodeToByteArray())

        assertFalse(old.exists())
        assertEquals("recent", recent.readText())
    }

    @Test
    fun `a filename cannot escape its share directory`() {
        val documents = SharedDocuments(temporary.newFolder())

        assertThrows(IllegalArgumentException::class.java) {
            documents.write("../private.txt", "no".encodeToByteArray())
        }
    }
}
