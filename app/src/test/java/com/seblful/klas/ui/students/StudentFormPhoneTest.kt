package com.seblful.klas.ui.students

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.testutil.AppLocaleRule
import com.seblful.klas.testutil.TestEnvRule
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
    val env = TestEnvRule()

    @Test
    fun `the phone opens empty whatever the currency, and offers the phone's contacts`() {
        val vm = StudentFormViewModel(
            androidx.lifecycle.SavedStateHandle(),
            env.subjectRepo,
            env.enrollmentRepo,
            env.studentRepo,
            env.settingsRepo,
            MainScope(),
            Dispatchers.Unconfined,
        )
        var currency by mutableStateOf(Currency.RUB)
        compose.setContent {
            CompositionLocalProvider(LocalCurrency provides currency) {
                KlasTheme { StudentFormContent(state = vm.state.collectAsState().value, viewModel = vm) }
            }
        }

        compose.onNodeWithText("Contact phone").assert(hasText("+", substring = true).not())
        compose.onNodeWithContentDescription("Choose from contacts").assertExists()

        currency = Currency.BYN
        compose.onNodeWithText("Contact phone").assert(hasText("+", substring = true).not())
        assertFalse("an untouched phone is not the tutor's work", vm.state.value.isDirty)
    }
}
