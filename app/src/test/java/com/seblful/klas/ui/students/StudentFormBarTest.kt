package com.seblful.klas.ui.students

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.Student
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class StudentFormBarTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @get:Rule(order = 0)
    val env = TestEnv()

    private fun vm() = StudentFormViewModel(
        androidx.lifecycle.SavedStateHandle(),
        env.subjectRepo,
        env.enrollmentRepo,
        env.studentRepo,
        env.settingsRepo,
        MainScope(),
        Dispatchers.Unconfined,
    )

    @Test
    fun `a new student's bar names the add and sums up whom once nothing is missing`() {
        runBlocking { env.fixtures.subject("Maths") }
        compose.setContent { KlasTheme { StudentFormScreen(onBack = {}, viewModel = vm()) } }

        compose.onNode(hasText("Add student") and hasClickAction()).assertIsNotEnabled()
        compose.onNodeWithText("Still needed").assertExists()

        compose.onNodeWithText("Name").performTextInput("Sofia")
        compose.onNodeWithText("Rate per hour").performTextInput("25")

        compose.onNode(hasText("Add student") and hasClickAction()).assertIsEnabled()
        compose.onNode(hasText("Sofia") and !hasSetTextAction()).assertExists()
        compose.onNode(hasText("Maths \u20C525", substring = true) and hasText("h · In person", substring = true))
            .assertExists()
    }

    @Test
    fun `a student opened to correct shows their rate and saves from the bar`() {
        val id = runBlocking {
            val maths = env.fixtures.subject("Maths")
            env.enrollmentRepo.createStudent(Student(name = "Liam"), mapOf(maths to Money.ofMajor(25)))
        }
        compose.setContent { KlasTheme { StudentFormScreen(onBack = {}, viewModel = vm(), studentId = id) } }

        compose.onNodeWithText("Edit student").assertExists()
        compose.onNode(hasText("Save") and hasClickAction()).assertIsEnabled().performClick()
    }
}
