package com.seblful.klas.ui.students

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudentFieldsTest {

    @Test
    fun `typed fields are stored trimmed, with the phone normalised`() {
        val stored = typedStudent("  Аня  ", " +375 29 123-45-67 ", " anya@example.com ", " 9 класс ", ClassMode.REMOTE)

        assertEquals(
            Student(
                name = "Аня",
                phone = "+375 29 123-45-67",
                email = "anya@example.com",
                note = "9 класс",
                defaultMode = ClassMode.REMOTE,
            ),
            stored,
        )
    }

    @Test
    fun `a blank name is refused`() {
        assertNull(typedStudent("   ", "", "", "", ClassMode.IN_PERSON))
    }

    @Test
    fun `an edit keeps the student's identity`() {
        val current = Student(id = 7, name = "Аня", note = "старое", archived = true)

        val edited = typedStudent("Анна", "+7 ", "", "новое", ClassMode.IN_PERSON, onto = current)

        assertEquals("only the dialling code is no phone", current.copy(name = "Анна", note = "новое"), edited)
    }
}
