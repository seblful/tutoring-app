package com.seblful.klas.testutil

import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.calendar.CalendarSync
import com.seblful.klas.calendar.Guest
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.local.Transactor
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
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentStore

/** Records reminder-refresh requests and armed plans so tests can assert side effects fired. */
class RecordingReminderScheduler : ReminderScheduler {
    var refreshCount = 0
        private set

    /** Every plan handed over, newest last. [armed] is the one currently in force. */
    val schedules = mutableListOf<List<Reminders.Plan>>()

    /** The reminders currently armed — the last plan scheduled, or empty if none ever was. */
    val armed: List<Reminders.Plan> get() = schedules.lastOrNull().orEmpty()

    override fun refreshNow() {
        refreshCount++
    }

    override fun schedule(plans: List<Reminders.Plan>) {
        schedules += plans
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

    val calls = mutableListOf<Upsert>()
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

    override suspend fun delete(eventId: String) {
        deletes += eventId
    }
}

/** Records posted reminders so tests can assert what a due reminder announced, and to whom. */
class RecordingNotifier : Notifier {
    data class Posted(val klass: TutoringClass, val whoName: String, val subjectName: String)

    val posted = mutableListOf<Posted>()
    var channelsEnsured = 0
        private set

    override fun ensureChannel() {
        channelsEnsured++
    }

    override fun showClassReminder(klass: TutoringClass, whoName: String, subjectName: String) {
        posted += Posted(klass, whoName, subjectName)
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
    override val required: Array<String> get() = arrayOf("READ_CALENDAR", "WRITE_CALENDAR")
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
 * A ready-made set of fake DAOs and the real repositories wired on top, for view-model and
 * repository tests. The repositories are the production classes — only the DAOs and the ports
 * (calendar, calendar permission, reminder scheduler) are fakes — so mapping logic and repository
 * side effects are exercised for real.
 */
class TestEnv(val transactor: RecordingTransactor = RecordingTransactor()) {
    val subjectDao = FakeSubjectDao()
    val studentDao = FakeStudentDao()
    val groupDao = FakeGroupDao()
    val priceDao = FakePriceDao()
    val rateHistoryDao = FakeRateHistoryDao()
    val classDao = FakeClassDao()
    val settingsDao = FakeSettingsDao()
    val paymentDao = FakePaymentDao()

    val calendar = FakeCalendarSync()
    val calendarPermission = FakeCalendarPermission()
    val reminders = RecordingReminderScheduler()

    val settingsRepo = SettingsRepository(settingsDao)
    val subjectRepo = SubjectRepository(subjectDao, classDao, groupDao)
    val studentRepo = StudentRepository(studentDao)
    val rateHistoryRepo = RateHistoryRepository(rateHistoryDao)
    val groupRepo = GroupRepository(groupDao, classDao, studentDao, rateHistoryRepo, transactor)
    val enrollmentRepo = EnrollmentRepository(studentDao, priceDao, classDao, rateHistoryRepo, transactor)
    val paymentRepo = PaymentRepository(paymentDao)
    val classRepo = ClassRepository(
        dao = classDao,
        studentDao = studentDao,
        subjectDao = subjectDao,
        groupDao = groupDao,
        calendarSync = calendar,
        reminderScheduler = reminders,
        settings = settingsRepo,
        transactor = transactor,
    )

    /**
     * The real backup manager and restore, on the same fake DAOs. Reachable in a JVM test because
     * both take the [com.seblful.klas.data.local.Transactor] port rather than an `AppDatabase` — which
     * is what lets the *failure* paths (an unsupported version, a file that isn't a backup) be
     * exercised through the production code that classifies them.
     */
    val backupManager = BackupManager(
        transactor = transactor,
        subjects = subjectDao,
        students = studentDao,
        groups = groupDao,
        prices = priceDao,
        rateHistory = rateHistoryDao,
        classes = classDao,
        payments = paymentDao,
    )

    val backupRestore = BackupRestore(
        backup = backupManager,
        settings = settingsRepo,
        classes = classRepo,
        reminders = reminders,
    )
}
