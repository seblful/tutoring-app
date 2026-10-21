package com.seblful.klas.ui.schedule.booking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Clock

/** The When section before and after a day is picked. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WhenSectionTest : ClassEditTestFixture() {

    @get:Rule
    val compose = createComposeRule()

    private val hint = "Pick a day to see when it's free"

    @Test
    fun `without a day it asks for one where the timeline will be`() {
        val vm = vm()
        compose.setContent { KlasTheme { ClassEditScreen(onBack = {}, viewModel = vm) } }

        compose.onNodeWithText(hint).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `with a day the timeline takes the hint's place`() {
        val vm = vm(day = Clock.System.todayIn(zone).plus(1, DateTimeUnit.DAY))
        compose.setContent { KlasTheme { ClassEditScreen(onBack = {}, viewModel = vm) } }
        compose.waitForIdle()

        compose.onNodeWithText(hint).assertDoesNotExist()
        compose.onNodeWithTag(TIMELINE_TAG).performScrollTo().assertIsDisplayed()
    }
}
