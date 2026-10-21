package com.seblful.klas.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Payment
import com.seblful.klas.testutil.DeviceTestEnv
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the Android database host; detailed repository rules run in commonTest on both platforms. */
@RunWith(AndroidJUnit4::class)
class DevicePersistenceTest {
    private val env = DeviceTestEnv()

    @After
    fun close() = env.close()

    @Test
    fun bookingBillingAndCalendarIdsPersistThroughTheBundledAndroidDriver() = runTest {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        env.fixtures.withCalendarSync()
        val id = env.fixtures.lesson(subject, listOf(student), env.clock.now()).single()
        env.fixtures.setStatus(id, LessonStatus.COMPLETED)
        env.paymentRepo.restore(Payment(studentId = student, date = env.clock.now(), amount = Money.ofMajor(10)))

        val rows = env.stored()
        assertEquals(LessonStatus.COMPLETED, rows.lessons.single().status)
        assertEquals(Money.ofMajor(30).minor, rows.attendance.single().chargeMinor)
        assertEquals("evt-1", rows.attendance.single().calendarEventId)
        assertEquals(Money.ofMajor(10).minor, rows.payments.single().amountMinor)
    }
}
