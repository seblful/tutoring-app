package com.seblful.klas.testutil

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.calendar.CalendarPermission
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.billing.RateHistoryRepository
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.ClassRow
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.maintenance.Maintenance
import com.seblful.klas.notifications.AlarmClock
import com.seblful.klas.notifications.ArmedReminders
import com.seblful.klas.notifications.DueDigest
import com.seblful.klas.notifications.ExactAlarmPermission
import com.seblful.klas.notifications.NotificationPermission
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.storage.DocumentRef
import com.seblful.klas.storage.DocumentSharing
import com.seblful.klas.storage.DocumentStore
import kotlinx.datetime.TimeZone
import org.junit.rules.TestRule
import java.io.Closeable
import kotlin.time.Instant

class FakeCalendarPermission(var granted: Boolean = true) : CalendarPermission {
    override fun isGranted(): Boolean = granted
}

class FakeNotificationPermission(
    var granted: Boolean = true,
    override var canAsk: Boolean = true,
) : NotificationPermission {
    override fun isGranted(): Boolean = granted
}

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

class FakeArmedReminders(private var ids: Set<Long> = emptySet()) : ArmedReminders {
    override fun read(): Set<Long> = ids
    override fun write(ids: Set<Long>) {
        this.ids = ids
    }
}

class FakeExactAlarmPermission(var granted: Boolean = true) : ExactAlarmPermission {
    override fun isGranted(): Boolean = granted
}

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
 * The phone the fixtures assume: Russian, in Belarus — the app's original tutor.
 *
 * Which matters to exactly two answers, both of them about a settings row nobody has written yet: what
 * `AppLanguage.SYSTEM` resolves to, and which currency an amount is captioned with. A test about either
 * hands [TestEnv] a different list; every other test wants "the app as it shipped", which is this.
 */
val RussianMinskPhone: List<java.util.Locale> = listOf(java.util.Locale.forLanguageTag("ru-BY"))

class TestEnv(
    val database: TestDatabase = TestDatabase(),
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
        Instant.parse("2026-06-01T12:00:00Z"),
        TimeZone.of("Europe/Minsk"),
    ),
) : TestRule by database, Closeable by database {
    private val db = database.db

    /** Every write the repositories make is a real transaction, counted. */
    val transactor: RecordingTransactor = database.transactor

    val subjectDao = db.subjectDao()
    val studentDao = db.studentDao()
    val groupDao = db.groupDao()
    val priceDao = db.priceDao()
    val rateHistoryDao = db.rateHistoryDao()

    /** The two lesson tables, and the join over them. */
    val classDao = db.classDao()
    val lessonDao = db.lessonDao()
    val attendanceDao = db.attendanceDao()
    val settingsDao = db.settingsDao()
    val paymentDao = db.paymentDao()
    val homeworkDao = db.homeworkDao()

    /** The same rows again, whole tables at a time — what backup and restore see. */
    val snapshotDao = db.snapshotDao()

    /**
     * Every row stored, table by table — what a test asserting on the database itself reads, rather than
     * one of the questions the DAOs are written to answer.
     */
    suspend fun stored(): DatabaseSnapshot = snapshotDao.read()

    /**
     * Every class (the `attendance JOIN lessons` rows), in id order — the order they were booked in.
     * `ClassDao.getAll` promises none, and SQLite's is whichever table the planner scans first.
     */
    suspend fun classes(): List<ClassRow> = classDao.getAll().sortedBy { it.id }

    val calendar = FakeCalendarSync()
    val calendarPermission = FakeCalendarPermission()
    val notificationPermission = FakeNotificationPermission()
    val exactAlarmPermission = FakeExactAlarmPermission()
    val reminders = RecordingReminderScheduler()
    val notifier = RecordingNotifier()
    val digestScheduler = RecordingDigestScheduler()

    val settingsRepo = SettingsRepository(settingsDao, deviceLocales.toDevicePreferences())
    val calendarMirror =
        CalendarMirror(classDao, attendanceDao, studentDao, subjectDao, groupDao, calendar, settingsRepo, homeworkDao)

    val rateHistoryRepo = RateHistoryRepository(rateHistoryDao, groupDao, priceDao, clock)
    val paymentRepo = PaymentRepository(paymentDao, clock)

    val classRepo = ClassRepository(
        dao = classDao,
        lessons = lessonDao,
        attendance = attendanceDao,
        calendar = calendarMirror,
        reminderScheduler = reminders,
        transactor = transactor,
    )

    val groupRepo = GroupRepository(groupDao, classRepo, studentDao, rateHistoryRepo, transactor, calendarMirror)
    val subjectRepo = SubjectRepository(subjectDao, classRepo, groupRepo, studentDao, calendarMirror, transactor)
    val enrollmentRepo = EnrollmentRepository(studentDao, priceDao, classRepo, rateHistoryRepo, transactor)

    val studentRepo = StudentRepository(studentDao, classRepo, calendarMirror)

    val homeworkRepo = HomeworkRepository(homeworkDao, classRepo, clock, calendarMirror)

    /**
     * The real backup manager and restore, on the same database — so an import is the real
     * replace-all, in one real transaction, and a file whose rows break a foreign key is refused by
     * SQLite rather than by a likeness of it. The *failure* paths (an unsupported version, a file that
     * isn't a backup) run through the production code that classifies them.
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
