package com.seblful.klas.ui.students

import kotlin.test.Test
import kotlin.test.assertEquals

class StudentDetailKeysTest {

    @Test
    fun `the same id as a payment and as a lesson yields different keys`() {
        val keys = (0L..50L).flatMap { listOf(StudentDetailKeys.payment(it), StudentDetailKeys.klass(it)) }
        assertEquals(keys.size, keys.toSet().size, "every LazyColumn key must be globally unique")
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
        assertEquals(fixed.size, fixed.toSet().size, "the fixed keys collide with each other")

        // Row keys for a wide spread of ids, including the ones a small database actually produces.
        val rows = (0L..50L).flatMap { listOf(StudentDetailKeys.payment(it), StudentDetailKeys.klass(it)) }
        val all = fixed + rows
        assertEquals(all.size, all.toSet().size, "a fixed key collides with a row key")
    }
}
