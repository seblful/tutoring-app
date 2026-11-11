package com.seblful.klas.ui.students

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.seblful.klas.domain.model.Student
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.Strings
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContactBlockTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val messaged = mutableListOf<String>()
    private val emailed = mutableListOf<String>()

    private fun show(student: Student) {
        rule.setContent {
            KlasTheme {
                ContactBlock(
                    student = student,
                    onCall = {},
                    onMessage = { phone, _ -> messaged += phone },
                    onEmail = { emailed += it },
                )
            }
        }
    }

    @Test
    fun `the phone is pressed alone, not the place under it`() {
        show(Student(name = "Liam", phone = PHONE))

        rule.onNodeWithText(PHONE).assert(clickableAs(context.texts().string(Strings.student_message))).performClick()
        rule.onNodeWithText(context.texts().string(Strings.class_mode_in_person)).assert(notClickable)
        assertEquals(listOf(PHONE), messaged)
    }

    @Test
    fun `an email standing in for the phone writes to it`() {
        show(Student(name = "Liam", email = EMAIL))

        rule.onNodeWithText(EMAIL)
            .assert(clickableAs(context.texts().string(Strings.student_write_email)))
            .performClick()
        assertEquals(listOf(EMAIL), emailed)
    }

    @Test
    fun `an archived student says so beside the place, as an archived group does`() {
        show(Student(name = "Liam", archived = true))

        rule.onNodeWithText(context.texts().string(Strings.student_archived_badge)).assertExists()
    }

    @Test
    fun `a held contact icon names itself, as the bar's icons do`() {
        show(Student(name = "Liam", phone = PHONE))
        val call = context.texts().string(Strings.student_call)

        rule.onNodeWithContentDescription(call).performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(1, rule.onAllNodesWithText(call).fetchSemanticsNodes().size)
    }

    private fun clickableAs(label: String) = SemanticsMatcher("clickable as «$label»") {
        it.config.getOrElseNullable(SemanticsActions.OnClick) { null }?.label == label
    }

    private val notClickable = SemanticsMatcher("not clickable") { SemanticsActions.OnClick !in it.config }

    private companion object {
        const val PHONE = "+1 202 555 0178"
        const val EMAIL = "liam@example.com"
    }
}
