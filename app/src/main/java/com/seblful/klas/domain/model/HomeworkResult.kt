package com.seblful.klas.domain.model

/**
 * How one student did the homework set at a lesson — their `attendance.homeworkResult`.
 *
 * **Stored on the row of the lesson it was set at, not the one it was checked at.** The app keeps no
 * series, so when a lesson ends the next one is often not booked yet and there is nothing to attach a
 * verdict to; and the lesson it is *due* at is derived (`domain/Homework`), so moving or cancelling that
 * lesson reroutes the check without a writer. A result filed against the checking lesson would point at
 * whatever happened to be next when it was written.
 *
 * **Null on the row means "not checked yet"**, which is a real state rather than a fourth constant: it is
 * what every row is until the tutor says otherwise, and what an upgrade backfills.
 */
enum class HomeworkResult {
    DONE,
    PARTIAL,
    NOT_DONE;

    companion object {
        /**
         * As [LessonStatus.parse], for the `attendance.homeworkResult` column. A result carries no money,
         * so the mapper reads an unrecognized one as not checked rather than failing the read — the cost is
         * one verdict to give again, against a screen that will not open.
         */
        fun parse(stored: String): HomeworkResult? = entries.firstOrNull { it.name == stored }
    }
}
