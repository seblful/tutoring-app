package com.seblful.klas.ui.students

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.seblful.klas.domain.model.LessonStatus
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.domain.time.today
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.observing
import com.seblful.klas.ui.common.Messages
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
@OptIn(ExperimentalCoroutinesApi::class)
class CardPaymentsTest : StudentDetailTestFixture() {
    @get:Rule(order = 2)
    val compose = createComposeRule()

    @get:Rule(order = 3)
    val language = AppLocaleRule.english()

    @Test
    fun `the parent payment interaction restores the open dialog and typed amount`() = runTest(main.dispatcher) {
        val student = seedStudent()
        val vm = vm(student)
        observing(vm.state)
        runCurrent()
        lateinit var interaction: CardPayments
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KlasTheme {
                interaction = rememberCardPayments(vm, messages)
                interaction.Dialogs()
            }
        }
        compose.runOnIdle { interaction.add() }
        compose.onNodeWithText("Amount").performTextReplacement("42.75")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Amount").assertTextContains("42.75")
        assertTrue(interaction.dialog is CardPayments.Dialog.Adding)
    }

    @Test
    fun `Add takes fresh opening defaults and replaces Edit`() = runTest(main.dispatcher) {
        val subject = env.fixtures.subject()
        val student = env.fixtures.pricedStudent(subjectId = subject)
        val lesson = env.fixtures.lesson(subject, listOf(student), clock.now() - 2.hours).single()
        env.fixtures.setStatus(lesson, LessonStatus.COMPLETED)
        val vm = vm(student)
        observing(vm.state)
        runCurrent()
        lateinit var interaction: CardPayments
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        compose.setContent { interaction = rememberCardPayments(vm, messages) }

        compose.runOnIdle { interaction.add() }
        val first = (interaction.dialog as CardPayments.Dialog.Adding).defaults
        assertEquals(today, first.day)
        assertNull(first.method)
        assertEquals(Money.ofMajor(30), first.suggestions.fullBalance)

        clock.advanceBy(1.days.inWholeMilliseconds)
        env.paymentRepo.record(student, Money.ofMajor(10), clock.today(), PaymentMethod.CARD)
        runCurrent()
        val paid = env.paymentRepo.observeForStudent(student).first().single()
        compose.runOnIdle { interaction.open(paid) }
        assertEquals(CardPayments.Dialog.Editing(paid), interaction.dialog)
        compose.runOnIdle { interaction.add() }
        val next = (interaction.dialog as CardPayments.Dialog.Adding).defaults
        assertEquals(clock.today(), next.day)
        assertEquals(PaymentMethod.CARD, next.method)
        assertEquals(Money.ofMajor(20), next.suggestions.fullBalance)
        compose.runOnIdle { interaction.dismiss() }
        assertNull(interaction.dialog)
    }

    @Test
    fun `Add waits for the student to load`() = runTest(main.dispatcher) {
        val vm = vm(seedStudent())
        lateinit var interaction: CardPayments
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        compose.setContent { interaction = rememberCardPayments(vm, messages) }

        compose.runOnIdle { interaction.add() }

        assertNull(interaction.dialog)
    }

    @Test
    fun `opening another payment replaces typed fields`() = runTest(main.dispatcher) {
        val student = seedStudent()
        env.paymentRepo.record(student, Money.ofMajor(10), today)
        env.paymentRepo.record(student, Money.ofMajor(20), today)
        val vm = vm(student)
        val payments = env.paymentRepo.observeForStudent(student).first().sortedBy { it.amount.minor }
        lateinit var interaction: CardPayments
        val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
        compose.setContent {
            KlasTheme {
                interaction = rememberCardPayments(vm, messages)
                interaction.Dialogs()
            }
        }
        compose.runOnIdle { interaction.open(payments.first()) }
        compose.onNodeWithText("Amount").performTextReplacement("999")

        compose.runOnIdle { interaction.open(payments.last()) }

        compose.onNodeWithText("Amount").assertTextContains("20")
        assertEquals(CardPayments.Dialog.Editing(payments.last()), interaction.dialog)
    }

    @Test
    fun `wording changes preserve typed fields and deletion Undo uses the current messages`() =
        runTest(main.dispatcher) {
            val student = seedStudent()
            env.paymentRepo.record(student, Money.ofMajor(10), today, PaymentMethod.TRANSFER)
            val original = env.paymentRepo.observeForStudent(student).first().single()
            val vm = vm(student)
            var messages by mutableStateOf(Messages(SnackbarHostState(), backgroundScope, "Undo"))
            lateinit var interaction: CardPayments
            compose.setContent {
                KlasTheme {
                    interaction = rememberCardPayments(vm, messages)
                    interaction.Dialogs()
                }
            }
            compose.runOnIdle { interaction.open(original) }
            compose.onNodeWithText("Amount").performTextReplacement("42.75")
            val before = interaction

            compose.runOnIdle { messages = Messages(messages.hostState, backgroundScope, "Take back") }
            compose.onNodeWithText("Amount").assertTextContains("42.75")
            assertSame(before, interaction)
            compose.onNodeWithText("Delete").performClick()
            runCurrent()
            assertNull(interaction.dialog)
            assertTrue(env.paymentRepo.observeForStudent(student).first().isEmpty())
            val snackbar = messages.hostState.currentSnackbarData!!
            assertEquals("Take back", snackbar.visuals.actionLabel)

            snackbar.performAction()
            runCurrent()

            assertEquals(listOf(original), env.paymentRepo.observeForStudent(student).first())
        }

    @Test
    fun `saving an added payment and a correction uses the card's existing write actions`() =
        runTest(main.dispatcher) {
            val student = seedStudent()
            val vm = vm(student)
            observing(vm.state)
            runCurrent()
            lateinit var interaction: CardPayments
            val messages = Messages(SnackbarHostState(), backgroundScope, "Undo")
            compose.setContent {
                KlasTheme {
                    interaction = rememberCardPayments(vm, messages)
                    interaction.Dialogs()
                }
            }
            compose.runOnIdle { interaction.add() }
            compose.onNodeWithText("Amount").performTextReplacement("35")
            compose.onNodeWithText("Save").performClick()
            runCurrent()
            val original = env.paymentRepo.observeForStudent(student).first().single()
            assertEquals(Money.ofMajor(35), original.amount)
            assertEquals(student, original.studentId)
            assertNull(interaction.dialog)

            compose.runOnIdle { interaction.open(original) }
            compose.onNodeWithText("Amount").performTextReplacement("45")
            compose.onNodeWithText("Save").performClick()
            runCurrent()

            val event = vm.events.stream.first() as StudentDetailViewModel.Event.PaymentCorrected
            assertEquals(original, event.original)
            assertEquals(original.id, event.corrected.id)
            assertEquals(Money.ofMajor(45), event.corrected.amount)
        }
}
