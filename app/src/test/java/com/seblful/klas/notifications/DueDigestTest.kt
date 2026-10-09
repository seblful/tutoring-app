package com.seblful.klas.notifications

import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/**
 * What each digest says when it comes due — driven without WorkManager.
 *
 * The rows are this environment's real ones read by the production repositories, so these are assertions
 * about what a tutor would actually be told rather than about a fixture agreeing with itself. Only the
 * `Notifier` is a double.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DueDigestTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    @get:Rule
    val env = TestEnv()

    /** Monday 15 June 2026. The environment's zone is Europe/Minsk (UTC+3), so this is 09:00 local. */
    private val morning: Instant = Instant.parse("2026-06-15T06:00:00Z")

    private fun at(hour: Int): Instant = Instant.parse("2026-06-15T%02d:00:00Z".format(hour - 3))

    private suspend fun enableAgenda() =
        env.settingsRepo.update { it.copy(dailyAgendaEnabled = true) }

    private suspend fun enableDebts() =
        env.settingsRepo.update { it.copy(debtDigestEnabled = true) }

    private suspend fun enableUnfinished() =
        env.settingsRepo.update { it.copy(unfinishedDigestEnabled = true) }

    /** Monday 15 June 2026, 21:00 in Minsk — the evening digest's default hour. */
    private val evening: Instant = at(21)

    // --- The morning agenda -------------------------------------------------------------------------

    @Test
    fun `the agenda names the day's lessons in order, with who and what`() = runTest(mainRule.dispatcher) {
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
        assertEquals("the day a tap on it opens", LocalDate.of(2026, 6, 15), agenda.day)
    }

    /**
     * A group lesson is **one** entry named by the group, the same rule a reminder is announced by: the
     * tutor is being told about one hour they will teach, and four lines about it would be noise.
     */
    @Test
    fun `a group lesson is one line, named by the group`() = runTest(mainRule.dispatcher) {
        enableAgenda()
        val subject = env.fixtures.subject("Химия")
        val members = listOf("Аня", "Боря", "Вера").map { env.fixtures.pricedStudent(it, subject) }
        val group = env.fixtures.group("11 «А»", subjectId = subject, members = members.toSet())
        env.fixtures.lesson(subject, members, start = at(11), groupId = group)

        env.dueDigest.postAgenda(morning)

        val agenda = env.notifier.agendas.single()
        assertEquals(1, agenda.lessons.size)
        assertEquals("11 «А»", agenda.lessons.single().who)
        assertEquals("one lesson of the tutor's", 1, agenda.summary.lessons)
    }

    /**
     * The summary is the same «4 занятия · 5 ч · 240 BYN» the schedule prints under the day, because it is
     * the same [com.seblful.klas.domain.DaySummary]. Two arithmetics for one morning would both look right.
     */
    @Test
    fun `the summary is what the day comes to`() = runTest(mainRule.dispatcher) {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        env.fixtures.lesson(subject, listOf(anya), start = at(11), minutes = 90)
        env.fixtures.lesson(subject, listOf(anya), start = at(14), minutes = 60)

        env.dueDigest.postAgenda(morning)

        val summary = env.notifier.agendas.single().summary
        assertEquals(2, summary.lessons)
        assertEquals(150, summary.minutes)
        assertEquals("30 an hour, for two and a half hours", Money.ofMajor(75), summary.money)
    }

    /**
     * **The whole day, not the rest of it.** The summary is a whole-day figure, so listing only what is
     * still ahead would put two different day-lengths in one notification with nothing saying which was
     * which. An 08:00 lesson is over by the time the digest goes out and still belongs to today.
     */
    @Test
    fun `a lesson earlier than the digest is still part of the day`() = runTest(mainRule.dispatcher) {
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
    fun `a cancelled lesson is summarised but not listed`() = runTest(mainRule.dispatcher) {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11))
        env.fixtures.cancelledLesson(subject, listOf(anya), start = at(14))

        env.dueDigest.postAgenda(morning)

        val agenda = env.notifier.agendas.single()
        assertEquals("only the lesson that is on", 1, agenda.lessons.size)
        assertEquals(1, agenda.summary.lessons)
        assertEquals(1, agenda.summary.cancelled)
    }

    @Test
    fun `an empty day is not announced at all`() = runTest(mainRule.dispatcher) {
        enableAgenda()

        assertFalse(env.dueDigest.postAgenda(morning))
        assertTrue(env.notifier.agendas.isEmpty())
    }

    /** A day whose only lesson was called off says nothing: the tutor is the one who cancelled it. */
    @Test
    fun `a day of nothing but cancellations is not announced`() = runTest(mainRule.dispatcher) {
        enableAgenda()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.cancelledLesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postAgenda(morning))
    }

    @Test
    fun `tomorrow's lesson is not today's business`() = runTest(mainRule.dispatcher) {
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
    fun `nothing is posted while the agenda is switched off`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11))

        assertFalse("off by default", env.dueDigest.postAgenda(morning))
        assertTrue(env.notifier.agendas.isEmpty())
    }

    // --- The evening list of unmarked lessons -------------------------------------------------------

    @Test
    fun `the evening digest names the lessons still not marked`() = runTest(mainRule.dispatcher) {
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
            "in start order, like the morning's",
            listOf(at(11) to "Аня Петрова", at(17) to "Боря Иванов"),
            digest.lessons.map { it.start to it.who },
        )
        // Carried rather than read when tapped: a tap after midnight is still about Monday.
        assertEquals(LocalDate.of(2026, 6, 15), digest.day)
    }

    /** A lesson the tutor already marked is not something to be asked about at nine in the evening. */
    @Test
    fun `a lesson already marked is not listed`() = runTest(mainRule.dispatcher) {
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
    fun `a lesson still in progress is not asked about`() = runTest(mainRule.dispatcher) {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(20), minutes = 90)

        assertFalse("nothing to say yet", env.dueDigest.postUnfinished(evening))
        assertTrue(env.notifier.unfinished.isEmpty())
    }

    /** A lesson later this evening has not happened, so there is nothing to mark about it either. */
    @Test
    fun `a lesson still to come is not asked about`() = runTest(mainRule.dispatcher) {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(22))

        assertFalse(env.dueDigest.postUnfinished(evening))
    }

    /** A group's hour is one lesson of the tutor's to mark, named by the group — as in the morning. */
    @Test
    fun `a group lesson is one entry, named by the group`() = runTest(mainRule.dispatcher) {
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
    fun `an evening with nothing unmarked posts nothing`() = runTest(mainRule.dispatcher) {
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
    fun `a lesson left unmarked on another day is not dragged in`() = runTest(mainRule.dispatcher) {
        enableUnfinished()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11).minusSeconds(24 * 60 * 60))

        assertFalse(env.dueDigest.postUnfinished(evening))
    }

    /** The switch is authoritative at the moment of posting, as it is for the other two. */
    @Test
    fun `nothing is posted while the evening digest is switched off`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject)
        env.fixtures.lesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postUnfinished(evening))
        assertTrue(env.notifier.unfinished.isEmpty())
    }

    // --- The weekly debt summary --------------------------------------------------------------------

    @Test
    fun `the debt digest counts who owes and what it comes to`() = runTest(mainRule.dispatcher) {
        enableDebts()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        val boris = env.fixtures.pricedStudent("Боря", subject, rate = Money.ofMajor(40))
        // The rate is booked **per lesson** and only pre-filled from the student's price, so it is stated
        // here: what a student owes comes from the rate their lesson was booked at, not from their price now.
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11), rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(boris), start = at(14), rate = Money.ofMajor(40))

        assertTrue(env.dueDigest.postDebts())

        assertEquals(Digests.Debts(students = 2, total = Money.ofMajor(70)), env.notifier.debts.single())
    }

    /** A lesson that was paid for leaves nobody owing, so there is nothing to send. */
    @Test
    fun `a settled week says nothing`() = runTest(mainRule.dispatcher) {
        enableDebts()
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))
        env.paymentRepo.restore(Payment(studentId = anya, date = morning, amount = Money.ofMajor(30)))

        assertFalse(env.dueDigest.postDebts())
        assertTrue(env.notifier.debts.isEmpty())
    }

    /**
     * **Active students only**, by the rule reports follow: archiving is how a tutor says they have stopped
     * teaching somebody, and a weekly reminder about a retired student's balance would make that a decision
     * with a cost. The debt does not vanish — it stops being chased here.
     */
    @Test
    fun `an archived student's debt is not chased`() = runTest(mainRule.dispatcher) {
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
    fun `credit is not netted off against another student's debt`() = runTest(mainRule.dispatcher) {
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
    fun `nothing is posted while the debt digest is switched off`() = runTest(mainRule.dispatcher) {
        val subject = env.fixtures.subject("Алгебра")
        val anya = env.fixtures.pricedStudent("Аня", subject, rate = Money.ofMajor(30))
        env.fixtures.completedLesson(subject, listOf(anya), start = at(11))

        assertFalse(env.dueDigest.postDebts())
    }
}
