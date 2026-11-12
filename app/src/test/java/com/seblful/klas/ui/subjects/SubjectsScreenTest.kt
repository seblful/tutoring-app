package com.seblful.klas.ui.subjects

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.TestEnvRule
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
class SubjectsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @get:Rule(order = 0)
    val env = TestEnvRule()

    private fun show() {
        val vm =
            SubjectsViewModel(env.subjectRepo, env.enrollmentRepo, env.groupRepo, MainScope(), Dispatchers.Unconfined)
        compose.setContent { KlasTheme { SubjectsScreen(onBack = {}, viewModel = vm) } }
    }

    @Test
    fun `rows say how a subject is used, adding is the last row, and only an unused one can be deleted`() {
        runBlocking {
            val maths = env.fixtures.subject("Maths")
            env.fixtures.subject("Chemistry")
            env.enrollmentRepo.enroll(env.fixtures.student("Liam"), maths, Money.ofMajor(25))
        }
        show()

        compose.onNodeWithText("1\u00A0student", substring = true).assertExists()
        compose.onNodeWithText("Not used yet").assertExists()
        compose.onNodeWithText("Add a subject").assertExists()

        compose.onNodeWithText("Maths").performClick()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("Chemistry").performClick()
        compose.onNodeWithText("Delete").assertExists()
    }
}
