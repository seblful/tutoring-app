package com.seblful.klas.data.local

/**
 * The id a row was saved under: [rowId] as Room's `@Upsert` returned it, or [current] when that
 * answer is **-1** — Room's way of saying the row already existed and was updated in place, which
 * is exactly the case an edit is.
 *
 * One statement of the sentinel, next to the DAOs that produce it. Four repositories each carried
 * their own three-token copy of this branch, and trusting the raw answer on an edit once pointed
 * every member insert of a group at id -1 and failed the foreign key — on the device only, because
 * two of the fake DAOs returned the real id on update and so kept the branch off every JVM path.
 */
fun upsertedId(rowId: Long, current: Long): Long = if (rowId == -1L) current else rowId
