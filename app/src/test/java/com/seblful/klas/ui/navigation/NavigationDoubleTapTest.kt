package com.seblful.klas.ui.navigation

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.MainActivity
import com.seblful.klas.R
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.domain.model.Subject
import com.seblful.klas.store.DemoTutor
import com.seblful.klas.store.StoreApp
import com.seblful.klas.store.StoreGraph
import com.seblful.klas.store.StoreScreenshots
import com.seblful.klas.testutil.FakeCalendarPermission
import com.seblful.klas.testutil.FakeCalendarSync
import com.seblful.klas.testutil.RecordingDigestScheduler
import com.seblful.klas.testutil.RecordingReminderScheduler
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * A second tap on a back arrow that lands while its screen is fading out. The leaving screen stays composed,
 * arrow included, for the length of the fade.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = StoreApp::class, qualifiers = "en-rUS-" + StoreScreenshots.PHONE)
class NavigationDoubleTapTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    @Before
    fun seed(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<StoreApp>()
        val graph = createGraphFactory<StoreGraph.Factory>().create(
            context = app,
            clock = DemoTutor.CLOCK,
            firstDayOfWeek = DayOfWeek.MONDAY,
            deviceLocales = listOf(Locale.US),
            reminders = RecordingReminderScheduler(),
            digests = RecordingDigestScheduler(),
            calendar = FakeCalendarSync(),
            calendarPermission = FakeCalendarPermission(granted = true),
        )
        app.graph = graph
        graph.settings.update { it.copy(onboardingComplete = true, language = AppLanguage.ENGLISH) }
        val subject = graph.subjects.upsert(Subject(name = "Maths"))
        graph.enrollment.createStudent(Student(name = STUDENT), mapOf(subject to Money.ofMajor(30)))
    }

    @Test
    fun `a double tap on a card's back arrow closes only the card`() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(text(R.string.nav_people))
            compose.onNode(peopleTab).performClick()
            waitFor(STUDENT)
            compose.onNodeWithText(STUDENT).performClick()
            waitFor(text(R.string.stat_earned))

            // Two taps a few frames apart: the second lands on the card while it is still fading out.
            compose.mainClock.autoAdvance = false
            repeat(2) {
                compose.onNodeWithContentDescription(text(R.string.action_back)).performClick()
                repeat(FRAMES_BETWEEN_TAPS) { compose.mainClock.advanceTimeByFrame() }
            }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()

            compose.onNode(peopleTab and isSelected()).assertExists()
        }
    }

    private val peopleTab get() = hasText(text(R.string.nav_people)) and isSelectable()

    private fun waitFor(value: String) = compose.waitUntil(TIMEOUT_MS) {
        compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty()
    }

    private fun text(res: Int) = ApplicationProvider.getApplicationContext<StoreApp>().getString(res)

    private companion object {
        const val STUDENT = "Double-tap student"
        const val TIMEOUT_MS = 10_000L

        // ~50 ms at 60 Hz: well inside the 180 ms cross-fade.
        const val FRAMES_BETWEEN_TAPS = 3
    }
}
