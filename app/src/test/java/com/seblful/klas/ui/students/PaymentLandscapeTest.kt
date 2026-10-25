package com.seblful.klas.ui.students

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.domain.model.Money
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A short landscape window is not reproducible in Robolectric (a dialog never idles), so this pins the scroll. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class PaymentLandscapeTest {
    @get:Rule val compose = createComposeRule()
    @get:Rule val language = AppLocaleRule.english()

    @Test
    fun `the payment form scrolls so a short window cannot hide its methods`() {
        compose.setContent {
            KlasTheme { PaymentDialog("Payment", Money.ofMajor(10), LocalDate(2026, 6, 1), null, { _, _, _ -> }, {}) }
        }

        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertExists()
        compose.onNodeWithText("Transfer").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }
}
