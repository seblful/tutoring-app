package com.seblful.klas.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.format.inLanguage
import com.seblful.klas.testutil.texts
import com.seblful.klas.text.Plurals
import com.seblful.klas.text.Strings
import com.seblful.klas.text.pluralStringResource
import com.seblful.klas.text.stringResource
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class AppLanguageTest {

    @get:Rule
    val rule = createComposeRule()

    private val russian = Locale.forLanguageTag("ru")
    private val english = Locale.forLanguageTag("en")

    private fun resourcesIn(locale: Locale) =
        InstrumentationRegistry.getInstrumentation().targetContext.inLanguage(locale).resources

    private fun show(locale: Locale, content: @Composable () -> Unit) {
        rule.setContent { KlasTheme { ProvideAppLanguage(locale.toLanguageTag()) { content() } } }
    }

    @Test
    fun aStringIsDrawnInTheLanguageTheAppProvides() {
        show(english) { Text(stringResource(Strings.nav_schedule)) }

        rule.onNodeWithText(resourcesIn(english).texts().string(Strings.nav_schedule)).assertIsDisplayed()
    }

    @Test
    fun theSameStringIsDrawnInRussianWhenRussianIsProvided() {
        show(russian) { Text(stringResource(Strings.nav_schedule)) }

        rule.onNodeWithText(resourcesIn(russian).texts().string(Strings.nav_schedule)).assertIsDisplayed()
    }

    @Test
    fun aCountedNounTakesTheFormItsOwnLanguageSelects() {
        show(russian) { Text(pluralStringResource(Plurals.lessons_count, 5, 5)) }
        rule.onNodeWithText(resourcesIn(russian).texts().plural(Plurals.lessons_count, 5, 5))
            .assertIsDisplayed()
    }

    @Test
    fun anEnglishCountedNounTakesEnglishForms() {
        show(english) { Text(pluralStringResource(Plurals.lessons_count, 5, 5)) }
        rule.onNodeWithText(resourcesIn(english).texts().plural(Plurals.lessons_count, 5, 5))
            .assertIsDisplayed()
    }

    @Test
    fun aStringReadThroughTheContextFollowsItToo() {
        show(english) { Text(scheduleLabel(LocalContext.current)) }

        rule.onNodeWithText(resourcesIn(english).texts().string(Strings.nav_schedule)).assertIsDisplayed()
    }

    private fun scheduleLabel(context: android.content.Context): String =
        context.texts().string(Strings.nav_schedule)
}
