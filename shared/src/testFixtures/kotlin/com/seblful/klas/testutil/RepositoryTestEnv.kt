package com.seblful.klas.testutil

import com.seblful.klas.calendar.CalendarMirror
import com.seblful.klas.data.backup.BackupManager
import com.seblful.klas.data.backup.BackupRestore
import com.seblful.klas.data.billing.BalanceRepository
import com.seblful.klas.data.billing.PaymentRepository
import com.seblful.klas.data.billing.RateHistoryRepository
import com.seblful.klas.data.homework.HomeworkRepository
import com.seblful.klas.data.lessons.ClassDao
import com.seblful.klas.data.lessons.ClassRepository
import com.seblful.klas.data.lessons.ClassRow
import com.seblful.klas.data.local.AppDatabase
import com.seblful.klas.data.local.DatabaseSnapshot
import com.seblful.klas.data.people.EnrollmentRepository
import com.seblful.klas.data.people.GroupRepository
import com.seblful.klas.data.people.StudentRepository
import com.seblful.klas.data.people.SubjectRepository
import com.seblful.klas.data.settings.GuideRepository
import com.seblful.klas.data.settings.SettingsRepository
import com.seblful.klas.lessons.LessonChanges
import com.seblful.klas.lessons.LessonCompletions
import com.seblful.klas.maintenance.Maintenance
import com.seblful.klas.notifications.DueDigest
import com.seblful.klas.notifications.Notifier
import com.seblful.klas.settings.DevicePreferences

/** Portable repository graph; the platform adapter owns the database lifetime. */
open class RepositoryTestEnv(
    db: AppDatabase,
    val transactor: RecordingTransactor,
    val clock: MutableClock = testClock(),
    preferences: DevicePreferences = DevicePreferences(listOf("ru-BY"), listOf("BYN")),
) {
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
    val reminders = RecordingReminderScheduler()
    val notifier = RecordingNotifier()
    val digestScheduler = RecordingDigestScheduler()

    val settingsRepo = SettingsRepository(settingsDao, preferences)
    val guideRepo = GuideRepository(settingsRepo, db.guideDao())
    val calendarMirror =
        CalendarMirror(
            classDao, attendanceDao, studentDao, subjectDao, groupDao, calendar, settingsRepo, homeworkDao, transactor,
            clock,
        )

    val rateHistoryRepo = RateHistoryRepository(rateHistoryDao, groupDao, priceDao, clock)
    val paymentRepo = PaymentRepository(paymentDao, clock)
    val balanceRepo = BalanceRepository(db.balanceDao())

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

    val lessonChanges = LessonChanges(classRepo, studentRepo, groupRepo)
    val lessonCompletions = LessonCompletions(classRepo, clock)

    val homeworkRepo = HomeworkRepository(homeworkDao, classRepo, clock, calendarMirror)

    /**
     * The real backup manager and restore, on the same database — so an import is the real
     * replace-all, in one real transaction, and a file whose rows break a foreign key is refused by
     * SQLite rather than by a likeness of it. The *failure* paths (an unsupported version, a file that
     * isn't a backup) run through the production code that classifies them.
     */
    val backupManager = BackupManager(transactor = transactor, snapshot = snapshotDao, settings = settingsRepo)

    /**
     * Seeds a starting state through the repositories rather than into the DAOs — see [Fixtures] for
     * why that matters. Lazy, because it closes over this environment.
     */
    val fixtures: Fixtures by lazy { Fixtures(this) }

    /** Where a restore keeps what it replaced, so a test can undo it. */
    val restoreSnapshots = InMemoryRestoreSnapshots()

    val backupRestore = BackupRestore(
        backup = backupManager,
        settings = settingsRepo,
        calendar = calendarMirror,
        reminders = reminders,
        snapshots = restoreSnapshots,
        clock = clock,
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
        balances = balanceRepo,
        settings = settingsRepo,
        notifier = notifier,
        clock = clock,
    )
}
