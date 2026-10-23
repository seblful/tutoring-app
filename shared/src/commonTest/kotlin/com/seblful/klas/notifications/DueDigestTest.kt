package com.seblful.klas.notifications

import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DueDigestTest {

    private val env = TestEnv()

    @AfterTest
    fun close() = env.close()

    private val morning: Instant = Instant.parse("2026-06-15T06:00:00Z")

    private fun at(hour: Int): Instant = LocalDate(2026, 6, 15).atTime(hour, 0).toInstant(env.clock.zone)

    private suspend fun enableAgenda() =
        env.settingsRepo.update { it.copy(dailyAgendaEnabled = true) }

    private suspend fun enableDebts() =
        env.settingsRepo.update { it.copy(debtDigestEnabled = true) }

    private suspend fun enableUnfinished() =
        env.settingsRepo.update { it.copy(unfinishedDigestEnabled = true) }

    private val evening: Instant = at(21)

    @Test
    fun `the agenda names the day's lessons in order - with who and what`() = runTest {
        enableAgenda()
        val algebra = env.fixtures.subject("Алгебра")
        val physics = env.fixtures.subject("Физика")
        val anya = env.fixtures.pricedStudent("Аня Петрова", algebra)
        val boris = env.fixtures.pricedStudent("Боря Иванов", physics)
        // Booked out of order, so the ordering asserted below is the digest's and not the fixture's.
        env.fixtures.lesson(physics, listOf(boris), start = at(14))
        env.fixtures.lesson(algebra, listOf(anya), start = at(11))

        assertTrue(env.dueDigest.postAgenda(morning))

        val agenda = env.notifier.agendas.single()
        assertEquals(
            listOf(at(11) to "Алгебра", at(14) to "Физика"),
            agenda.lessons.map { it.start to it.subject },
        )
        assertEquals(listOf("Аня Петрова", "Боря Иванов"), agenda.lessons.map { it.who })
        assertEquals(LocalDate(2026, 6, 15), agenda.day, "the day a tap on it opens")
    }

    /**
     * A group lesson is **one** entry named by the group, the same rule a reminder is announced by: the
     * tutor is being told about one hour they will teach, and four lines about it would be noise.
     */
    @Test
    fun `a group lesson is one line - named by the group`() = runTest {
        enableAgenda()
        val subject = env.fixtures.subject("Химия")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.pricedStudent(it, subject) }
        val group = env.fixtures.group("11 «А»", subjectId = subject, members = members.toSet())
        env.fixtures.lesson(subject, members, start = at(11), groupId = group)

        env.dueDigest.postAgenda(morning)

        val agenda = env.notifier.agendas.single()
        assertEquals(1, agenda.lessons.size)
        assertEquals("11 «А»", agenda.lessons.single().who)
        assertEquals(1, agenda.summary.lessons, "one lesson of the tutor's")
    }

    /**
     * The summary is the same «4 занятия · 5 ч · 240 BYN» the schedule prints under the day, because it is
     * the same [com.seblful.klas.domain.schedule.DaySummary]. Two arithmetics for one morning would both look right.
     */
    @Test
    fun `the summary is the day's lessons and hours - planned lessons are not money yet`() = runTest {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        env.fixtures.lesson(subject, listOf(anya), start = at(11), minutes = 90)
        env.fixtures.lesson(subject, listOf(anya), start = at(14), minutes = 60)

        env.dueDigest.postAgenda(morning)

        val summary = env.notifier.agendas.single().summary
        assertEquals(2, summary.lessons)
        assertEquals(150, summary.minutes)
        assertEquals(Money.ZERO, summary.money, "only billed amounts are money")
    }

    /**
     * **The whole day, not the rest of it.** The summary is a whole-day figure, so listing only what is
     * still ahead would put two different day-lengths in one notification with nothing saying which was
     * which. An 08:00 lesson is over by the time the digest goes out and still belongs to today.
     */
    @Test
    fun `a lesson earlier than the digest is still part of the day`() = runTest {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(8))

        env.dueDigest.postAgenda(morning)

        assertEquals(1, env.notifier.agendas.single().lessons.size)
    }

    /**
     * A cancelled lesson is counted by the summary, apart, and gets no line of its own — the division the
     * schedule already draws. It gave its hour back, and a row for it among the day's real work would read
     * as one more thing to do.
     */
    @Test
    fun `a cancelled lesson is summarised but not listed`() = runTest {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11))
        env.fixtures.cancelledLesson(subject, listOf(anya), start = at(14))

        env.dueDigest.postAgenda(morning)

        val agenda = env.notifier.agendas.single()
        assertEquals(1, agenda.lessons.size, "only the lesson that is on")
        assertEquals(1, agenda.summary.lessons)
        assertEquals(1, agenda.summary.cancelled)
    }

    @Test
    fun `an empty day is not announced at all`() = runTest {
        enableAgenda()

        assertFalse(env.dueDigest.postAgenda(morning))
        assertTrue(env.notifier.agendas.isEmpty())
    }

    /** A day whose only lesson was called off says nothing: the tutor is the one who cancelled it. */
    @Test
    fun `a day of nothing but cancellations is not announced`() = runTest {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.cancelledLesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postAgenda(morning))
    }

    @Test
    fun `tomorrow's lesson is not today's business`() = runTest {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = Instant.parse("2026-06-16T08:00:00Z"))

        assertFalse(env.dueDigest.postAgenda(morning))
    }

    /**
     * The switch is re-read at the moment of posting, not only when the work was enqueued. The queue
     * outlives the process, so "what is enqueued matches the settings" is a claim about two systems
     * agreeing — the same argument `Reminders.shouldPost` makes for a lesson cancelled since.
     */
    @Test
    fun `nothing is posted while the agenda is switched off`() = runTest {
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postAgenda(morning), "off by default")
        assertTrue(env.notifier.agendas.isEmpty())
    }

    // --- The evening list of unmarked lessons -------------------------------------------------------

    @Test
    fun `the evening digest names the lessons still not marked`() = runTest {
        enableUnfinished()
        val algebra = env.fixtures.subject("Алгебра")
        val physics = env.fixtures.subject("Физика")
        val anya = env.fixtures.pricedStudent("Аня Петрова", algebra)
        val boris = env.fixtures.pricedStudent("Боря Иванов", physics)
        env.fixtures.lesson(physics, listOf(boris), start = at(17))
        env.fixtures.lesson(algebra, listOf(anya), start = at(11))

        assertTrue(env.dueDigest.postUnfinished(evening))

        val digest = env.notifier.unfinished.single()
        assertEquals(
            listOf(at(11) to "Аня Петрова", at(17) to "Боря Иванов"),
            digest.lessons.map { it.start to it.who },
            "in start order, like the morning's",
        )
        // Carried rather than read when tapped: a tap after midnight is still about Monday.
        assertEquals(LocalDate(2026, 6, 15), digest.day)
    }

    /** A lesson the tutor already marked is not something to be asked about at nine in the evening. */
    @Test
    fun `a lesson already marked is not listed`() = runTest {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))
        env.fixtures.cancelledLesson(subject, listOf(anya), start = at(14))
        env.fixtures.lesson(subject, listOf(anya), start = at(17))

        env.dueDigest.postUnfinished(evening)

        assertEquals(listOf(at(17)), env.notifier.unfinished.single().lessons.map { it.start })
    }

    /**
     * **A lesson still running is not unmarked yet.** The digest fires at an hour the tutor chose, and a
     * 20:30 lesson is still being taught at nine — asking about it would be asking them to mark a lesson
     * they are in the middle of. This is the boundary `LessonPhase` exists to state once.
     */
    @Test
    fun `a lesson still in progress is not asked about`() = runTest {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(20), minutes = 90)

        assertFalse(env.dueDigest.postUnfinished(evening), "nothing to say yet")
        assertTrue(env.notifier.unfinished.isEmpty())
    }

    /** A lesson later this evening has not happened, so there is nothing to mark about it either. */
    @Test
    fun `a lesson still to come is not asked about`() = runTest {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(22))

        assertFalse(env.dueDigest.postUnfinished(evening))
    }

    /** A group's hour is one lesson of the tutor's to mark, named by the group — as in the morning. */
    @Test
    fun `a group lesson is one entry - named by the group`() = runTest {
        enableUnfinished()
        val subject = env.fixtures.subject("Химия")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.pricedStudent(it, subject) }
        val group = env.fixtures.group("11 «А»", subjectId = subject, members = members.toSet())
        env.fixtures.lesson(subject, members, start = at(17), groupId = group)

        env.dueDigest.postUnfinished(evening)

        val digest = env.notifier.unfinished.single()
        assertEquals(1, digest.count)
        assertEquals("11 «А»", digest.lessons.single().who)
    }

    /** An evening with everything marked says nothing at all — the ordinary evening. */
    @Test
    fun `an evening with nothing unmarked posts nothing`() = runTest {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postUnfinished(evening))
        assertTrue(env.notifier.unfinished.isEmpty())
    }

    /**
     * **Today's, not the whole backlog.** Yesterday's forgotten lesson is not what closing down this
     * evening is about, and a digest that accumulated them would arrive every night with no way to
     * clear it.
     */
    @Test
    fun `a lesson left unmarked on another day is not dragged in`() = runTest {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11).minus((24 * 60 * 60).seconds))

        assertFalse(env.dueDigest.postUnfinished(evening))
    }

    /** The switch is authoritative at the moment of posting, as it is for the other two. */
    @Test
    fun `nothing is posted while the evening digest is switched off`() = runTest {
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postUnfinished(evening))
        assertTrue(env.notifier.unfinished.isEmpty())
    }

    // --- The weekly debt summary --------------------------------------------------------------------

    @Test
    fun `the debt digest counts who owes and what it comes to`() = runTest {
        enableDebts()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        val boris = env.fixtures.pricedStudent("Боря", subject, rate = Money.ofMajor(40))
        // The rate is booked **per lesson** and only pre-filled from the student's price, so it is stated
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11), rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(boris), start = at(14), rate = Money.ofMajor(40))

        assertTrue(env.dueDigest.postDebts())

        assertEquals(Digests.Debts(students = 2, total = Money.ofMajor(70)), env.notifier.debts.single())
    }

    @Test
    fun `a settled week says nothing`() = runTest {
        enableDebts()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))
        env.paymentRepo.restore(Payment(studentId = anya, date = morning, amount = Money.ofMajor(30)))

        assertFalse(env.dueDigest.postDebts())
        assertTrue(env.notifier.debts.isEmpty())
    }

    @Test
    fun `an archived student's debt is not chased`() = runTest {
        enableDebts()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))
        env.studentRepo.setArchived(env.studentRepo.getById(anya)!!, archived = true)

        assertFalse(env.dueDigest.postDebts())
    }

    /**
     * An overpaid student does not reduce what somebody else owes — `Reports.totalOutstanding` sums the
     * positive balances rather than netting them, which is the difference between "owed to the tutor" and
     * "the tutor's net position".
     */
    @Test
    fun `credit is not netted off against another student's debt`() = runTest {
        enableDebts()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        val boris = env.fixtures.pricedStudent("Боря", subject, rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))
        // Boris has paid for a lesson he has not had.
        env.paymentRepo.restore(Payment(studentId = boris, date = morning, amount = Money.ofMajor(50)))

        env.dueDigest.postDebts()

        assertEquals(Digests.Debts(students = 1, total = Money.ofMajor(30)), env.notifier.debts.single())
    }

    @Test
    fun `nothing is posted while the debt digest is switched off`() = runTest {
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postDebts())
    }
}
