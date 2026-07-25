package com.seblful.repetit.testutil

import com.seblful.repetit.calendar.CalendarPermission
import com.seblful.repetit.calendar.CalendarSync
import com.seblful.repetit.calendar.Guest
import com.seblful.repetit.data.repository.ClassRepository
import com.seblful.repetit.data.repository.EnrollmentRepository
import com.seblful.repetit.data.repository.GroupRepository
import com.seblful.repetit.data.repository.PaymentRepository
import com.seblful.repetit.data.repository.PriceRepository
import com.seblful.repetit.data.repository.SettingsRepository
import com.seblful.repetit.data.repository.StudentRepository
import com.seblful.repetit.data.repository.SubjectRepository
import com.seblful.repetit.domain.ReminderScheduler
import com.seblful.repetit.domain.model.TutoringClass
import com.seblful.repetit.notifications.Notifier

/** Records reminder-refresh requests so tests can assert side effects fired. */
class RecordingReminderScheduler : ReminderScheduler {
    var refreshCount = 0
        private set

    override fun refreshNow() {
        refreshCount++
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

    override suspend fun isAvailable(): Boolean = available

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

/** Stands in for the device's calendar permission; flip [granted] to drive either branch. */
class FakeCalendarPermission(var granted: Boolean = true) : CalendarPermission {
    override fun isGranted(): Boolean = granted
}

/**
 * A ready-made set of fake DAOs and the real repositories wired on top, for view-model and
 * repository tests. The repositories are the production classes — only the DAOs and the ports
 * (calendar, calendar permission, reminder scheduler) are fakes — so mapping logic and repository
 * side effects are exercised for real.
 */
class TestEnv {
    val subjectDao = FakeSubjectDao()
    val studentDao = FakeStudentDao()
    val groupDao = FakeGroupDao()
    val priceDao = FakePriceDao()
    val classDao = FakeClassDao()
    val settingsDao = FakeSettingsDao()
    val paymentDao = FakePaymentDao()

    val calendar = FakeCalendarSync()
    val calendarPermission = FakeCalendarPermission()
    val reminders = RecordingReminderScheduler()

    val settingsRepo = SettingsRepository(settingsDao)
    val subjectRepo = SubjectRepository(subjectDao, classDao)
    val studentRepo = StudentRepository(studentDao)
    val groupRepo = GroupRepository(groupDao, classDao)
    val priceRepo = PriceRepository(priceDao)
    val enrollmentRepo = EnrollmentRepository(studentDao, priceDao, classDao)
    val paymentRepo = PaymentRepository(paymentDao)
    val classRepo = ClassRepository(
        dao = classDao,
        studentDao = studentDao,
        subjectDao = subjectDao,
        groupDao = groupDao,
        calendarSync = calendar,
        reminderScheduler = reminders,
        settings = settingsRepo,
    )
}
