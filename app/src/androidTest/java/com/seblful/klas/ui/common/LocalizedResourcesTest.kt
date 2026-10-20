package com.seblful.klas.ui.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.seblful.klas.R
import com.seblful.klas.format.inLanguage
import com.seblful.klas.ui.theme.KlasTheme
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class LocalizedResourcesTest {

    @get:Rule
    val rule = createComposeRule()

    private val russian = Locale.forLanguageTag("ru")
    private val english = Locale.forLanguageTag("en")

    private fun resourcesIn(locale: Locale) =
        InstrumentationRegistry.getInstrumentation().targetContext.inLanguage(locale).resources

    private fun show(locale: Locale, content: @Composable () -> Unit) {
        rule.setContent { KlasTheme { ProvideLocalizedResources(locale) { content() } } }
    }

    @Test
    fun aStringIsDrawnInTheLanguageTheAppProvides() {
        show(english) { Text(stringResource(R.string.nav_schedule)) }

        rule.onNodeWithText(resourcesIn(english).getString(R.string.nav_schedule)).assertIsDisplayed()
    }

    @Test
    fun theSameStringIsDrawnInRussianWhenRussianIsProvided() {
        show(russian) { Text(stringResource(R.string.nav_schedule)) }

        rule.onNodeWithText(resourcesIn(russian).getString(R.string.nav_schedule)).assertIsDisplayed()
    }

    @Test
    fun aCountedNounTakesTheFormItsOwnLanguageSelects() {
        show(russian) { Text(pluralStringResource(R.plurals.lessons_count, 5, 5)) }
        rule.onNodeWithText(resourcesIn(russian).getQuantityString(R.plurals.lessons_count, 5, 5))
            .assertIsDisplayed()
    }

    @Test
    fun anEnglishCountedNounTakesEnglishForms() {
        show(english) { Text(pluralStringResource(R.plurals.lessons_count, 5, 5)) }
        rule.onNodeWithText(resourcesIn(english).getQuantityString(R.plurals.lessons_count, 5, 5))
            .assertIsDisplayed()
    }

    @Test
    fun aStringReadThroughTheContextFollowsItToo() {
        show(english) { Text(scheduleLabel(LocalContext.current)) }

        rule.onNodeWithText(resourcesIn(english).getString(R.string.nav_schedule)).assertIsDisplayed()
    }

    private fun scheduleLabel(context: android.content.Context): String =
        context.getString(R.string.nav_schedule)
}
