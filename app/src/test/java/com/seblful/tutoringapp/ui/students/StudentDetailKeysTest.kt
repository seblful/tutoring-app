package com.seblful.tutoringapp.ui.students

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
}
