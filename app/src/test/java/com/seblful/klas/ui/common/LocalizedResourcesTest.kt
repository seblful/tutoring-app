package com.seblful.klas.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.seblful.klas.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class LocalizedResourcesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    @Config(qualifiers = "en-rUS")
    fun `changing to Arabic and back updates words and layout direction together`() {
        var locale by mutableStateOf(Locale.ENGLISH)
        compose.setContent {
            ProvideLocalizedResources(locale) {
                Text("${LocalLayoutDirection.current}|${stringResource(R.string.nav_homework)}")
            }
        }
        compose.onNodeWithText("Ltr|Homework").assertExists()
        compose.runOnIdle { locale = Locale.forLanguageTag("ar") }
        compose.onNodeWithText("Rtl|الواجبات").assertExists()
        compose.runOnIdle { locale = Locale.ENGLISH }
        compose.onNodeWithText("Ltr|Homework").assertExists()
    }

    @Test
    @Config(qualifiers = "ar")
    fun `English uses left to right layout on an Arabic phone`() {
        compose.setContent {
            ProvideLocalizedResources(Locale.ENGLISH) {
                Text("${LocalLayoutDirection.current}|${stringResource(R.string.nav_homework)}")
            }
        }
        compose.onNodeWithText("Ltr|Homework").assertExists()
    }
}
