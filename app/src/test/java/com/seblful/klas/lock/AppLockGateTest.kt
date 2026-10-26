package com.seblful.klas.lock

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.common.DurationPickerModal
import com.seblful.klas.ui.common.KlasMenu
import com.seblful.klas.ui.common.MenuField
import com.seblful.klas.ui.common.MenuItem
import com.seblful.klas.ui.common.TimePickerModal
import com.seblful.klas.ui.designsystem.component.CoachMark
import com.seblful.klas.ui.designsystem.component.KlasSheet
import com.seblful.klas.ui.students.PaymentDialog
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppLockGateTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val prompts = mutableListOf<String>()

    private fun gate(locked: Boolean) = compose.setContent {
        KlasTheme { AppLockGate(locked, onUnlock = { prompts += it }) { Text("Ava Thompson owes 90 $") } }
    }

    @Test
    fun `locked, the content is out of reach and the prompt opens by itself`() {
        gate(locked = true)

        compose.onNodeWithText("Ava Thompson owes 90 $").assertDoesNotExist()
        // The app's own name, which a debug build spells «Klas-debug».
        compose.onNodeWithText("is locked", substring = true).assertExists()
        assertTrue(prompts.single().startsWith("Unlock "))

        compose.onNodeWithText("Unlock").performClick()
        assertEquals("a dismissed prompt can be asked for again", 2, prompts.size)
    }

    @Test
    fun `open, the content shows and nothing is asked`() {
        gate(locked = false)

        compose.onNodeWithText("Ava Thompson owes 90 $").assertExists()
        compose.onNodeWithText("is locked", substring = true).assertDoesNotExist()
        assertEquals(emptyList<String>(), prompts)
    }

    @Test
    fun `locking hides payment actions and unlocking restores the typed payment`() {
        var locked by mutableStateOf(false)
        var saved: Money? = null
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) {
                    PaymentDialog(
                        "Private payment", Money.ofMajor(10), LocalDate(2026, 6, 1), null,
                        { amount, _, _ -> saved = amount }, {},
                    )
                }
            }
        }
        compose.onNodeWithText("Amount").performTextReplacement("42.75")

        compose.runOnIdle { locked = true }
        compose.onNodeWithText("Private payment").assertDoesNotExist()
        compose.onNodeWithText("Save").assertDoesNotExist()
        assertEquals(null, saved)

        compose.runOnIdle { locked = false }
        compose.onNodeWithText("Amount").assertTextContains("42.75")
        compose.onNodeWithText("Save").performClick()
        assertEquals(Money(4275), saved)
    }

    @Test
    @OptIn(ExperimentalMaterial3Api::class)
    fun `locking hides a sheet and retains draft state inside its window`() {
        var locked by mutableStateOf(false)
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) {
                    KlasSheet({}, rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                        var draft by rememberSaveable { mutableStateOf("") }
                        OutlinedTextField(draft, { draft = it }, label = { Text("Private lesson") })
                    }
                }
            }
        }
        compose.onNodeWithText("Private lesson").performTextReplacement("Ava's lesson")

        compose.runOnIdle { locked = true }
        compose.onNodeWithText("Private lesson").assertDoesNotExist()
        compose.onNodeWithText("Ava's lesson").assertDoesNotExist()

        compose.runOnIdle { locked = false }
        compose.onNodeWithText("Private lesson").assertTextContains("Ava's lesson")
    }

    @Test
    fun `locking hides menu actions until the tutor unlocks`() {
        var locked by mutableStateOf(false)
        var deleted = false
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) {
                    KlasMenu(expanded = true, onDismissRequest = {}) {
                        MenuItem(text = { Text("Delete private payment") }, onClick = { deleted = true })
                    }
                }
            }
        }
        compose.onNodeWithText("Delete private payment").assertExists()

        compose.runOnIdle { locked = true }
        compose.onNodeWithText("Delete private payment").assertDoesNotExist()
        assertEquals(false, deleted)

        compose.runOnIdle { locked = false }
        compose.onNodeWithText("Delete private payment").performClick()
        assertEquals(true, deleted)
    }

    @Test
    fun `locking a typed picker preserves both fields for confirmation after unlock`() {
        var locked by mutableStateOf(false)
        var confirmed: Int? = null
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) { DurationPickerModal(60, { confirmed = it }, {}) }
            }
        }
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("2")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("15")

        compose.runOnIdle { locked = true }
        compose.onNodeWithText("Done").assertDoesNotExist()

        compose.runOnIdle { locked = false }
        compose.onAllNodes(hasSetTextAction())[0].assertTextContains("2")
        compose.onAllNodes(hasSetTextAction())[1].assertTextContains("15")
        compose.onNodeWithText("Done").performClick()
        assertEquals(135, confirmed)
    }

    @Test
    fun `a new dialog opened beneath the lock waits to request focus until unlocking`() {
        var locked by mutableStateOf(true)
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) {
                    PaymentDialog("Private payment", null, LocalDate(2026, 6, 1), null, { _, _, _ -> }, {})
                }
            }
        }
        compose.onNodeWithText("Private payment").assertDoesNotExist()

        compose.runOnIdle { locked = false }
        compose.onNodeWithText("Amount").performTextReplacement("25")
        compose.onNodeWithText("Amount").assertTextContains("25")
    }

    @Test
    fun `an exposed field menu is hidden while locked`() {
        var locked by mutableStateOf(false)
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) {
                    MenuField("Student", "Ava", enabled = true) {
                        MenuItem(text = { Text("Private student option") }, onClick = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Student").performClick()
        compose.onNodeWithText("Private student option").assertExists()

        compose.runOnIdle { locked = true }
        compose.onNodeWithText("Private student option").assertDoesNotExist()
    }

    @Test
    fun `a typed time survives locking and confirms after unlocking`() {
        var locked by mutableStateOf(false)
        var confirmed: LocalTime? = null
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) { TimePickerModal(LocalTime(9, 0), { confirmed = it }, {}) }
            }
        }
        compose.onNodeWithContentDescription("Switch to text input", substring = true).performClick()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("45")

        compose.runOnIdle { locked = true }
        compose.onNodeWithText("Done").assertDoesNotExist()

        compose.runOnIdle { locked = false }
        compose.onAllNodes(hasSetTextAction())[1].assertTextContains("45")
        compose.onNodeWithText("Done").performClick()
        assertEquals(LocalTime(9, 45), confirmed)
    }

    @Test
    fun `locking hides an open coach mark without marking it read`() {
        var locked by mutableStateOf(false)
        var dismissed = 0
        compose.setContent {
            KlasTheme {
                AppLockGate(locked, {}) {
                    CoachMark("Private lesson tip", visible = true, onDismiss = { dismissed++ }) { Text("Lesson") }
                }
            }
        }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithText("Private lesson tip").assertExists()

        compose.runOnIdle { locked = true }
        compose.onNodeWithText("Private lesson tip").assertDoesNotExist()
        assertEquals(0, dismissed)
    }

    @Test
    fun `a window draft survives process recreation while the app is locked`() {
        var locked by mutableStateOf(false)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KlasTheme {
                AppLockGate(locked, {}) {
                    KlasMenu(expanded = true, onDismissRequest = {}) { SavedWindowDraft() }
                }
            }
        }
        compose.onNodeWithText("Private menu search").performTextReplacement("Ava")
        compose.runOnIdle { locked = true }

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Private menu search").assertDoesNotExist()

        compose.runOnIdle { locked = false }
        compose.onNodeWithText("Private menu search").assertTextContains("Ava")
    }
}

@Composable
private fun SavedWindowDraft() {
    var draft by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(draft, { draft = it }, label = { Text("Private menu search") })
}
