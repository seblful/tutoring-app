package com.seblful.repetit.data

import com.seblful.repetit.data.local.ClassEntity
import com.seblful.repetit.data.local.GroupEntity
import com.seblful.repetit.data.local.PaymentEntity
import com.seblful.repetit.data.local.PriceEntity
import com.seblful.repetit.data.local.StudentEntity
import com.seblful.repetit.data.local.SubjectEntity
import com.seblful.repetit.domain.model.ClassMode
import com.seblful.repetit.domain.model.ClassStatus
import com.seblful.repetit.domain.model.Group
import com.seblful.repetit.domain.model.Money
import com.seblful.repetit.domain.model.Payment
import com.seblful.repetit.domain.model.Price
import com.seblful.repetit.domain.model.Student
import com.seblful.repetit.domain.model.Subject
import com.seblful.repetit.domain.model.TutoringClass
import kotlinx.serialization.descriptors.elementNames
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
    fun `group round-trips including its own rate`() {
        val g = Group(
            id = 5, name = "11 «А»", note = "вечер", price = Money(2050),
            defaultMode = ClassMode.REMOTE, archived = true,
        )
        assertEquals(g, g.toEntity().toDomain())
        assertEquals("the rate persists in minor units", 2050L, g.toEntity().priceMinor)
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
            groupId = null, mode = ClassMode.REMOTE, calendarEventId = "evt-1",
        )
        assertEquals(c, c.toEntity().toDomain())
    }

    @Test
    fun `class start instant maps to and from epoch millis`() {
        val c = TutoringClass(studentId = 1, subjectId = 1, start = Instant.parse("2026-06-20T10:00:00Z"), durationMinutes = 60)
        assertEquals(Instant.parse("2026-06-20T10:00:00Z").toEpochMilli(), c.toEntity().startMillis)
        assertEquals(c.start, c.toEntity().toDomain().start)
    }

    @Test
    fun `a group class keeps its group id`() {
        val c = TutoringClass(
            id = 11, studentId = 1, subjectId = 2,
            start = Instant.ofEpochMilli(1_700_000_000_000),
            durationMinutes = 60, price = Money.ofMajor(20), groupId = 5,
        )
        assertEquals(c, c.toEntity().toDomain())
        assertEquals(5L, c.toEntity().groupId)
    }

    /**
     * The columns each mapped entity has, listed so that adding one has to be a deliberate act.
     *
     * A mapper that forgets a field still compiles: the mappers construct with named arguments and
     * every non-essential entity column has a default, so a forgotten value is dropped on the way out
     * and silently reset on the way back. The round-trips above would not catch it either — a field
     * nobody sets sits at its default on both sides and compares equal. This test fails instead, in
     * front of whoever adds the column, while they still have the mapper open.
     */
    @Test
    fun `every entity column is accounted for by a mapper`() {
        val columns = mapOf(
            "SubjectEntity" to setOf("id", "name"),
            "StudentEntity" to setOf("id", "name", "phone", "email", "note", "defaultMode", "archived"),
            "GroupEntity" to setOf("id", "name", "note", "priceMinor", "defaultMode", "archived"),
            "PriceEntity" to setOf("studentId", "subjectId", "amountMinor"),
            "PaymentEntity" to setOf("id", "studentId", "dateMillis", "amountMinor"),
            "ClassEntity" to setOf(
                "id", "studentId", "subjectId", "startMillis", "durationMinutes",
                "priceMinor", "status", "chargeMinor", "groupId", "mode", "calendarEventId",
            ),
        )
        val actual = listOf(
            SubjectEntity.serializer(),
            StudentEntity.serializer(),
            GroupEntity.serializer(),
            PriceEntity.serializer(),
            PaymentEntity.serializer(),
            ClassEntity.serializer(),
        ).associate { it.descriptor.serialName.substringAfterLast('.') to it.descriptor.elementNames.toSet() }

        assertEquals(
            "An entity gained or lost a column — map it in Mappers.kt, then list it here.",
            columns,
            actual,
        )
    }
}
