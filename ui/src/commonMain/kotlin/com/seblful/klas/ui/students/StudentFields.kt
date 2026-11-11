package com.seblful.klas.ui.students

import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.people.Phone

/**
 * [onto] with a student's fields as the tutor typed them, in the shape they are stored in: the name, email
 * and note trimmed, the phone through [Phone.stored] — or null when the name is blank, since a student
 * nobody can name cannot be listed, searched or booked.
 *
 * @param onto the student being edited, keeping their id and archive state; a new one by default.
 */
fun typedStudent(
    name: String,
    phone: String,
    email: String,
    note: String,
    mode: ClassMode,
    onto: Student = Student(name = ""),
): Student? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    return onto.copy(
        name = trimmed,
        phone = Phone.stored(phone),
        email = email.trim(),
        note = note.trim(),
        defaultMode = mode,
    )
}
