package com.seblful.klas.ui.students

/**
 * Stable item keys for the single `LazyColumn` in [StudentDetailScreen].
 *
 * Subjects, payments and classes live in separate Room tables whose ids each start at 1, so a
 * subject, a payment and a class can all have id `1`. Compose requires every key within one
 * `LazyColumn` to be unique; keying items by the raw id makes those ids collide and throws
 * `IllegalArgumentException` while composing. That happens for any student with data in more than
 * one section at once — i.e. anyone with a completed class *and* a payment, which is exactly the
 * set of students who have a balance. Namespacing each id by its kind keeps the keys distinct.
 *
 * The fixed keys below name the card's own furniture (the stat row, the tabs, each tab's add button
 * and empty state). They exist for the same reason: they share the list with the id-keyed rows, so
 * they need names that no row can collide with — and a key on the sticky tab bar in particular is
 * what keeps it from being re-created, and its ripple restarted, each time a tab swaps the rows
 * underneath it.
 */
internal object StudentDetailKeys {
    const val STATS = "stats"
    const val CONTACTS = "contacts"
    const val TABS = "tabs"
    const val SUBJECTS_ADD = "subjects:add"
    const val SUBJECTS_EMPTY = "subjects:empty"
    const val CLASSES_FILTER = "classes:filter"
    const val CLASSES_EMPTY = "classes:empty"
    const val PAYMENTS_ADD = "payments:add"
    const val PAYMENTS_EMPTY = "payments:empty"

    fun subject(id: Long): String = "subject:$id"
    fun payment(id: Long): String = "payment:$id"
    fun klass(id: Long): String = "class:$id"
}
