package com.seblful.klas.domain.lesson

import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassSession
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SessionMoveJoinersTest {
    @Test
    fun `future replanning applies equally to new members and a wholly replaced roster`() {
        val now = Instant.parse("2026-06-01T12:00:00Z")
        listOf(LessonStatus.COMPLETED, LessonStatus.CANCELLED).forEach { status ->
            val original = TutoringClass(
                id = 1, lessonId = 1, studentId = 1, subjectId = 1,
                start = now.minusSeconds(7200), durationMinutes = 60,
                price = Money.ofMajor(30), status = status, groupId = 7,
            )
            listOf(setOf(1L, 2L), setOf(2L)).forEach { roster ->
                val edit = SessionRules.lessonEdit(
                    ClassSession(7, listOf(original)), SessionEdit(1, 60, ClassMode.IN_PERSON, original.price),
                    newStart = now.plusSeconds(86400), now = now, roster = roster,
                )
                val joining = ClassRules.rebill(edit.joining.single())

                assertEquals(edit.lesson.status, joining.status)
                assertEquals(LessonStatus.SCHEDULED, joining.status)
                assertEquals(Attended.EXPECTED, joining.attended)
                assertEquals(Money.ZERO, joining.charge)
                assertEquals(CalendarSyncPolicy.Action.UPSERT, CalendarSyncPolicy.actionFor(joining.status))
            }
        }
    }
}
