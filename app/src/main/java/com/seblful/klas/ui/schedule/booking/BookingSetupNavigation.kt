package com.seblful.klas.ui.schedule.booking

/** Setup destinations pushed over the booking draft. */
data class BookingSetupNavigation(
    val addStudent: () -> Unit = {},
    val addGroup: () -> Unit = {},
    val studentSubjects: (Long) -> Unit = {},
    val groupEdit: (Long) -> Unit = {},
    val subjects: () -> Unit = {},
)
