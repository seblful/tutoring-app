package com.seblful.klas.domain.people

import com.seblful.klas.domain.model.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubjectNamesTest {

    private val subjects = listOf(Subject(id = 1, name = "Математика"), Subject(id = 2, name = "Физика"))

    @Test
    fun `a name is the same subject whatever its case and spaces`() {
        assertEquals(1L, SubjectNames.sameAs("  математика ", subjects)?.id)
        assertEquals(2L, SubjectNames.sameAs("ФИЗИКА", subjects)?.id)
    }

    @Test
    fun `a new name is nobody's`() {
        assertNull(SubjectNames.sameAs("Химия", subjects))
    }

    /** Renaming a subject to its own name, recased, is not a clash with itself. */
    @Test
    fun `the subject being renamed does not clash with itself`() {
        assertNull(SubjectNames.sameAs("МАТЕМАТИКА", subjects, exceptId = 1))
        assertEquals(1L, SubjectNames.sameAs("математика", subjects, exceptId = 2)?.id)
    }
}
