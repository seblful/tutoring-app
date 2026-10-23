package com.seblful.klas.ui.homework

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Instant

/** Homework says which lesson it is for, never when it is «due» — it has no date of its own. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class HomeworkLessonLineTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `on the tab, under its section, a row gives whose it is and the lesson's time alone`() {
        show(nested = false)

        compose.onNodeWithText("Liam", substring = true).assertExists()
        compose.onNodeWithText("Today's lesson", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Due", substring = true).assertDoesNotExist()
    }

    @Test
    fun `in a student's card, which has no sections, a row names the lesson`() {
        show(nested = true)

        compose.onNodeWithText("Today's lesson", substring = true).assertExists()
        compose.onNodeWithText("Due", substring = true).assertDoesNotExist()
    }

    private fun show(nested: Boolean) {
        compose.setContent {
            KlasTheme {
                SwipeableHomework(
                    row = row,
                    onSwipe = {},
                    onClick = {},
                    messages = rememberMessages(),
                    nested = nested,
                )
            }
        }
    }

    private val row = HomeworkRow(
        homework = Homework(
            id = 1,
            assignee = Assignee.Student(1),
            text = "Newton's laws: 5.12–5.20",
            givenAt = Instant.fromEpochMilliseconds(0),
        ),
        title = "Liam",
        lessonAt = Instant.fromEpochMilliseconds(0),
        due = HomeworkRules.Due.TODAY,
    )
}
