package com.seblful.klas.ui.schedule.booking

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.seblful.klas.domain.model.ClassMode
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.schedule.Repeat
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.current
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The details card: Length, Repeat, Rate and Place, each opened in place. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DetailsSectionTest : ClassEditTestFixture() {

    @get:Rule
    val compose = createComposeRule()

    private val day = env.clock.today().plus(3, DateTimeUnit.DAY)

    private fun open(vm: ClassEditViewModel) {
        compose.setContent { KlasTheme { ClassEditScreen(onBack = {}, viewModel = vm) } }
        compose.waitForIdle()
    }

    private fun tapRow(label: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun press(name: String) {
        compose.onNodeWithContentDescription(name).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test
    fun `folded, Repeat says the booking is once`() {
        open(vm(day = day))

        compose.onNodeWithText("Once").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("One lesson more").assertDoesNotExist()
    }

    @Test
    fun `one more lesson makes the booking a weekly series of two`() {
        val vm = vm(day = day)
        open(vm)

        tapRow("Repeat")
        press("One lesson more")

        val form = vm.state.current.form
        assertEquals(Repeat.WEEKLY, form.repeat)
        assertEquals(2, form.lessons)
    }

    @Test
    fun `opening one row folds the one that was open`() {
        open(vm(day = day))

        tapRow("Repeat")
        tapRow("Length")

        compose.onNodeWithContentDescription("One lesson more").assertDoesNotExist()
        compose.onNodeWithContentDescription("15 minutes longer").assertIsDisplayed()
    }

    @Test
    fun `length steps a quarter of an hour`() {
        val vm = vm(day = day)
        open(vm)
        val before = vm.state.current.form.durationMinutes!!

        tapRow("Length")
        press("15 minutes longer")

        assertEquals(before + 15, vm.state.current.form.durationMinutes)
    }

    @Test
    fun `a tap on the length types it in hours and minutes`() {
        val vm = vm(day = day)
        open(vm)

        tapRow("Length")
        compose.onNodeWithTag(LENGTH_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Hours").performTextReplacement("1")
        compose.onNodeWithText("Minutes").performTextReplacement("20")
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()

        assertEquals(80, vm.state.current.form.durationMinutes)
    }

    @Test
    fun `length offers no chips, only the stepper`() {
        open(vm(day = day))

        tapRow("Length")

        compose.onNodeWithText("45 min").assertDoesNotExist()
        compose.onNodeWithText("1.5 h").assertDoesNotExist()
    }

    @Test
    fun `a tap on the lesson count types it, keeping the booking weekly`() {
        val vm = vm(day = day)
        open(vm)

        tapRow("Repeat")
        compose.onNodeWithTag(LESSONS_TAG).performScrollTo().performClick()
        compose.onNodeWithText("Lessons (up to 52)").performTextReplacement("10")
        compose.onNodeWithText("Done").performClick()
        compose.waitForIdle()

        val form = vm.state.current.form
        assertEquals(Repeat.WEEKLY, form.repeat)
        assertEquals(10, form.lessons)
    }

    @Test
    fun `repeat offers no chips, only the stepper`() {
        open(vm(day = day))

        tapRow("Repeat")

        compose.onNodeWithText("12").assertDoesNotExist()
    }

    @Test
    fun `the rate steps from the student's own`() = runTest(main.dispatcher) {
        val (studentId, _) = seedStudentWithPricedSubject(price = Money.ofMajor(30))
        val vm = vm(day = day, studentId = studentId)
        open(vm)

        tapRow("Rate")
        press("Raise the rate")

        assertEquals(Money.ofMajor(35), vm.state.current.form.price.amount)
    }

    @Test
    fun `price and format fold apart, each opening alone`() {
        val vm = vm(day = day)
        open(vm)

        tapRow("Rate")
        compose.onNodeWithContentDescription("Raise the rate").assertExists()
        compose.onNodeWithText("Remote").assertDoesNotExist()

        tapRow("Place")
        compose.onNodeWithContentDescription("Raise the rate").assertDoesNotExist()
        compose.onNodeWithText("Remote").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(ClassMode.REMOTE, vm.state.current.form.mode)
    }
}
