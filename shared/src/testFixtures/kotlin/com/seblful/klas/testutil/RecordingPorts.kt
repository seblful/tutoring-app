package com.seblful.klas.testutil

import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.domain.reminders.ReminderScheduler
import com.seblful.klas.domain.reminders.Reminders
import com.seblful.klas.notifications.Notifier
import kotlinx.datetime.LocalDate

class RecordingReminderScheduler : ReminderScheduler {
    var refreshCount = 0
        private set

    /** Every plan handed over, newest last. [armed] is the one currently in force. */
    val schedules = mutableListOf<List<Reminders.Plan>>()

    /** The reminders currently armed — the last plan scheduled, or empty if none ever was. */
    val armed: List<Reminders.Plan> get() = schedules.lastOrNull().orEmpty()

    /** How many times the standing upkeep pass was asked for — `KlasApp` does this once, on start. */
    var periodicCount = 0
        private set

    override fun refreshNow() {
        refreshCount++
    }

    override fun ensurePeriodicMaintenance() {
        periodicCount++
    }

    override fun schedule(plans: List<Reminders.Plan>) {
        schedules += plans
    }
}

class RecordingDigestScheduler : DigestScheduler {

    /** Every plan handed over, newest last. */
    val plans = mutableListOf<Digests.Plan>()

    /** The plan currently in force — the last one scheduled, or nothing arranged if none ever was. */
    val planned: Digests.Plan get() = plans.lastOrNull() ?: Digests.Plan(null, null, null)

    override suspend fun schedule(plan: Digests.Plan) {
        plans += plan
    }
}

class FakeCalendarSync(var available: Boolean = true) : CalendarSync {
    data class Upsert(
        val klass: TutoringClass,
        val title: String,
        val guest: Guest?,
        val reminderLeadMinutes: Int,
        val description: String,
    )

    data class Refresh(val klass: TutoringClass, val title: String, val description: String)

    val calls = mutableListOf<Upsert>()
    val refreshes = mutableListOf<Refresh>()
    val deletes = mutableListOf<String>()
    private var nextEventId = 1

    /** The classes mirrored so far — the part most tests assert on. Clear [calls] to reset. */
    val upserts: List<TutoringClass> get() = calls.map { it.klass }

    /** The guest carried by the most recent mirror, if any. */
    val lastGuest: Guest? get() = calls.lastOrNull()?.guest

    override suspend fun upsert(
        klass: TutoringClass,
        title: String,
        guest: Guest?,
        reminderLeadMinutes: Int,
        description: String,
    ): String? {
        if (!available) return null
        calls += Upsert(klass, title, guest, reminderLeadMinutes, description)
        return klass.calendarEventId ?: "evt-${nextEventId++}"
    }

    /**
     * A refresh is recorded apart from [calls], because it is a different claim about the world: an
     * upsert says an event exists *because the app made one*, a refresh says one was brought in line
     * if it happened to be there. A test asserting "booking mirrored once" must not be satisfied by a
     * completed lesson's refresh, and vice versa.
     */
    override suspend fun refresh(klass: TutoringClass, title: String, description: String) {
        if (!available) return
        if (klass.calendarEventId == null) return
        refreshes += Refresh(klass, title, description)
    }

    override suspend fun delete(eventId: String) {
        deletes += eventId
    }
}

class RecordingNotifier : Notifier {
    data class Posted(
        val klass: TutoringClass,
        val whoName: String,
        val subjectName: String,
        val homeworkToCheck: String? = null,
    )

    val posted = mutableListOf<Posted>()
    var channelsEnsured = 0
        private set

    override fun ensureChannels() {
        channelsEnsured++
    }

    override fun showClassReminder(
        klass: TutoringClass,
        whoName: String,
        subjectName: String,
        homeworkToCheck: String?,
    ) {
        posted += Posted(klass, whoName, subjectName, homeworkToCheck)
    }

    /** Which reminders were taken back out of the tray, in the order they were. */
    val cancelled = mutableListOf<Long>()

    override fun cancelClassReminder(classId: Long) {
        cancelled += classId
    }

    /** The agendas posted, in order — what the morning digest said about each day it announced. */
    val agendas = mutableListOf<Digests.Agenda>()

    override fun showAgenda(agenda: Digests.Agenda, currency: Currency) {
        agendas += agenda
    }

    /** The evening lists of unmarked lessons posted. */
    val unfinished = mutableListOf<Digests.Unfinished>()

    override fun showUnfinished(unfinished: Digests.Unfinished) {
        this.unfinished += unfinished
    }

    /** The weekly debt summaries posted. */
    val debts = mutableListOf<Digests.Debts>()

    override fun showDebts(debts: Digests.Debts, currency: Currency) {
        this.debts += debts
    }

    data class AutoCompleted(val lessons: Int, val billed: Money, val day: LocalDate)

    val autoCompleted = mutableListOf<AutoCompleted>()

    override fun showAutoCompleted(lessons: Int, billed: Money, day: LocalDate, currency: Currency) {
        autoCompleted += AutoCompleted(lessons, billed, day)
    }
}

class RecordingTransactor(private val delegate: Transactor, var failAt: Int? = null) : Transactor {
    var count = 0
        private set

    /** How deeply nested the transaction currently in progress is; 0 when none is. */
    var depth = 0
        private set

    /** The greatest nesting reached, so a test can assert one flat transaction rather than several. */
    var maxDepth = 0
        private set

    override suspend fun <T> transaction(block: suspend () -> T): T {
        val number = ++count
        depth++
        maxDepth = maxOf(maxDepth, depth)
        try {
            return delegate.transaction {
                block().also { if (number == failAt) error("Transaction $number failed to commit") }
            }
        } finally {
            depth--
        }
    }
}
