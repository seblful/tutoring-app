package com.seblful.klas.ui.homework

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.seblful.klas.domain.model.Assignee
import com.seblful.klas.domain.model.Homework
import com.seblful.klas.queries.homework.AssigneeChoice
import com.seblful.klas.queries.homework.HomeworkRow
import com.seblful.klas.testutil.MainDispatcherRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.common.state.UiEvents
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class CardHomeworkTest {
    @get:Rule(order = 0)
    val env = TestEnv()

    @get:Rule(order = 1)
    val main = MainDispatcherRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    @Test
    fun `updating message wording preserves the open dialog`() = runTest(main.dispatcher) {
        val choice = AssigneeChoice(item.assignee, "Ann", env.clock.now())
        val actions = CardHomeworkActions(item.assignee, { choice }, writes)
        var messages by mutableStateOf(Messages(SnackbarHostState(), backgroundScope, "Undo"))
        lateinit var interaction: CardHomework
        compose.setContent { interaction = rememberCardHomework(actions, messages) }
        compose.runOnIdle { interaction.add() }
        val original = interaction

        compose.runOnIdle {
            messages = Messages(messages.hostState, backgroundScope, "Take back")
        }
        compose.waitForIdle()

        assertSame(original, interaction)
        assertEquals(HomeworkDialogDraft.adding(listOf(choice)), interaction.dialog)
    }

    private val failures = UiEvents<Unit>()
    private val writes get() = HomeworkActions(env.homeworkRepo, main.appScope, failures, Unit)

    private val item = Homework(
        id = 1, assignee = Assignee.Student(1), text = "p. 12", givenAt = Instant.fromEpochSeconds(0),
    )
    private val row = HomeworkRow(item, "Ann", lessonAt = null, due = null)

    @Test
    fun `each Add reads a fresh choice and only one dialog is open`() = runTest(main.dispatcher) {
        var choice: AssigneeChoice? = AssigneeChoice(item.assignee, "Ann", env.clock.now())
        val actions = CardHomeworkActions(item.assignee, { choice }, writes)
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        lateinit var interaction: CardHomework
        compose.setContent { interaction = rememberCardHomework(actions, messages) }

        compose.runOnIdle { interaction.add() }
        assertEquals(HomeworkDialogDraft.adding(listOf(choice!!)), interaction.dialog)
        compose.runOnIdle { interaction.open(row) }
        val own = AssigneeChoice(row.homework.assignee, row.title, row.lessonAt)
        assertEquals(HomeworkDialogDraft.editing(row.homework, listOf(own)), interaction.dialog)
        compose.runOnIdle { interaction.dismiss() }
        assertEquals(HomeworkDialogDraft(), interaction.dialog)

        choice = choice!!.copy(name = "Anna", nextLessonAt = env.clock.now() + 1.days)
        compose.runOnIdle { interaction.add() }
        assertEquals(HomeworkDialogDraft.adding(listOf(choice!!)), interaction.dialog)
        compose.runOnIdle { interaction.dismiss() }
        assertEquals(HomeworkDialogDraft(), interaction.dialog)
    }

    @Test
    fun `an unavailable owner opens no add dialog`() = runTest(main.dispatcher) {
        val actions = CardHomeworkActions(item.assignee, { null }, writes)
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        lateinit var interaction: CardHomework
        compose.setContent { interaction = rememberCardHomework(actions, messages) }

        compose.runOnIdle { interaction.add() }

        assertEquals(HomeworkDialogDraft(), interaction.dialog)
    }

    @Test
    fun `giving belongs to the card while editing preserves a group's assignment`() = runTest(main.dispatcher) {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val group = env.fixtures.group(subjectId = subject, members = setOf(student))
        val groupItem = env.fixtures.homework(Assignee.Group(group), text = "Group work")
        val owner = Assignee.Student(student)
        val actions = CardHomeworkActions(owner, { null }, writes)

        actions.give("Individual work")
        actions.edit(groupItem, "Corrected group work")
        runCurrent()

        val individual = env.homeworkRepo.observeForStudent(student).first().single { it.assignee == owner }
        val edited = env.homeworkRepo.observeForGroup(group).first().single()
        assertEquals(owner, individual.assignee)
        assertEquals("Individual work", individual.text)
        assertEquals(Assignee.Group(group), edited.assignee)
        assertEquals("Corrected group work", edited.text)
        assertEquals(groupItem.id, edited.id)
    }

    @Test
    fun `a screen's swipe and snackbar Undo restore the original homework`() = runTest(main.dispatcher) {
        val student = env.fixtures.student()
        val original = env.fixtures.homework(Assignee.Student(student), text = "Exercises")
        val actions = CardHomeworkActions(original.assignee, { null }, writes)
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        lateinit var interaction: CardHomework
        compose.setContent { interaction = rememberCardHomework(actions, messages) }
        val firstInteraction = interaction
        compose.runOnIdle { interaction.open(HomeworkRow(original, "Ann", null, null)) }

        compose.runOnIdle { interaction.swiped(original, true) }
        runCurrent()
        assertEquals(true, env.homeworkRepo.observeForStudent(student).first().single().isDone)
        messages.hostState.currentSnackbarData!!.performAction()
        runCurrent()

        assertEquals(original, env.homeworkRepo.observeForStudent(student).first().single())
        assertSame(firstInteraction, interaction)
    }
}
