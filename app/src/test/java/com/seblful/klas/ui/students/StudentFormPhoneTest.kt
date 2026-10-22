package com.seblful.klas.ui.students

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.TestEnv
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.theme.KlasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StudentFormPhoneTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val language = AppLocaleRule.english()

    @get:Rule(order = 0)
    val env = TestEnv()

    @Test
    fun `the phone opens on the dialling code of the chosen currency and follows a change of it`() {
        val vm = StudentFormViewModel(
            env.subjectRepo,
            env.enrollmentRepo,
            env.settingsRepo,
            MainScope(),
            Dispatchers.Unconfined,
        )
        // Onboarding embeds the form after the currency step, which can still be revisited.
        var currency by mutableStateOf(Currency.RUB)
        compose.setContent {
            CompositionLocalProvider(LocalCurrency provides currency) {
                KlasTheme { StudentFormContent(state = vm.state.collectAsState().value, viewModel = vm) }
            }
        }

        compose.onNodeWithText("Contact phone").assertTextContains("+7", substring = true)

        currency = Currency.BYN
        compose.onNodeWithText("Contact phone").assertTextContains("+375", substring = true)
        assertFalse("the code alone is not the tutor's work", vm.state.value.isDirty)
    }
}
