package com.seblful.klas.testutil

import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.local.Transactor
import com.seblful.klas.data.repository.CalendarMirror
import com.seblful.klas.data.repository.ClassRepository
import com.seblful.klas.data.repository.EnrollmentRepository
import com.seblful.klas.data.repository.GroupRepository
import com.seblful.klas.data.repository.PaymentRepository
import com.seblful.klas.data.repository.RateHistoryRepository
import com.seblful.klas.data.repository.SettingsRepository
import com.seblful.klas.data.repository.StudentRepository
import com.seblful.klas.data.repository.SubjectRepository
import com.seblful.klas.domain.ReminderScheduler
import com.seblful.klas.domain.Reminders
import com.seblful.klas.domain.DigestScheduler
import com.seblful.klas.domain.Digests
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.notifications.DueDigest
import com.seblful.klas.notifications.AlarmClock
import com.seblful.klas.notifications.ArmedReminders
import com.seblful.klas.notifications.ExactAlarmPermission
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentSharing
import com.seblful.klas.storage.DocumentStore
import com.seblful.klas.work.Maintenance

/** Records reminder-refresh requests and armed plans so tests can assert side effects fired. */
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

/**
 * Records the digest plans arranged, so a test can assert *when* each digest was set for — and, just as
 * importantly, that a switched-off one was arranged for nothing at all.
 */
class RecordingDigestScheduler : DigestScheduler {

    /** Every plan handed over, newest last. */
    val plans = mutableListOf<Digests.Plan>()

    /** The plan currently in force — the last one scheduled, or nothing arranged if none ever was. */
    val planned: Digests.Plan get() = plans.lastOrNull() ?: Digests.Plan(null, null, null)

    override suspend fun schedule(plan: Digests.Plan) {
        plans += plan
    }
}

/**
 * Records calendar writes. [available] controls whether [upsert] returns an id (mimicking a
 * writable calendar) or null (no calendar / permission), so tests can drive both branches.
 */
class FakeCalendarSync(var available: Boolean = true) : CalendarSync {
    /** One recorded mirror, with every argument, so a test can assert the guest as well as the class. */
    data class Upsert(
        val klass: TutoringClass,
        val title: String,
        val guest: Guest?,
        val reminderLeadMinutes: Int,
    )

    /** One recorded refresh: the lesson as it now stands, and the title it is being retitled to. */
    data class Refresh(val klass: TutoringClass, val title: String)

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
    ): String? {
        if (!available) return null
        calls += Upsert(klass, title, guest, reminderLeadMinutes)
        return klass.calendarEventId ?: "evt-${nextEventId++}"
    }

    /**
     * A refresh is recorded apart from [calls], because it is a different claim about the world: an
     * upsert says an event exists *because the app made one*, a refresh says one was brought in line
     * if it happened to be there. A test asserting "booking mirrored once" must not be satisfied by a
     * completed lesson's refresh, and vice versa.
     */
    override suspend fun refresh(klass: TutoringClass, title: String) {
        if (!available) return
        // The real adapter no-ops without an event id, and a fake that recorded the call anyway would
        // let a test claim the calendar was touched where the device would have done nothing.
        if (klass.calendarEventId == null) return
        refreshes += Refresh(klass, title)
    }

    override suspend fun delete(eventId: String) {
        deletes += eventId
    }
}

/** Records everything posted to the tray, so tests can assert what the app announced and to whom. */
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

    /**
     * What each unattended upkeep pass reported billing. Empty is the assertion that matters most often:
     * a pass the tutor asked for reports through the screen instead, and must post nothing here.
     */
    data class AutoCompleted(val lessons: Int, val billed: Money)

    val autoCompleted = mutableListOf<AutoCompleted>()

    override fun showAutoCompleted(lessons: Int, billed: Money, currency: Currency) {
        autoCompleted += AutoCompleted(lessons, billed)
    }
}

/**
 * Runs transactions inline — the fake DAOs are plain in-memory maps, so there is no real atomicity
 * to reproduce. It counts them instead, which is what tests actually need to assert: that a lesson's
 * rows are written in **one** transaction rather than several.
 *
 * [failAt] makes that numbered transaction throw after the block has run, standing in for a commit
 * that doesn't land; it is a `var` so a test can seed its data over successful transactions and then
 * arm the failure for the one it is actually about. The fake DAOs cannot roll back, so a test uses it
 * to assert that the *calendar* and the reminder scheduler are left alone when a write fails — the
 * observable half of atomicity that doesn't need a database to check.
 */
class RecordingTransactor(var failAt: Int? = null) : Transactor {
    var count = 0
        private set

    /** How deeply nested the transaction currently in progress is; 0 when none is. */
    var depth = 0
        private set

    /** The greatest nesting reached, so a test can assert one flat transaction rather than several. */
    var maxDepth = 0
        private set

    override suspend fun <T> transaction(block: suspend () -> T): T {
        count++
        depth++
        maxDepth = maxOf(maxDepth, depth)
        try {
            val result = block()
            if (count == failAt) error("Transaction $count failed to commit")
            return result
        } finally {
            depth--
        }
    }
}

