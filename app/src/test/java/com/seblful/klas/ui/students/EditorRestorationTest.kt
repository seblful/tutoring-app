package com.seblful.klas.ui.students

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.homework.CardHomework
import com.seblful.klas.ui.homework.rememberCardHomework
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class EditorRestorationTest : StudentDetailTestFixture() {
    @get:Rule(order = 2)
    val compose = createComposeRule()
    @get:Rule(order = 3)
    val language = AppLocaleRule.english()

    @Test
    fun `a card restores the open homework editor and its unfinished text`() = runTest(main.dispatcher) {
        val student = seedStudent()
        val vm = vm(student)
        observing(vm.state)
        runCurrent()
        lateinit var homework: CardHomework
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KlasTheme {
                homework = rememberCardHomework(vm.cardHomework, messages)
                homework.Dialogs()
            }
        }
        compose.runOnIdle { homework.add() }
        compose.onNodeWithText("Task").performTextReplacement("An unfinished assignment")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Task").assertTextContains("An unfinished assignment")
    }
}
