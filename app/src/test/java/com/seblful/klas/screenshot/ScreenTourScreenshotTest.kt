package com.seblful.klas.screenshot

import androidx.compose.ui.test.filter
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions
import com.github.takahirom.roborazzi.ThresholdValidator
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.seblful.klas.MainActivity
import com.seblful.klas.domain.model.ThemeChoice
import com.seblful.klas.format.formatDay
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.StringResource
import com.seblful.klas.text.Strings
import com.seblful.klas.ui.common.SKELETON_TAG
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
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
            tab(Strings.nav_schedule)
            waitFor(string(Strings.class_status_in_progress))
            click(Strings.schedule_view_month)
            compose.onAllNodesWithText(LATER_DAY).filterToOne(isSelectable()).performClick()
            laterDayShown()
            settle()
            compose.onAllNodes(hasText(string(Strings.class_status_scheduled), substring = true) and hasClickAction())
                .onFirst().performClick()
            capture(variant, "lesson-sheet")
            back(scenario)

            describedClick(Strings.class_book)
            capture(variant, "booking")
            back(scenario)

            tab(Strings.nav_people)
            waitFor(tutor.debtor.name)
            describedClick(Strings.fab_new_student)
            capture(variant, "student-new")
            back(scenario)

            click(Strings.groups_title)
            describedClick(Strings.fab_new_group)
            capture(variant, "group-new")
            back(scenario)

            tab(Strings.nav_homework)
            waitFor(tutor.group.homework)
            click(Strings.homework_side_archive)
            capture(variant, "homework-done")

            describedClick(Strings.settings_title)
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
            waitFor(string(Strings.onboarding_start))
            capture(variant, "onboarding-welcome")
            click(Strings.onboarding_start)
            capture(variant, "onboarding-locale")
            click(Strings.onboarding_continue)
            capture(variant, "onboarding-subjects")
        }
    }

    private fun back(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun click(label: StringResource) {
        val node = compose.onAllNodesWithText(string(label)).filter(hasClickAction()).onFirst()
        // Below the fold at large text a tap lands on nothing; a node outside any scroll has nothing to scroll.
        runCatching { node.performScrollTo() }
        node.performClick()
        settle()
    }

    /** An icon button, found by what it says to a screen reader. */
    private fun describedClick(label: StringResource) {
        compose.onAllNodes(hasContentDescription(string(label)) and hasClickAction()).onFirst().performClick()
        settle()
    }

    private fun tab(label: StringResource) {
        compose.onAllNodes((hasText(string(label)) or hasContentDescription(string(label))) and isSelectable())
            .onFirst().performClick()
        settle()
    }

    private fun capture(variant: String, screen: String) {
        settle()
        // Room reads on its own threads, which neither the clock nor idling waits for.
        compose.waitUntil(TIMEOUT_MS) {
            compose.onAllNodesWithTag(SKELETON_TAG, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
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

    /** The later day's own header: it is the new week's read, which the test clock does not wait for. */
    private fun laterDayShown() = waitFor(
        LocalDate(DemoTutor.NOW.year, DemoTutor.NOW.month, LATER_DAY.toInt()).formatDay(),
    )

    private fun settle() {
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        finishRipples()
        compose.waitForIdle()
    }

    private fun string(id: StringResource): String =
        ApplicationProvider.getApplicationContext<StoreApp>().texts().string(id)

    private companion object {
        const val TIMEOUT_MS = 60_000L
        const val SETTLE_MS = 2_000L

        /** A day of the demo tutor's month after its pinned today, the 14th. */
        const val LATER_DAY = "20"

        /** The pages «Ещё» held when it was a tab, now rows of Settings' index. */
        val AppPages = listOf(
            Strings.more_subjects to "subjects",
            Strings.more_backup to "backup",
            Strings.about_title to "about",
        )

        val SettingsPages = listOf(
            Strings.settings_appearance_title to "settings-appearance",
            Strings.settings_notifications_title to "settings-notifications",
            Strings.settings_lessons_title to "settings-lessons",
            Strings.settings_calendar_title to "settings-calendar",
            Strings.settings_privacy_title to "settings-privacy",
            Strings.diagnostics_title to "diagnostics",
        )
    }
}
