package com.seblful.klas.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.testutil.FakeNotificationPermission
import com.seblful.klas.testutil.TestEnvRule
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidNotifierPrivacyTest {
    @get:Rule
    val env = TestEnvRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val notifier = AndroidNotifier(
        context, FakeNotificationPermission(),
        DisplayLocale(env.settingsRepo, DevicePreferences(listOf("en-US"), listOf("USD"))),
        env.settingsRepo,
    )
    private val lesson = TutoringClass(
        id = 77, studentId = 1, subjectId = 1, durationMinutes = 60,
        start = LocalDate(2026, 6, 1).atTime(17, 0).toInstant(TimeZone.currentSystemDefault()),
    )

    private fun posted(): Notification = shadowOf(manager).getNotification(77)

    @Test
    fun `by default a reminder keeps whose lesson it is off a secure lock screen`() = runTest {
        env.settingsRepo.get()

        notifier.showClassReminder(lesson, "Ava Thompson", "Maths", null)

        val reminder = posted()
        assertEquals(Notification.VISIBILITY_PRIVATE, reminder.visibility)
        val shown = reminder.publicVersion.extras
        assertEquals("Lesson coming up", shown.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Unlock to see details", shown.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("class_reminders.private", reminder.channelId)
        assertEquals(
            "redacted whatever the phone's own lock-screen setting says",
            Notification.VISIBILITY_PRIVATE,
            manager.getNotificationChannel(reminder.channelId).lockscreenVisibility,
        )
    }

    @Test
    fun `an upgraded install's channels are replaced once, keeping what the tutor tuned`() = runTest {
        manager.createNotificationChannel(
            NotificationChannel("class_reminders", "Reminders", NotificationManager.IMPORTANCE_LOW),
        )
        env.settingsRepo.get()

        notifier.ensureChannels()

        assertNull("the channel from before the split is gone", manager.getNotificationChannel("class_reminders"))
        assertEquals(
            "the tutor had quietened reminders, and they stay quiet",
            NotificationManager.IMPORTANCE_LOW,
            manager.getNotificationChannel("class_reminders.private").importance,
        )
    }

    @Test
    fun `choosing details swaps in channels that leave the lock screen to the phone`() = runTest {
        env.settingsRepo.get()
        notifier.ensureChannels()

        env.settingsRepo.update { it.copy(privacy = it.privacy.copy(lockScreenDetails = true)) }
        notifier.ensureChannels()
        notifier.showClassReminder(lesson, "Ava Thompson", "Maths", null)

        assertNull(manager.getNotificationChannel("class_reminders.private"))
        assertEquals("class_reminders.shown", posted().channelId)
        assertFalse(
            manager.getNotificationChannel("class_reminders.shown").lockscreenVisibility ==
                Notification.VISIBILITY_PRIVATE,
        )
    }

    @Test
    fun `before the settings are read, the channels in place are kept rather than swapped`() {
        manager.createNotificationChannel(
            NotificationChannel("class_reminders.shown", "Reminders", NotificationManager.IMPORTANCE_HIGH),
        )

        notifier.ensureChannels()

        assertTrue(manager.getNotificationChannel("class_reminders.shown") != null)
        assertNull(manager.getNotificationChannel("class_reminders.private"))
    }

    @Test
    fun `marking a lesson taught from the tray asks for the unlock first`() = runTest {
        env.settingsRepo.get()

        notifier.showClassReminder(lesson, "Ava Thompson", "Maths", null)

        val taught = NotificationCompat.getAction(posted(), 0)!!
        assertTrue("it bills the lesson", taught.isAuthenticationRequired)
        assertFalse(
            "moving it only opens the app",
            NotificationCompat.getAction(posted(), 1)!!.isAuthenticationRequired,
        )
    }
}
