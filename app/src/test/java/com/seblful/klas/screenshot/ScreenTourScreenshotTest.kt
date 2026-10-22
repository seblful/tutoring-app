package com.seblful.klas.screenshot

import androidx.annotation.StringRes
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.TimeZone

/**
 * Every screen [FormFactorScreenshotTest] does not reach — the lesson sheet, the forms, More and each
 * Settings page, onboarding — in light, dark, large text and Russian, on a phone. The safety net under the
 * design-system migration: a screen that changes by accident shows here. Found by their words in the
 * language in force, so one tour runs in both.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = StoreApp::class)
class ScreenTourScreenshotTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val timeZone = TimeZone.getDefault()
    private val locale = Locale.getDefault()

    @After
    fun restoreDefaults() {
        TimeZone.setDefault(timeZone)
        Locale.setDefault(locale)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
    fun light() = tour("light", DemoTutor.english)

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
    fun dark() = tour("dark", DemoTutor.english, ThemeChoice.DARK)

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi", fontScale = 1.5f)
    fun largeText() = tour("large-text", DemoTutor.english)

    @Test
    @Config(qualifiers = "ru-rRU-w412dp-h915dp-port-mdpi")
    fun russian() = tour("ru", DemoTutor.russian)

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h915dp-port-mdpi")
    fun onboarding() = firstRun("light", DemoTutor.english)

    @Test
    @Config(qualifiers = "ru-rRU-w412dp-h915dp-port-mdpi")
    fun onboardingRussian() = firstRun("ru", DemoTutor.russian)

    private fun graph(tutor: DemoTutor, seeded: Boolean, theme: ThemeChoice? = null) {
        TimeZone.setDefault(TimeZone.getTimeZone(DemoTutor.ZONE.id))
        Locale.setDefault(tutor.locale)
        val app = ApplicationProvider.getApplicationContext<StoreApp>()
        val graph = createGraphFactory<StoreGraph.Factory>().create(
            context = app,
            clock = DemoTutor.CLOCK,
            firstDayOfWeek = tutor.firstDayOfWeek,
            deviceLocales = listOf(tutor.locale),
            reminders = RecordingReminderScheduler(),
            digests = RecordingDigestScheduler(),
            calendar = FakeCalendarSync(),
            calendarPermission = FakeCalendarPermission(granted = true),
        )
        app.graph = graph
        if (seeded) runBlocking { graph.seeder.seed(tutor) }
        theme?.let { mode -> runBlocking { graph.settings.update { it.copy(themeMode = mode) } } }
    }

    private fun tour(variant: String, tutor: DemoTutor, theme: ThemeChoice? = null) {
        graph(tutor, seeded = true, theme = theme)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Today is left alone, as in [FormFactorScreenshotTest]: its «now» line moves on its own.
            tab(R.string.nav_schedule)
            waitFor(string(R.string.class_status_in_progress))
            click(R.string.schedule_view_month)
            compose.onAllNodesWithText(LATER_DAY).filterToOne(isSelectable()).performClick()
            settle()
            compose.onAllNodes(hasText(string(R.string.class_status_scheduled), substring = true) and hasClickAction())
                .onFirst().performClick()
            capture(variant, "lesson-sheet")
            back(scenario)

            describedClick(R.string.schedule_add_class)
            capture(variant, "booking")
            back(scenario)

            tab(R.string.nav_people)
            waitFor(tutor.debtor.name)
            describedClick(R.string.students_add)
            capture(variant, "student-new")
            back(scenario)

            click(R.string.groups_title)
            describedClick(R.string.groups_add)
            capture(variant, "group-new")
            back(scenario)

            tab(R.string.nav_homework)
            waitFor(tutor.group.homework)
            click(R.string.homework_side_archive)
            capture(variant, "homework-done")

            describedClick(R.string.settings_title)
            capture(variant, "settings")
            for ((label, name) in SettingsPages + AppPages) {
                click(label)
                capture(variant, name)
                back(scenario)
            }
        }
    }

    private fun firstRun(variant: String, tutor: DemoTutor) {
        graph(tutor, seeded = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(string(R.string.onboarding_start))
            capture(variant, "onboarding-welcome")
            click(R.string.onboarding_start)
            capture(variant, "onboarding-locale")
            click(R.string.onboarding_continue)
            capture(variant, "onboarding-subjects")
        }
    }

    private fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun click(@StringRes label: Int) {
        compose.onAllNodesWithText(string(label)).filter(hasClickAction()).onFirst().performClick()
        settle()
    }

    /** An icon button, found by what it says to a screen reader. */
    private fun describedClick(@StringRes label: Int) {
        compose.onAllNodes(hasContentDescription(string(label)) and hasClickAction()).onFirst().performClick()
        settle()
    }

    private fun tab(@StringRes label: Int) {
        compose.onAllNodesWithText(string(label)).filterToOne(isSelectable()).performClick()
        settle()
    }

    private fun capture(variant: String, screen: String) {
        settle()
        captureScreenRoboImage(
            "src/test/screenshots/Tour.$variant.$screen.png",
            RoborazziOptions(compareOptions = CompareOptions(resultValidator = ThresholdValidator(ChangeThreshold))),
        )
    }

    private fun waitFor(text: String) {
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
    }

    private fun string(@StringRes id: Int): String = ApplicationProvider.getApplicationContext<StoreApp>().getString(id)

    private companion object {
        const val TIMEOUT_MS = 60_000L
        const val SETTLE_MS = 2_000L

        /** A day of the demo tutor's month after its pinned today, the 14th. */
        const val LATER_DAY = "20"

        /** The pages «Ещё» held when it was a tab, now rows of Settings' index. */
        val AppPages = listOf(
            R.string.more_subjects to "subjects",
            R.string.more_backup to "backup",
            R.string.about_title to "about",
        )

        val SettingsPages = listOf(
            R.string.settings_appearance_title to "settings-appearance",
            R.string.settings_notifications_title to "settings-notifications",
            R.string.settings_lessons_title to "settings-lessons",
            R.string.settings_calendar_title to "settings-calendar",
            R.string.settings_privacy_title to "settings-privacy",
            R.string.diagnostics_title to "diagnostics",
        )
    }
}
