package com.seblful.klas.queries.lessons

import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.format.ObservableWallClock
import com.seblful.klas.testutil.MutableClock
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.testutil.store
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TodayLessonsQueryTest {
    private val env = TestEnv()
    private val store = env.store()

    @AfterTest
    fun close() = env.close()

    private fun today(hour: Int): Instant =
        Instant.parse("2026-06-01T" + hour.toString().padStart(2, '0') + ":00:00+03:00")

    @Test
    fun `one named row per lesson in time order including a whole group`() = runTest {
        val subject = env.fixtures.subject("Maths")
        val ann = env.fixtures.pricedStudent("Ann", subject)
        val bob = env.fixtures.pricedStudent("Bob", subject)
        val group = env.fixtures.group("Group", subject, setOf(ann, bob))
        val grouped = env.fixtures.lesson(subject, listOf(ann, bob), today(16), groupId = group)
        val individual = env.fixtures.lesson(subject, listOf(ann), today(9)).single()

        val rows = store.todayLessons.observe().first()

        assertEquals(listOf(individual, grouped.first()), rows.map { it.session.first.id })
        assertEquals(listOf("Ann", "Group"), rows.map { it.who })
        assertEquals(listOf("Maths", "Maths"), rows.map { it.subject })
        assertEquals(2, rows.last().session.classes.size)
    }

    @Test
    fun `taught lessons stay while cancelled lessons and other days leave`() = runTest {
        val subject = env.fixtures.subject()
        val ann = env.fixtures.pricedStudent(subjectId = subject)
        val taught = env.fixtures.lesson(subject, listOf(ann), today(9)).single()
        val cancelled = env.fixtures.lesson(subject, listOf(ann), today(11)).single()
        env.fixtures.lesson(subject, listOf(ann), Instant.parse("2026-06-02T09:00:00+03:00"))
        env.fixtures.lesson(subject, listOf(ann), Instant.parse("2026-05-31T23:00:00+03:00"))
        store.classes.setStatus(store.classes.getSessionFor(taught)!!, LessonStatus.COMPLETED)
        store.classes.setStatus(store.classes.getSessionFor(cancelled)!!, LessonStatus.CANCELLED)

        val rows = store.todayLessons.observe().first()

        assertEquals(listOf(taught), rows.map { it.session.first.id })
        assertEquals(LessonStatus.COMPLETED, rows.single().session.status)
    }

    @Test
    fun `names follow student subject and group edits`() = runTest {
        val subject = env.fixtures.subject("Maths")
        val ann = env.fixtures.pricedStudent("Ann", subject)
        val bob = env.fixtures.pricedStudent("Bob", subject)
        val group = env.fixtures.group("Group", subject, setOf(ann, bob))
        env.fixtures.lesson(subject, listOf(ann), today(9))
        env.fixtures.lesson(subject, listOf(ann, bob), today(16), groupId = group)
        var rows = emptyList<NamedLesson>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.todayLessons.observe().collect { rows = it }
        }
        runCurrent()

        store.students.upsert(Student(id = ann, name = "Anna"))
        store.subjects.save(Subject(id = subject, name = "Geometry"))
        store.groups.save(store.groups.getById(group)!!.copy(name = "Geometry group"), setOf(ann, bob))
        runCurrent()

        assertEquals(listOf("Anna", "Geometry group"), rows.map { it.who })
        assertEquals(listOf("Geometry", "Geometry"), rows.map { it.subject })
    }

    @Test
    fun `the observed day rolls over at local midnight`() = runTest {
        val clock = MutableClock(Instant.parse("2026-06-01T20:59:59Z"))
        val query = env.store(hostClock = clock).todayLessons
        val subject = env.fixtures.subject()
        val ann = env.fixtures.pricedStudent(subjectId = subject)
        val first = env.fixtures.lesson(subject, listOf(ann), today(9)).single()
        val second = env.fixtures.lesson(subject, listOf(ann), Instant.parse("2026-06-02T09:00:00+03:00")).single()
        var rows = emptyList<NamedLesson>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { query.observe().collect { rows = it } }
        runCurrent()
        assertEquals(listOf(first), rows.map { it.session.first.id })

        clock.advanceBy(1_000)
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(second), rows.map { it.session.first.id })
    }

    @Test
    fun `a zone change requeries bounds even when the date stays the same`() = runTest {
        val clock = ChangingClock()
        val query = env.store(hostClock = clock).todayLessons
        val subject = env.fixtures.subject()
        val ann = env.fixtures.pricedStudent(subjectId = subject)
        val id = env.fixtures.lesson(subject, listOf(ann), Instant.parse("2026-05-31T22:30:00Z")).single()
        var rows = emptyList<NamedLesson>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { query.observe().collect { rows = it } }
        runCurrent()
        assertEquals(listOf(id), rows.map { it.session.first.id })

        clock.changeZone(TimeZone.UTC)
        runCurrent()

        assertTrue(rows.isEmpty())
    }

    private class ChangingClock : ObservableWallClock {
        override var zone: TimeZone = TimeZone.of("Europe/Minsk")
            private set
        override val zoneChanges = MutableStateFlow(zone)
        override fun now(): Instant = Instant.parse("2026-06-01T12:00:00Z")

        fun changeZone(zone: TimeZone) {
            this.zone = zone
            zoneChanges.value = zone
        }
    }
}
