package com.seblful.klas.data.local

/**
 * The id a row was saved under: [rowId] as Room's `@Upsert` returned it, or [current] when that
 * answer is **-1** — Room's way of saying the row already existed and was updated in place, which
 * is exactly the case an edit is.
 *
 * One statement of the sentinel, next to the DAOs that produce it. Trusting the raw answer on an edit
 * would point every member insert of a group at id -1 and fail the foreign key — on the device only,
 * since a fake DAO that returns the real id on update keeps the branch off every JVM path.
 */
fun upsertedId(rowId: Long, current: Long): Long = if (rowId == -1L) current else rowId
