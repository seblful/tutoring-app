package com.seblful.klas.data.lessons

import kotlin.time.Instant

/**
 * When the tutor next sees a student or a group, and when they last did — see [LessonSpan] for what counts.
 */
data class NearestLessons(val next: Instant?, val last: Instant?) {
    companion object {
        /** Nobody booked yet. */
        val NONE = NearestLessons(next = null, last = null)
    }
}
