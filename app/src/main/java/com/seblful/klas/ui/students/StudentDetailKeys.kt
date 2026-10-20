package com.seblful.klas.ui.students

import com.seblful.klas.ui.common.DetailCard

internal object StudentDetailKeys {
    const val BALANCE = "balance"
    const val STATS = DetailCard.STATS_KEY
    const val CONTACTS = "contacts"
    const val TABS = DetailCard.VIEWS_KEY
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
