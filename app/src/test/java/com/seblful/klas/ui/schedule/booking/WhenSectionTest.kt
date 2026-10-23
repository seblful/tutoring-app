package com.seblful.klas.ui.schedule.booking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The When section: the day row and the stepper, before and after there is a day and a time. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WhenSectionTest : ClassEditTestFixture() {

    @get:Rule
    val compose = createComposeRule()

    private val day = env.clock.today().plus(3, DateTimeUnit.DAY)

    @Test
    fun `without a day it asks for one`() {
        val vm = vm()
        compose.setContent { KlasTheme { ClassEditScreen(onBack = {}, viewModel = vm) } }

        compose.onNodeWithText("Choose a day").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Set time").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a day with nothing booked says so`() {
        val vm = vm(day = day)
        compose.setContent { KlasTheme { ClassEditScreen(onBack = {}, viewModel = vm) } }
        compose.waitForIdle()

        compose.onNodeWithText("Choose a day").assertDoesNotExist()
        compose.onNodeWithText("No other lessons").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `the day is written out as its lessons and the free time a lesson still fits in`() {
        runBlocking { seedLesson(day, LocalTime(15, 30)) }
        val vm = vm(day = day)
        compose.setContent { KlasTheme { ClassEditScreen(onBack = {}, viewModel = vm) } }
        compose.waitForIdle()

        compose.onNodeWithText("1 lesson · free 9:00 AM–3:30 PM, from 4:30 PM", substring = true).assertExists()
        compose.onNodeWithText("takes 9:00 AM, the first free time", substring = true).assertExists()
    }

    @Test
    fun `the stepper's buttons move the lesson and show where it is`() {
        val vm = vm(day = day)
        compose.setContent { KlasTheme { ClassEditScreen(onBack = {}, viewModel = vm) } }
        compose.waitForIdle()
        vm.startAt(day.atTime(16, 0).toInstant(zone))
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Later, to 4:15 PM").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(LocalTime(16, 15), vm.state.current.form.timeFrom)
        // Robolectric's phone keeps a 12-hour clock.
        compose.onNodeWithTag(TIME_TAG).assertTextEquals("4:15 PM–5:15 PM")
    }
}
