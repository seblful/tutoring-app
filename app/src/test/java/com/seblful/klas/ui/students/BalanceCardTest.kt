package com.seblful.klas.ui.students

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.seblful.klas.domain.model.Balance
import com.seblful.klas.domain.model.Money
import com.seblful.klas.queries.students.StudentDetails
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class BalanceCardTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @Test
    fun `the balance is a section whose header sends the statement`() {
        var statements = 0
        compose.setContent {
            KlasTheme {
                BalanceCard(
                    StudentDetails(balance = Balance.Owed(Money.ofMajor(200))),
                    onAddPayment = {},
                    onStatement = { statements++ },
                )
            }
        }

        compose.onNodeWithText("Balance").assertIsDisplayed()
        compose.onNodeWithText("Owes").assertIsDisplayed()
        compose.onNodeWithText("Statement").performClick()

        assertEquals(1, statements)
    }
}
