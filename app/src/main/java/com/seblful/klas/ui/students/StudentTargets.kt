package com.seblful.klas.ui.students
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings

/** The task carried from a lesson or an unfinished setup form, opened as its dialog over the card. */
enum class StudentTask { HOMEWORK, PAYMENT, SUBJECT }

/** A student's list that can run long, shown in full on its own page. Subjects are few, and stay on the card. */
enum class StudentSection(val titleRes: StringResource) {
    LESSONS(Strings.student_classes_title),
    PAYMENTS(Strings.student_payments_title),

    /** Theirs and their groups', current and done — the one place an archived student's is still listed. */
    HOMEWORK(Strings.student_homework_title),
}
