package com.seblful.klas.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.testutil.FakeNotificationPermission
import com.seblful.klas.testutil.TestEnv
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class AndroidNotifierClockTest {
    @get:Rule
    val env = TestEnv()

    private fun reminder(format: String): String {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, format)
        val notifier = AndroidNotifier(
            context, FakeNotificationPermission(),
            DisplayLocale(env.settingsRepo, DevicePreferences(listOf("en-US"), listOf("USD"))),
            env.settingsRepo,
        )
        val lesson = TutoringClass(
            id = 77, studentId = 1, subjectId = 1, durationMinutes = 60,
            start = LocalDate(2026, 6, 1).atTime(21, 0).toInstant(TimeZone.currentSystemDefault()),
        )
        notifier.showClassReminder(lesson, "Student", "Maths", null)
        val manager = context.getSystemService(NotificationManager::class.java)
        return shadowOf(manager).getNotification(77).extras.getCharSequence(Notification.EXTRA_TEXT).toString()
    }

    @Test
    fun `a reminder uses the phone's twelve-hour clock`() {
        assertTrue(reminder("12").contains("9:00 PM"))
    }

    @Test
    fun `a reminder uses the phone's twenty-four-hour clock`() {
        assertTrue(reminder("24").contains("21:00"))
    }
}
