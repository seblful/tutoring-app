package com.seblful.tutoringapp.testutil

import com.seblful.tutoringapp.calendar.CalendarSync
import com.seblful.tutoringapp.data.repository.ClassRepository
import com.seblful.tutoringapp.data.repository.PaymentRepository
import com.seblful.tutoringapp.data.repository.PriceRepository
import com.seblful.tutoringapp.data.repository.SettingsRepository
import com.seblful.tutoringapp.data.repository.StudentRepository
import com.seblful.tutoringapp.data.repository.SubjectRepository
import com.seblful.tutoringapp.domain.ReminderScheduler
import com.seblful.tutoringapp.domain.model.TutoringClass

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
    val upserts = mutableListOf<TutoringClass>()
    val deletes = mutableListOf<String>()
    private var nextEventId = 1

    override suspend fun isAvailable(): Boolean = available

    override suspend fun upsert(
        klass: TutoringClass,
        title: String,
        guestEmail: String?,
        reminderLeadMinutes: Int,
    ): String? {
        if (!available) return null
        upserts += klass
        return klass.calendarEventId ?: "evt-${nextEventId++}"
    }

    override suspend fun delete(eventId: String) {
        deletes += eventId
    }
}

/**
 * A ready-made set of fake DAOs and the real repositories wired on top, for view-model and
 * repository tests. The repositories are the production classes — only the DAOs and the two ports
 * (calendar, reminder scheduler) are fakes — so mapping logic and repository side effects are
 * exercised for real.
 */
class TestEnv {
    val subjectDao = FakeSubjectDao()
    val studentDao = FakeStudentDao()
    val priceDao = FakePriceDao()
    val classDao = FakeClassDao()
    val settingsDao = FakeSettingsDao()
    val paymentDao = FakePaymentDao()

    val calendar = FakeCalendarSync()
    val reminders = RecordingReminderScheduler()

    val settingsRepo = SettingsRepository(settingsDao)
    val subjectRepo = SubjectRepository(subjectDao, classDao)
    val studentRepo = StudentRepository(studentDao)
    val priceRepo = PriceRepository(priceDao)
    val paymentRepo = PaymentRepository(paymentDao)
    val classRepo = ClassRepository(
        dao = classDao,
        studentDao = studentDao,
        subjectDao = subjectDao,
        calendarSync = calendar,
        reminderScheduler = reminders,
        settings = settingsRepo,
    )
}
