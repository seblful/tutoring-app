package com.seblful.klas.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = StoreApp::class)
class WidgetPreviewsTest {

    private val app = ApplicationProvider.getApplicationContext<StoreApp>()
    private val russian = Locale.forLanguageTag("ru")

    @Before
    fun graph() {
        app.graph = createGraphFactory<StoreGraph.Factory>().create(
            context = app,
            clock = DemoTutor.CLOCK,
            firstDayOfWeek = DemoTutor.russian.firstDayOfWeek,
            deviceLocales = listOf(russian),
            reminders = RecordingReminderScheduler(),
            digests = RecordingDigestScheduler(),
            calendar = FakeCalendarSync(),
            calendarPermission = FakeCalendarPermission(granted = true),
        )
        // Robolectric reads no widget providers from the manifest.
        listOf(TodayWidgetReceiver::class.java, QuickActionsWidgetReceiver::class.java).forEach { receiver ->
            shadowOf(AppWidgetManager.getInstance(app)).addInstalledProvider(
                AppWidgetProviderInfo().apply {
                    provider = ComponentName(app, receiver)
                    widgetCategory = AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
                },
            )
        }
    }

    @Test
    fun `the picker gets the widgets' own layouts, once per install and language`() = runTest {
        WidgetPreviews(app).publish(russian)

        assertNotNull(preview())
        assertEquals("ru", published()?.substringAfter('/'))

        WidgetPreviews(app).publish(Locale.ENGLISH)
        assertEquals("en", published()?.substringAfter('/'))
    }

    @Test
    @Config(sdk = [34])
    fun `before Android 15 the picker keeps the app icon`() = runTest {
        WidgetPreviews(app).publish(russian)

        assertNull(published())
    }

    // Glance publishes for every category at once, and Robolectric keeps one preview per exact mask,
    // whichever widget set it.
    private fun preview() = AppWidgetManager.getInstance(app).getWidgetPreview(
        ComponentName(app, TodayWidgetReceiver::class.java),
        Process.myUserHandle(),
        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or
            AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD or
            AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX,
    )

    private fun published(): String? =
        app.getSharedPreferences("widget-previews", Context.MODE_PRIVATE).getString("published-for", null)
}
