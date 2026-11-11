package com.seblful.klas.ui.common

import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.Texts

/** What archiving a student or a group says: that it happened, and how many planned lessons it called off. */
fun Texts.archivedMessage(cancelledLessons: Int): String =
    if (cancelledLessons == 0) string(Strings.archived_message)
    else plural(Plurals.archived_lessons_cancelled, cancelledLessons, cancelledLessons)
