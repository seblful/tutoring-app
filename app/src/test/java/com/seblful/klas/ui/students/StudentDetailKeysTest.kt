package com.seblful.klas.ui.students

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentDetailKeysTest {

    /**
     * Reproduces the crash on opening a student with a balance. Subjects, payments and classes
     * are keyed in one `LazyColumn`; their ids come from different tables and overlap, so id `1`
     * appears in all three sections. Compose rejects duplicate keys, so the screen must namespace
     * them. This asserts the combined keys (in the same render order as the screen) stay unique.
     */
    @Test
    fun `keys are unique when subject, payment and class ids overlap`() {
        val subjectIds = listOf(1L, 2L, 3L)
        val paymentIds = listOf(1L, 2L)
        val classIds = listOf(1L, 2L, 3L, 4L)

        val keys = subjectIds.map(StudentDetailKeys::subject) +
            paymentIds.map(StudentDetailKeys::payment) +
            classIds.map(StudentDetailKeys::klass)

        assertEquals(
            "every LazyColumn key must be globally unique",
            keys.size,
            keys.toSet().size,
        )
    }

    @Test
    fun `the same id in different sections yields different keys`() {
        assertEquals(
            3,
            setOf(
                StudentDetailKeys.subject(1L),
                StudentDetailKeys.payment(1L),
                StudentDetailKeys.klass(1L),
            ).size,
        )
    }

    /**
     * The card's own furniture — the stat row, the sticky tab bar, each tab's add button and empty
     * state — shares the list with the id-keyed rows, so its fixed names have to be distinct from each
     * other *and* unable to collide with any row's key. This is the same crash as above, reached from
     * the other side: a fixed key that happened to read like a row's would take a row's slot.
     */
    @Test
    fun `the card's fixed keys are distinct from each other and from every row key`() {
        val fixed = listOf(
            StudentDetailKeys.STATS,
            StudentDetailKeys.CONTACTS,
            StudentDetailKeys.TABS,
            StudentDetailKeys.SUBJECTS_ADD,
            StudentDetailKeys.SUBJECTS_EMPTY,
            StudentDetailKeys.CLASSES_FILTER,
            StudentDetailKeys.CLASSES_EMPTY,
            StudentDetailKeys.PAYMENTS_ADD,
            StudentDetailKeys.PAYMENTS_EMPTY,
        )
        assertEquals("the fixed keys collide with each other", fixed.size, fixed.toSet().size)

        // Row keys for a wide spread of ids, including the ones a small database actually produces.
        val rows = (0L..50L).flatMap {
            listOf(
                StudentDetailKeys.subject(it),
                StudentDetailKeys.payment(it),
                StudentDetailKeys.klass(it),
            )
        }
        val all = fixed + rows
        assertEquals("a fixed key collides with a row key", all.size, all.toSet().size)
    }
}
