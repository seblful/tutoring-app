package com.seblful.klas.domain.model

/**
 * Row ids as their own types, for the **report breakdowns** and nothing else.
 *
 * Ids are plain `Long`s everywhere else in the app on purpose, and this is not the start of changing
 * that. They are typed here because the reports are the one place where ids stop being arguments and
 * become *keys*: `Reports.incomeByStudent`, `incomeBySubject` and `incomeByGroup` all used to hand back
 * a `Map<Long, Money>`, which `ReportsViewModel` then paired by hand with one of three equally-shaped
 * `Map<Long, String>` name tables. Nothing about
 *
 * ```
 * bySubject = Reports.incomeBySubject(inPeriod).toSortedLines(total) { studentNames[it] }
 * ```
 *
 * is a type error, and nothing about the result looks wrong either: a subject id looked up among
 * student names simply misses, `orEmpty()` turns the miss into `""`, and the report draws a real amount
 * against a blank label. Silence is what makes it worth a type — a crash would have been findable.
 *
 * Elsewhere an id is passed as a named parameter to a function that takes exactly one kind
 * (`priceFor(studentId, subjectId)`, `getMemberIds(groupId)`), where the name and the signature
 * already carry the meaning; wrapping those would add ceremony without removing a mistake anyone
 * can make. Entities and the models keep `Long` too, so Room, the backup format and the migrations
 * are untouched by this.
 */
@JvmInline
value class StudentId(val value: Long)

@JvmInline
value class SubjectId(val value: Long)

@JvmInline
value class GroupId(val value: Long)
