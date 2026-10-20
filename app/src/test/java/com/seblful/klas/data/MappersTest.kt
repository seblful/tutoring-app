package com.seblful.klas.data

import com.seblful.klas.data.local.AttendanceEntity
import com.seblful.klas.data.local.ClassRow
import com.seblful.klas.data.local.GroupEntity
import com.seblful.klas.data.local.HomeworkEntity
import com.seblful.klas.data.local.LessonEntity
import com.seblful.klas.data.local.PaymentEntity
import com.seblful.klas.data.local.PriceEntity
import com.seblful.klas.data.local.RateChangeEntity
import com.seblful.klas.data.local.StudentEntity
import com.seblful.klas.data.local.SubjectEntity
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Attended
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Group
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.domain.model.LessonStatus
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
import kotlin.time.Instant

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
            at = Instant.fromEpochMilliseconds(1_700_000_123_000),
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
            at = Instant.fromEpochMilliseconds(1_700_000_123_000),
        )
        assertEquals(c, c.toEntity().toDomain())
        assertNull("a group's change names no student", c.toEntity().studentId)
        assertNull("nor a subject", c.toEntity().subjectId)
    }

    /**
     * The two nulls that carry meaning: no previous rate means the history starts here, and no date
     * means the rate predates the log. Both have to survive the round trip as *null*, since a zero
     * or an epoch date would read as a real amount and a real day.
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
        val p = Payment(
            id = 4, studentId = 1, date = Instant.fromEpochMilliseconds(1_700_000_123_000), amount = Money(150000),
        )
        assertEquals(p, p.toEntity().toDomain())
    }

    private fun TutoringClass.roundTrip(): TutoringClass = rejoin(toLessonEntity(), toAttendanceEntity())

    private fun rejoin(lesson: LessonEntity, attendance: AttendanceEntity) = ClassRow(
        id = attendance.id,
        lessonId = attendance.lessonId,
        studentId = attendance.studentId,
        subjectId = lesson.subjectId,
        startMillis = lesson.startMillis,
        durationMinutes = lesson.durationMinutes,
        priceMinor = attendance.priceMinor,
        status = lesson.status,
        state = attendance.state,
        chargeMinor = attendance.chargeMinor,
        groupId = lesson.groupId,
        mode = lesson.mode,
        calendarEventId = attendance.calendarEventId,
    ).toDomain()

    @Test
    fun `class round-trips all fields including status, attendance, charge, mode and event id`() {
        val c = TutoringClass(
            id = 9, lessonId = 4, studentId = 1, subjectId = 2,
            start = Instant.fromEpochMilliseconds(1_700_000_000_000),
            durationMinutes = 90, price = Money.ofMajor(30),
            status = LessonStatus.COMPLETED, attended = Attended.ABSENT_BILLED,
            charge = Money.ofMajor(45),
            groupId = null, mode = ClassMode.REMOTE,
            calendarEventId = "evt-1",
        )
        assertEquals(c, c.roundTrip())
    }

    @Test
    fun `one student's homework round-trips through its row`() {
        val h = Homework(
            id = 3, assignee = Assignee.Student(7), text = "§12, упр. 4–7",
            givenAt = Instant.fromEpochMilliseconds(1_700_000_000_000),
            doneAt = Instant.fromEpochMilliseconds(1_700_086_400_000),
        )

        assertEquals(7L, h.toEntity().studentId)
        assertNull(h.toEntity().groupId)
        assertEquals(h, h.toEntity().toDomain())
    }

    @Test
    fun `a group's homework round-trips through its row, current`() {
        val h = Homework(
            id = 4, assignee = Assignee.Group(5), text = "Задачи", givenAt = Instant.fromEpochMilliseconds(0),
        )

        assertNull(h.toEntity().studentId)
        assertEquals(5L, h.toEntity().groupId)
        assertNull(h.toEntity().doneAtMillis)
        assertEquals(h, h.toEntity().toDomain())
    }

    @Test
    fun `homework for nobody fails the read, naming the row`() {
        val failure = runCatching {
            HomeworkEntity(id = 11, text = "§12", givenAtMillis = 0).toDomain()
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure!!.message!!.contains("11"))
    }

    @Test
    fun `the lesson's facts go to the lesson's row and the student's to their own`() {
        val c = TutoringClass(
            id = 9, lessonId = 4, studentId = 1, subjectId = 2,
            start = Instant.fromEpochMilliseconds(1_700_000_000_000),
            durationMinutes = 90, price = Money.ofMajor(30),
            status = LessonStatus.COMPLETED, attended = Attended.PRESENT,
            charge = Money.ofMajor(45), groupId = 5, mode = ClassMode.REMOTE,
            calendarEventId = "evt-1",
        )

        val lesson = c.toLessonEntity()
        assertEquals(4L, lesson.id)
        assertEquals(LessonStatus.COMPLETED, lesson.status)
        assertEquals(5L, lesson.groupId)
        assertEquals(90, lesson.durationMinutes)

        val attendance = c.toAttendanceEntity()
        assertEquals(9L, attendance.id)
        assertEquals(4L, attendance.lessonId)
        assertEquals(Attended.PRESENT, attendance.state)
        assertEquals(Money.ofMajor(30).minor, attendance.priceMinor)
        assertEquals(Money.ofMajor(45).minor, attendance.chargeMinor)
        assertEquals("evt-1", attendance.calendarEventId)
    }

    @Test
    fun `class start instant maps to and from epoch millis`() {
        val c =
            TutoringClass(studentId = 1, subjectId = 1, start = Instant.parse("2026-06-20T10:00:00Z"), durationMinutes = 60)
        assertEquals(Instant.parse("2026-06-20T10:00:00Z").toEpochMilliseconds(), c.toLessonEntity().startMillis)
        assertEquals(c.start, c.roundTrip().start)
    }

    @Test
    fun `a group class keeps its group id`() {
        val c = TutoringClass(
            id = 11, lessonId = 3, studentId = 1, subjectId = 2,
            start = Instant.fromEpochMilliseconds(1_700_000_000_000),
            durationMinutes = 60, price = Money.ofMajor(20), groupId = 5,
        )
        assertEquals(c, c.roundTrip())
        assertEquals(5L, c.toLessonEntity().groupId)
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
            "LessonEntity" to setOf(
                "id", "subjectId", "startMillis", "durationMinutes", "status", "groupId", "mode",
            ),
            "AttendanceEntity" to setOf(
                "id", "lessonId", "studentId", "state", "priceMinor", "chargeMinor", "calendarEventId",
            ),
        )
        val actual = listOf(
            SubjectEntity.serializer(),
            StudentEntity.serializer(),
            GroupEntity.serializer(),
            PriceEntity.serializer(),
            RateChangeEntity.serializer(),
            PaymentEntity.serializer(),
            LessonEntity.serializer(),
            AttendanceEntity.serializer(),
        ).associate { it.descriptor.serialName.substringAfterLast('.') to it.descriptor.elementNames.toSet() }

        assertEquals(
            "An entity gained or lost a column — map it in Mappers.kt, then list it here.",
            columns,
            actual,
        )
    }
}
