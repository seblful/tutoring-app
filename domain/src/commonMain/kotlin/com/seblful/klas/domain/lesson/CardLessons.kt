package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.TutoringClass
import kotlin.time.Instant

/**
 * Which lessons a student's or a group's card leads with: the next one, and the few under its lessons
 * heading. Over whatever row the card lists, read through `lesson`.
 */
object CardLessons {

    /**
     * The first not cancelled and not yet over, so one under way is still next — the People list's rule
     * (`LessonSpan`). Null with none booked.
     */
    fun <T> next(rows: List<T>, now: Instant, lesson: (T) -> TutoringClass): T? = rows
        .filter { lesson(it).status != LessonStatus.CANCELLED && lesson(it).end > now }
        .minByOrNull { lesson(it).start }

    /**
     * The latest already begun, newest first, as the full list reads. With none begun yet, the ones booked
     * after [next], soonest first — so a new card shows its run of bookings rather than an empty section.
     */
    fun <T> preview(rows: List<T>, now: Instant, limit: Int, lesson: (T) -> TutoringClass): List<T> {
        val next = next(rows, now, lesson)
        val begun = rows.filter { lesson(it).start <= now && it != next }
        return if (begun.isNotEmpty()) {
            begun.sortedByDescending { lesson(it).start }.take(limit)
        } else {
            rows.filter { lesson(it).start > now && it != next }.sortedBy { lesson(it).start }.take(limit)
        }
    }
}
