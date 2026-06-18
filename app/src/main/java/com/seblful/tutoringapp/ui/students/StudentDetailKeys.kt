package com.seblful.tutoringapp.ui.students

/**
 * Stable item keys for the single `LazyColumn` in [StudentDetailScreen].
 *
 * Subjects, payments and classes live in separate Room tables whose ids each start at 1, so a
 * subject, a payment and a class can all have id `1`. Compose requires every key within one
 * `LazyColumn` to be unique; keying items by the raw id makes those ids collide and throws
 * `IllegalArgumentException` while composing. That happens for any student with data in more than
 * one section at once — i.e. anyone with a completed class *and* a payment, which is exactly the
 * set of students who have a balance. Namespacing each id by its kind keeps the keys distinct.
 */
internal object StudentDetailKeys {
    fun subject(id: Long): String = "subject:$id"
    fun payment(id: Long): String = "payment:$id"
    fun klass(id: Long): String = "class:$id"
}
