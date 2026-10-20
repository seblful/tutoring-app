package com.seblful.klas.ui.homework

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.R
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class HomeworkDialogFocusTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val ann = AssigneeChoice(Assignee.Student(1), "Аня", nextLessonAt = null)
    private val group = AssigneeChoice(Assignee.Group(2), "11 «А»", nextLessonAt = null)

    private fun open(editing: Homework? = null) {
        rule.setContent {
            KlasTheme {
                HomeworkDialog(
                    choices = listOf(ann, group),
                    onSave = { _, _ -> },
                    onDismiss = {},
                    editing = editing,
                )
            }
        }
    }

    private fun task() = rule.onNodeWithText(context.getString(R.string.homework_text_label))

    @Test
    fun aNewOneDoesNotPutTheCaretInTheTask() {
        open()

        task().assertIsNotFocused()
    }

    @Test
    fun aNewOneDoesNotOpenWhoItIsFor() {
        open()

        rule.onNodeWithText(ann.name).assertDoesNotExist()
        rule.onNodeWithText(group.name).assertDoesNotExist()
    }

    @Test
    fun aCorrectionOpensInTheTask() {
        open(
            editing = Homework(
                id = 7, assignee = ann.assignee, text = "§12", givenAt = Instant.fromEpochMilliseconds(0),
            ),
        )

        task().assertIsFocused()
    }
}
