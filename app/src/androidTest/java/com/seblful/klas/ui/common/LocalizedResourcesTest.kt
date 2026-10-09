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
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * **The screens follow the app's language setting, and not the phone's.**
 *
 * This is the one part of the language feature that could fail silently and completely, and the only one no
 * JVM test can reach. Whether `stringResource` honours a provided locale is a fact about *Compose*: it reads
 * `LocalResources`, which the app overrides — but which local it reads has changed across Compose versions,
 * and getting it wrong produces a Settings row that stores the choice perfectly while every screen goes on
 * drawing the phone's language. A test asserting the row would pass; only a rendered frame can tell.
 *
 * Exercised through the **same** [ProvideLocalizedResources] `AppRoot` uses, which is why that provision is
 * a composable of its own rather than four lines at the root.
 *
 * The words asserted are read straight out of each language's folder, not written here: what is being checked
 * is that the *provision* reaches the resource lookup, and hard-coding «Расписание» against "Schedule" would
 * fail the day either wording is improved for reasons having nothing to do with this.
 */
@HiltAndroidTest
class LocalizedResourcesTest {

    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
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

    /**
     * **The half that would otherwise be missed.** A plural's *form* comes from the configuration's locale
     * rather than from the folder its strings were loaded from, so providing the resources without the
     * configuration gives a screen whose ordinary strings follow the setting and whose counted nouns do not —
     * English text picked by Russian rules, or «5 занятия». Five is the count that tells them apart: Russian
     * selects `many` and English `other`.
     */
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

    /**
     * The app's **other** way of reading a string: hand `LocalContext.current` to a plain function and let it
     * do the lookup. `BackupFailure.messageIn(context)` is exactly this shape, and it is how the wording of a
     * refused restore reaches both the backup screen and first-run setup — so if the provided context were
     * not part of the provision, a snackbar would arrive in the phone's language over a screen in the
     * tutor's.
     *
     * Written the way the app writes it, through a non-composable taking a `Context`, rather than as
     * `LocalContext.current.getString(...)` inside the composition — which is the same lookup and is what
     * Compose's own `LocalContextGetResourceValueCall` lint rule exists to discourage.
     */
    @Test
    fun aStringReadThroughTheContextFollowsItToo() {
        show(english) { Text(scheduleLabel(LocalContext.current)) }

        rule.onNodeWithText(resourcesIn(english).getString(R.string.nav_schedule)).assertIsDisplayed()
    }

    /** A stand-in for `BackupFailure.messageIn`: words built from a `Context`, outside composition. */
    private fun scheduleLabel(context: android.content.Context): String =
        context.getString(R.string.nav_schedule)
}
