package com.seblful.klas.ui.students

import org.junit.Assert.assertEquals
import org.junit.Test

class StudentDetailKeysTest {

    @Test
    fun `the same id as a payment and as a lesson yields different keys`() {
        val keys = (0L..50L).flatMap { listOf(StudentDetailKeys.payment(it), StudentDetailKeys.klass(it)) }
        assertEquals("every LazyColumn key must be globally unique", keys.size, keys.toSet().size)
    }

    @Test
    fun `the fixed keys are distinct from each other and from every row key`() {
        val fixed = listOf(
            StudentDetailKeys.CONTACTS,
            StudentDetailKeys.NEXT,
            StudentDetailKeys.BALANCE,
            StudentDetailKeys.LESSONS,
            StudentDetailKeys.HOMEWORK,
            StudentDetailKeys.PAYMENTS,
            StudentDetailKeys.SUBJECTS,
            StudentDetailKeys.CLASSES_FILTER,
            StudentDetailKeys.CLASSES_EMPTY,
            StudentDetailKeys.PAYMENTS_ADD,
            StudentDetailKeys.PAYMENTS_EMPTY,
        )
        assertEquals("the fixed keys collide with each other", fixed.size, fixed.toSet().size)

        // Row keys for a wide spread of ids, including the ones a small database actually produces.
        val rows = (0L..50L).flatMap { listOf(StudentDetailKeys.payment(it), StudentDetailKeys.klass(it)) }
        val all = fixed + rows
        assertEquals("a fixed key collides with a row key", all.size, all.toSet().size)
    }
}
