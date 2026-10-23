package com.seblful.klas.ui.students

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.seblful.klas.domain.model.Money
import com.seblful.klas.domain.model.PaymentMethod
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
@OptIn(ExperimentalTestApi::class)
class PaymentDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    private val today = LocalDate(2026, 6, 1)

    @Test
    fun `an invalid amount cannot be saved and a correction records the amount`() {
        var saved: Money? = null
        compose.setContent {
            KlasTheme {
                PaymentDialog("Payment", Money.ofMajor(10), today, null, { amount, _, _ -> saved = amount }, {})
            }
        }

        compose.onNodeWithText("Amount").performTextReplacement("-1")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Amount").performTextReplacement("25.50")
        compose.onNodeWithText("Save").assertIsEnabled().performClick()

        assertEquals(Money(2550), saved)
    }

    @Test
    fun `a typed amount survives recreation of the payment dialog`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            KlasTheme { PaymentDialog("Payment", Money.ofMajor(10), today, null, { _, _, _ -> }, {}) }
        }
        compose.onNodeWithText("Amount").performTextReplacement("42.75")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Amount").assertTextContains("42.75")
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `the method offered can be changed before saving`() {
        val saved = mutableListOf<PaymentMethod?>()
        compose.setContent {
            KlasTheme {
                PaymentDialog("Payment", Money.ofMajor(10), today, PaymentMethod.CARD, { _, _, m -> saved += m }, {})
            }
        }

        compose.onNodeWithText("Card").assertIsSelected()
        compose.onNodeWithText("Transfer").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf<PaymentMethod?>(PaymentMethod.TRANSFER), saved)
    }

    @Test
    fun `a method can be taken back to none`() {
        val saved = mutableListOf<PaymentMethod?>()
        compose.setContent {
            KlasTheme {
                PaymentDialog("Payment", Money.ofMajor(10), today, PaymentMethod.CASH, { _, _, m -> saved += m }, {})
            }
        }

        compose.onNodeWithText("Cash").performClick()
        compose.onNodeWithText("Cash").assertIsNotSelected()
        compose.onNodeWithText("Save").performClick()
        assertEquals(listOf<PaymentMethod?>(null), saved)
    }
}
