package com.seblful.klas.ui.homework

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.domain.homework.HomeworkRules
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.ui.common.rememberMessages
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class NestedHomeworkNamingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a group's homework on the group's own card does not repeat the group's name`() {
        show(owner = group)

        compose.onNodeWithText(GroupName, substring = true).assertDoesNotExist()
    }

    @Test
    fun `a group's homework on a member's card says which group it is`() {
        show(owner = Assignee.Student(7))

        compose.onNodeWithText(GroupName, substring = true).assertExists()
    }

    private fun show(owner: Assignee) {
        compose.setContent {
            KlasTheme {
                SwipeableHomework(
                    row = row,
                    onSwipe = {},
                    onClick = {},
                    messages = rememberMessages(),
                    nested = true,
                    owner = owner,
                )
            }
        }
    }

    private val group = Assignee.Group(3)

    private val row = HomeworkRow(
        homework = Homework(
            id = 1,
            assignee = group,
            text = "ЦТ, вариант 7: часть А, задания 1–18",
            givenAt = Instant.fromEpochMilliseconds(0),
        ),
        title = GroupName,
        lessonAt = Instant.fromEpochMilliseconds(0),
        due = HomeworkRules.Due.OVERDUE,
    )

    private companion object {
        const val GroupName = "Подготовка к ЦТ"
    }
}
