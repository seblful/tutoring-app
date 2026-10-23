package com.seblful.klas.ui.common

import android.content.res.Resources
import com.seblful.klas.R

/** What archiving a student or a group says: that it happened, and how many planned lessons it called off. */
fun Resources.archivedMessage(cancelledLessons: Int): String =
    if (cancelledLessons == 0) getString(R.string.archived_message)
    else getQuantityString(R.plurals.archived_lessons_cancelled, cancelledLessons, cancelledLessons)
