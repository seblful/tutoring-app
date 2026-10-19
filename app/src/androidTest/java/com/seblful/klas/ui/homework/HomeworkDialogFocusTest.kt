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

/**
 * **A new piece of homework opens on nothing.** Who it is for comes first, so the dialog used to be
 * wrong from its first frame: the caret in «Задание» and the keyboard up, asking for the homework before
 * the tutor had said whose it was, and covering the dialog while doing it. Nor is «Для кого» opened for
 * them — it is a tap away, and a menu over the dialog on arrival would be the same interruption.
 *
 * Correcting one is the other way round: who is settled, and the text is the thing being corrected.
 *
 * A device test because focus and the soft keyboard are the platform's, not Compose's.
 */
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

    /** «Задание» — a text field merges its label into its own node, so this finds the field. */
    private fun task() = rule.onNodeWithText(context.getString(R.string.homework_text_label))

    @Test
    fun aNewOneDoesNotPutTheCaretInTheTask() {
        open()

        task().assertIsNotFocused()
    }

    @Test
    fun aNewOneDoesNotOpenWhoItIsFor() {
        open()

        // Nobody is preselected, so the names appear only in the open menu.
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
