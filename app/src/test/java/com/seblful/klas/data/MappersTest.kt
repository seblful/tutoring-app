package com.seblful.klas.data

import com.seblful.klas.data.local.ClassEntity
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.ClassStatus
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.model.Price
import com.seblful.klas.domain.model.RateChange
import com.seblful.klas.domain.model.RateOwner
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.domain.model.TutoringClass
import kotlinx.serialization.descriptors.elementNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `a student's rate change round-trips, owner included`() {
        val c = RateChange(
            id = 3,
            owner = RateOwner.StudentSubject(studentId = 1, subjectId = 2),
            from = Money(2000),
            to = Money(2550),
            at = Instant.ofEpochMilli(1_700_000_123_000),
        )
        assertEquals(c, c.toEntity().toDomain())
        assertNull("a student's change names no group", c.toEntity().groupId)
    }

    @Test
    fun `a group's rate change round-trips, owner included`() {
        val c = RateChange(
            id = 4,
            owner = RateOwner.GroupRate(groupId = 7),
            from = Money(2000),
            to = Money(2500),
            at = Instant.ofEpochMilli(1_700_000_123_000),
        )
        assertEquals(c, c.toEntity().toDomain())
        assertNull("a group's change names no student", c.toEntity().studentId)
        assertNull("nor a subject", c.toEntity().subjectId)
    }

    /**
     * The two nulls that carry meaning: no previous rate means the history starts here, and no date
     * means the rate predates the log (the rows the v6 → v7 migration seeds). Both have to survive the
     * round trip as *null*, since a zero or an epoch date would read as a real amount and a real day.
     */
    @Test
    fun `an initial rate change keeps its missing previous amount and missing date`() {
        val c = RateChange(
            id = 5,
            owner = RateOwner.StudentSubject(studentId = 1, subjectId = 2),
            from = null,
            to = Money(3000),
            at = null,
        )
        assertEquals(c, c.toEntity().toDomain())
        assertNull(c.toEntity().fromMinor)
        assertNull(c.toEntity().atMillis)
    }

    /**
     * A row belonging to neither a student's subject nor a group cannot be produced by the mapper, so
     * it can only arrive from a hand-edited database or a doctored backup. Attributing it to some
     * default owner would put a stranger's price in a tutor's history; failing is the honest option.
     */
    @Test
    fun `a rate change belonging to nobody is rejected rather than guessed`() {
        val orphan = RateChangeEntity(id = 9, toMinor = 1000)
        val thrown = runCatching { orphan.toDomain() }.exceptionOrNull()
        assertTrue("expected an error, got $thrown", thrown is IllegalStateException)
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
            "GroupEntity" to setOf("id", "name", "note", "priceMinor", "subjectId", "defaultMode", "archived"),
            "PriceEntity" to setOf("studentId", "subjectId", "amountMinor"),
            "RateChangeEntity" to setOf(
                "id", "studentId", "subjectId", "groupId", "fromMinor", "toMinor", "atMillis",
            ),
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
            RateChangeEntity.serializer(),
            PaymentEntity.serializer(),
            ClassEntity.serializer(),
        ).associate { it.descriptor.serialName.substringAfterLast('.') to it.descriptor.elementNames.toSet() }

        assertEquals(
            "An entity gained or lost a column — map it in Mappers.kt, then list it here.",
            columns,
            actual,
        )
    }

    // --- Reading text that is not one of the enum's names ------------------------------------------
    // The mappers run inside the Flows every screen collects, so what they do with an unreadable
    // column decides whether that screen draws at all. The two answers differ by whether the column
    // carries money — see the KDoc on `modeOf`/`statusOf`.

    /**
     * A mode is a label and a calendar location, so an unreadable one costs a wrong word rather than a
     * screen. The row is still read, which is what matters: the rest of the lesson is intact.
     */
    @Test
    fun `an unreadable mode falls back to in person rather than failing the read`() {
        val student = StudentEntity(1, "Иван", defaultMode = "TELEPATHY").toDomain()
        assertEquals(ClassMode.IN_PERSON, student.defaultMode)
        assertEquals("the rest of the row survives", "Иван", student.name)

        val group = GroupEntity(1, "11 «А»", defaultMode = "HYBRID").toDomain()
        assertEquals(ClassMode.IN_PERSON, group.defaultMode)

        val klass = ClassEntity(
            1, 1, 1, 1_900_000_000_000, 60,
            status = ClassStatus.SCHEDULED.name, mode = "HYBRID",
        ).toDomain()
        assertEquals(ClassMode.IN_PERSON, klass.mode)
    }

    /**
     * A status is what makes a lesson count as income, so there is no fallback that doesn't move money:
     * `SCHEDULED` un-bills a lesson that earned, `COMPLETED` invents one that didn't. It fails instead,
     * naming the row — and the backup import now refuses such a file outright, so this should be
     * unreachable from anything but a hand-edited database.
     */
    @Test
    fun `an unreadable status fails the read, naming the class`() {
        val entity = ClassEntity(42, 1, 1, 1_900_000_000_000, 60, status = "MISSED")

        val failure = runCatching { entity.toDomain() }.exceptionOrNull()

        assertTrue("it fails rather than guessing", failure is IllegalStateException)
        assertTrue("and says which row: ${failure?.message}", failure!!.message!!.contains("42"))
        assertTrue(failure.message!!.contains("MISSED"))
    }
}