/** Stands in for the device's calendar permission; flip [granted] to drive either branch. */
class FakeCalendarPermission(var granted: Boolean = true) : CalendarPermission {
    override fun isGranted(): Boolean = granted
    override val required: List<String> get() = listOf("READ_CALENDAR", "WRITE_CALENDAR")
}

/**
 * Stands in for whether a reminder would actually be shown. [granted] false is a fresh Android 13+
 * install — the setting on, the permission never asked for — which is the case the settings row exists
 * to stop being silent about.
 *
 * @param required what the platform would let the app ask for. **Empty is a real state**, not a
 *   degenerate one: notifications switched off for the whole app in system settings leave nothing to
 *   request, and the app has to say so rather than show a dialog that closes itself.
 */
class FakeNotificationPermission(
    var granted: Boolean = true,
    override var required: List<String> = listOf("POST_NOTIFICATIONS"),
) : NotificationPermission {
    override fun isGranted(): Boolean = granted
}

/**
 * Records what was armed and what was cancelled, in order — the two halves of the reminder replacement
 * discipline, which is what `ReminderAlarms` exists to get right and what `AlarmManager` cannot be asked
 * about. [exact] is which of the port's two set methods was taken.
 */
class RecordingAlarmClock : AlarmClock {

    data class Armed(val classId: Long, val atMillis: Long, val exact: Boolean)

    val armed = mutableListOf<Armed>()
    val cancelled = mutableListOf<Long>()

    override fun setExact(classId: Long, atMillis: Long) {
        armed += Armed(classId, atMillis, exact = true)
    }

    override fun setInexact(classId: Long, atMillis: Long) {
        armed += Armed(classId, atMillis, exact = false)
    }

    override fun cancel(classId: Long) {
        cancelled += classId
    }

    /** Forgets the calls so far, for a test asserting what a *second* arming did. */
    fun clear() {
        armed.clear()
        cancelled.clear()
    }
}

/**
 * The armed-id record, in memory.
 *
 * Deliberately its own object rather than a field on [RecordingAlarmClock]: the point of the split is that
 * the record outlives the process, so a test hands the *same* store to a second `ReminderAlarms` to stand in
 * for a restart.
 */
class FakeArmedReminders(private var ids: Set<Long> = emptySet()) : ArmedReminders {
    override fun read(): Set<Long> = ids
    override fun write(ids: Set<Long>) {
        this.ids = ids
    }
}

/**
 * Stands in for whether an alarm may be set for an exact instant.
 *
 * [granted] false is the default state of a fresh Android 14+ install: reminders are shown, but through
 * an inexact alarm Doze may hold until its next window, so the hour's warning can land during the lesson.
 * Defaults to true here because that is what every version below Android 12 reports and what most tests
 * are not about.
 */
class FakeExactAlarmPermission(var granted: Boolean = true) : ExactAlarmPermission {
    override fun isGranted(): Boolean = granted
}

/**
 * An in-memory [DocumentStore]. [docs] is the "file system": seed it to drive a read, inspect it
 * after a write. Set [failing] to make both operations throw, which is how the error branch of an
 * export or import is reached without a device.
 */
class FakeDocumentStore(var failing: Boolean = false) : DocumentStore {
    val docs = mutableMapOf<String, String>()

    override suspend fun read(ref: DocumentRef): String {
        if (failing) error("Cannot open input stream")
        return docs[ref.value] ?: error("No such document: ${ref.value}")
    }

    override suspend fun write(ref: DocumentRef, text: String) {
        if (failing) error("Cannot open output stream")
        docs[ref.value] = text
    }
}

/**
 * Records what was handed to the share sheet.
 *
 * The real adapter writes a file and starts a chooser, neither of which exists on the JVM — but what the
 * app *decides* does: the filename, the type that picks who is offered it, the subject line and the
 * bytes. That is the whole of what a test can be wrong about here.
 */
class RecordingDocumentSharing(var failing: Boolean = false) : DocumentSharing {
    data class Shared(
        val filename: String,
        val mimeType: String,
        val subject: String,
        val text: String,
    )

    val shared = mutableListOf<Shared>()

    override suspend fun share(filename: String, mimeType: String, subject: String, text: String) {
        if (failing) error("Nothing can receive this")
        shared += Shared(filename, mimeType, subject, text)
    }
}

/**
 * A ready-made set of fake DAOs and the real repositories wired on top, for view-model and
 * repository tests. The repositories are the production classes — only the DAOs and the ports
 * (calendar, calendar permission, reminder scheduler) are fakes — so mapping logic and repository
 * side effects are exercised for real.
 */
/**
 * The phone the fixtures assume: Russian, in Belarus — the app's original tutor.
 *
 * Which matters to exactly two answers, both of them about a settings row nobody has written yet: what
 * `AppLanguage.SYSTEM` resolves to, and which currency an amount is captioned with. A test about either
 * hands [TestEnv] a different list; every other test wants "the app as it shipped", which is this.
 */
