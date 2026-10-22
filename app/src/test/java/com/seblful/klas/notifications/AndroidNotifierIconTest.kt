package com.seblful.klas.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.R
import com.seblful.klas.domain.model.TutoringClass
import com.seblful.klas.format.DisplayLocale
import com.seblful.klas.settings.DevicePreferences
import com.seblful.klas.testutil.FakeNotificationPermission
import com.seblful.klas.testutil.TestEnv
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

@RunWith(RobolectricTestRunner::class)
class AndroidNotifierIconTest {
    @get:Rule
    val env = TestEnv()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `a notification wears the Klas mark in the status bar`() {
        val notifier = AndroidNotifier(
            context, FakeNotificationPermission(),
            DisplayLocale(env.settingsRepo, DevicePreferences(listOf("en-US"), listOf("USD"))),
        )
        val lesson = TutoringClass(
            id = 77, studentId = 1, subjectId = 1, durationMinutes = 60,
            start = LocalDate(2026, 6, 1).atTime(21, 0).toInstant(TimeZone.currentSystemDefault()),
        )
        notifier.showClassReminder(lesson, "Student", "Maths", null)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(R.drawable.ic_stat_klas, shadowOf(manager).getNotification(77).smallIcon.resId)
    }

    @Test
    fun `the Klas mark inflates at the status-bar icon size`() {
        val icon = ContextCompat.getDrawable(context, R.drawable.ic_stat_klas)!!
        val px = (24 * context.resources.displayMetrics.density).toInt()

        assertEquals(px, icon.intrinsicWidth)
        assertEquals(px, icon.intrinsicHeight)
    }
}
