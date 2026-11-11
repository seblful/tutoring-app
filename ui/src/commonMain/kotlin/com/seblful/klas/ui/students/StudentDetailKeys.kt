package com.seblful.klas.ui.students

/** List keys for the student's card and for the page each of its lists opens to. */
object StudentDetailKeys {
    const val CONTACTS = "contacts"
    const val NEXT = "next"
    const val BALANCE = "balance"
    const val LESSONS = "lessons"
    const val HOMEWORK = "homework"
    const val PAYMENTS = "payments"
    const val SUBJECTS = "subjects"

    const val CLASSES_FILTER = "classes:filter"
    const val CLASSES_EMPTY = "classes:empty"
    const val PAYMENTS_ADD = "payments:add"
    const val PAYMENTS_EMPTY = "payments:empty"

    fun payment(id: Long): String = "payment:$id"
    fun klass(id: Long): String = "class:$id"
}