val RussianMinskPhone: List<java.util.Locale> = listOf(java.util.Locale.forLanguageTag("ru-BY"))

class TestEnv(
    val transactor: RecordingTransactor = RecordingTransactor(),
    val deviceLocales: List<java.util.Locale> = RussianMinskPhone,
    /**
     * The environment's clock, pinned — what the engine's defaulted `now`s read in a test, so the shipped
     * default path is the one being exercised. Move it when a test needs time to pass.
     *
     * A parameter rather than only a property, because it is now the clock a **payment's instant** is
     * derived from (`PaymentRepository`): a test about which hour a payment lands at has to be able to say
     * what "today" is before the repositories are built, rather than handing its own clock to a view model
     * and leaving the writer on a different one.
     */
    val clock: MutableClock = MutableClock(
        java.time.Instant.parse("2026-06-01T12:00:00Z"),
        java.time.ZoneId.of("Europe/Minsk"),
    ),
) {
    val subjectDao = FakeSubjectDao()
    val studentDao = FakeStudentDao()
    val groupDao = FakeGroupDao()
    val priceDao = FakePriceDao()
    val rateHistoryDao = FakeRateHistoryDao()

    /** The two lesson tables, and the `classes` view over them. */
    val classStore = FakeClassStore()
    val classDao = FakeClassDao(classStore)
    val lessonDao = FakeLessonDao(classStore)
    val attendanceDao = FakeAttendanceDao(classStore)
    val settingsDao = FakeSettingsDao()
    val paymentDao = FakePaymentDao()

    /** The same rows again, whole tables at a time — what backup and restore see. */
    val snapshotDao = FakeSnapshotDao(
        subjectDao, studentDao, groupDao, priceDao, rateHistoryDao, classStore, paymentDao,
    )

    val calendar = FakeCalendarSync()
    val calendarPermission = FakeCalendarPermission()
    val notificationPermission = FakeNotificationPermission()
    val exactAlarmPermission = FakeExactAlarmPermission()
    val reminders = RecordingReminderScheduler()
    val notifier = RecordingNotifier()
    val digestScheduler = RecordingDigestScheduler()

    val settingsRepo = SettingsRepository(settingsDao, deviceLocales)
    val calendarMirror =
        CalendarMirror(classDao, attendanceDao, studentDao, subjectDao, groupDao, calendar, settingsRepo)

    val rateHistoryRepo = RateHistoryRepository(rateHistoryDao, groupDao, priceDao, clock)
    val paymentRepo = PaymentRepository(paymentDao, clock)

    // Declared before the four repositories below it, all of which ask it whether anything is still
    // taught somewhere rather than counting rows in the class table themselves.
    val classRepo = ClassRepository(
        dao = classDao,
        lessons = lessonDao,
        attendance = attendanceDao,
        calendar = calendarMirror,
        reminderScheduler = reminders,
        transactor = transactor,
    )

    val groupRepo = GroupRepository(groupDao, classRepo, studentDao, rateHistoryRepo, transactor, calendarMirror)
    val subjectRepo = SubjectRepository(subjectDao, classRepo, groupRepo, calendarMirror)
    val enrollmentRepo = EnrollmentRepository(studentDao, priceDao, classRepo, rateHistoryRepo, transactor)

    // Archiving a student has to call off their planned lessons, so this reaches for the class
    // repository rather than restating any of what a class change owes.
    val studentRepo = StudentRepository(studentDao, classRepo, calendarMirror)

    /**
     * The real backup manager and restore, on the same fake DAOs. Reachable in a JVM test because
     * both take the [com.seblful.klas.data.local.Transactor] port rather than an `AppDatabase` — which
     * is what lets the *failure* paths (an unsupported version, a file that isn't a backup) be
     * exercised through the production code that classifies them.
     */
    val backupManager = BackupManager(transactor = transactor, snapshot = snapshotDao)

    /**
     * Seeds a starting state through the repositories rather than into the DAOs — see [Fixtures] for
     * why that matters. Lazy, because it closes over this environment.
     */
    val fixtures: Fixtures by lazy { Fixtures(this) }

    val backupRestore = BackupRestore(
        backup = backupManager,
        settings = settingsRepo,
        calendar = calendarMirror,
        reminders = reminders,
    )

    /**
     * The real upkeep pass. Not a fake, because the settings screen's auto-completion switch runs it
     * for real and reports what it settled — a double would let a test agree about a number this
     * environment made up.
     */
    val maintenance = Maintenance(
        classes = classRepo,
        settings = settingsRepo,
        scheduler = reminders,
        digests = digestScheduler,
        notifier = notifier,
        clock = clock,
    )

    /**
     * Everything the two digests do when they come due. The real class, like [maintenance] — only the
     * `Notifier` under it is a double, so what a digest *says* is this environment's rows read by the
     * production code rather than a fixture agreeing with itself.
     */
    val dueDigest = DueDigest(
        classes = classRepo,
        students = studentRepo,
        subjects = subjectRepo,
        groups = groupRepo,
        payments = paymentRepo,
        settings = settingsRepo,
        notifier = notifier,
        clock = clock,
    )
}
