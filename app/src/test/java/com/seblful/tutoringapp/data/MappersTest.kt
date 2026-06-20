package com.seblful.tutoringapp.data

import com.seblful.tutoringapp.domain.model.ClassMode
import com.seblful.tutoringapp.domain.model.ClassStatus
import com.seblful.tutoringapp.domain.model.Money
import com.seblful.tutoringapp.domain.model.Payment
import com.seblful.tutoringapp.domain.model.Price
import com.seblful.tutoringapp.domain.model.Student
import com.seblful.tutoringapp.domain.model.Subject
import com.seblful.tutoringapp.domain.model.TutoringClass
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** Entity ⇄ domain mapping must be lossless — a wrong field here silently corrupts persisted data. */
class MappersTest {

    @Test
    fun `subject round-trips`() {
        val s = Subject(id = 7, name = "Математика")
        assertEquals(s, s.toEntity().toDomain())
    }

    @Test
    fun `student round-trips including mode and archived`() {
        val s = Student(
            id = 3, name = "Аня", phone = "+375 29", email = "a@b.co",
            note = "n", defaultMode = ClassMode.REMOTE, archived = true,
        )
        assertEquals(s, s.toEntity().toDomain())
    }

    @Test
    fun `price round-trips and keeps minor units exactly`() {
        val p = Price(studentId = 1, subjectId = 2, amount = Money(4505))
        assertEquals(p, p.toEntity().toDomain())
    }

    @Test
    fun `payment round-trips with millisecond instant precision`() {
        val p = Payment(id = 4, studentId = 1, date = Instant.ofEpochMilli(1_700_000_123_000), amount = Money(150000))
        assertEquals(p, p.toEntity().toDomain())
    }

    @Test
    fun `class round-trips all fields including status, charge, mode and event id`() {
        val c = TutoringClass(
            id = 9, studentId = 1, subjectId = 2,
            start = Instant.ofEpochMilli(1_700_000_000_000),
            durationMinutes = 90, price = Money.ofMajor(30),
            status = ClassStatus.COMPLETED, charge = Money.ofMajor(45),
            recurringGroupId = null, mode = ClassMode.REMOTE, calendarEventId = "evt-1",
        )
        assertEquals(c, c.toEntity().toDomain())
    }

    @Test
    fun `class start instant maps to and from epoch millis`() {
        val c = TutoringClass(studentId = 1, subjectId = 1, start = Instant.parse("2026-06-20T10:00:00Z"), durationMinutes = 60)
        assertEquals(Instant.parse("2026-06-20T10:00:00Z").toEpochMilli(), c.toEntity().startMillis)
        assertEquals(c.start, c.toEntity().toDomain().start)
    }
}
