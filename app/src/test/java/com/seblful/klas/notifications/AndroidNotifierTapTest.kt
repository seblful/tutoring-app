package com.seblful.klas.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.domain.reminders.Digests
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.notifications.TrayNavigation.Destination
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.testutil.FakeNotificationPermission
import com.seblful.klas.testutil.TestEnv
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Android draws [TrayNotice]s: each tap and Open button is the notice's own destination, as a link. */
@RunWith(RobolectricTestRunner::class)
class AndroidNotifierTapTest {
    @get:Rule
    val env = TestEnv()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidNotifier(
        context, FakeNotificationPermission(),
        DisplayLocale(env.settingsRepo, DevicePreferences(listOf("en-US"), listOf("USD"))),
        env.settingsRepo,
    )

    private fun PendingIntent.arrival(): Destination? {
        val intent = shadowOf(this).savedIntent
        return TrayNavigation.destinationFor(intent.action, classId = 0, link = intent.dataString)
    }

    @Test
    fun `a reminder's tap shows the lesson and its Move opens the lesson's form`() = runTest {
        env.settingsRepo.get()
        val lesson = TutoringClass(
            id = 77, studentId = 1, subjectId = 1, durationMinutes = 60,
            start = LocalDate(2026, 6, 1).atTime(17, 0).toInstant(TimeZone.currentSystemDefault()),
        )

        notifier.showClassReminder(lesson, "Ava Thompson", "Maths", null)

        val posted = shadowOf(manager).getNotification(77)
        assertEquals(Destination.LessonOnSchedule(77), posted.contentIntent.arrival())
        assertEquals(Destination.Lesson(77), NotificationCompat.getAction(posted, 1)!!.actionIntent!!.arrival())
    }

    @Test
    fun `the debt digest opens the debts`() = runTest {
        env.settingsRepo.get()

        notifier.showDebts(Digests.Debts(students = 1, total = Money(500)), Currency.USD)

        val posted = shadowOf(manager).allNotifications.single { it.channelId.startsWith("debt_digest") }
        assertEquals(Destination.Debts, posted.contentIntent.arrival())
    }
}
