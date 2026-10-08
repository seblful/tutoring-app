package com.seblful.klas.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.seblful.klas.domain.model.Currency
import com.seblful.klas.ui.common.LocalCurrency
import com.seblful.klas.ui.common.Spacing
import com.seblful.klas.ui.theme.KlasTheme

/**
 * The three renders every shared component is looked at in.
 *
 * ### Why these three and not one
 *
 * The project has no shortage of tests, and it still had no way to *see* a component: reaching
 * `ContentCard` or `StatRow` meant building, installing, and navigating to a screen that happened to use
 * one — then doing it twice more for dark and for a large font. So the three conditions that actually
 * break this app's layouts are declared once here and applied by annotation:
 *
 * - **light** and **dark**, because the palette is hand-written per mode ([KlasTheme] has two full
 *   schemes and its own `KlasColors` on top), and nothing but looking catches a tone that reads on one
 *   ground and vanishes on the other.
 * - **1.5× text**, because almost every layout bug this codebase has fixed was a font-scale bug wearing
 *   a different hat: the bottom bar's wrapping labels, the stat tile's amount, the group row's rate
 *   taking the name's width. It is the single most productive thing to look at.
 *
 * All three are drawn in **Russian** (`locale = "ru"`), the language of the sample data and of the tutor:
 * a word that comes from resources — a status, «Удалить», a field's error — then matches the name
 * beside it, and Cyrillic is the width these components have to survive. English is the shorter
 * language almost everywhere it matters, so it is not a fourth render.
 *
 * ### Each render is a screenshot test
 *
 * Every `@Preview` in the app is screenshotted under Robolectric and compared with a committed golden
 * (`app/src/test/screenshots/`), found by scanning — so a preview added here is covered with no test to
 * write, and a change that moves one fails `verifyRoborazziDebug`. See docs/testing.md.
 *
 * ### Why this lives in `src/debug`
 *
 * A preview is a development tool, and shipping one in the release APK means shipping the sample data
 * it draws. The tooling renderer is a `debugImplementation` dependency, and Android Studio renders the
 * debug variant by default, so previews here are visible exactly where they are useful and absent from
 * anything a tutor installs. The `@Preview` annotation itself comes from `ui-tooling-preview`, which is
 * a normal `implementation` — that is the small artefact, and it is what makes this arrangement work.
 */
@Preview(name = "Light", group = "component", showBackground = true, widthDp = 360, locale = "ru")
@Preview(
    name = "Dark",
    group = "component",
    showBackground = true,
    widthDp = 360,
    locale = "ru",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(
    name = "Large text",
    group = "component",
    showBackground = true,
    widthDp = 360,
    locale = "ru",
    fontScale = 1.5f,
)
annotation class KlasPreview

/**
 * The context a component gets on a real screen: the app's theme, its surface colour, and a currency.
 *
 * [KlasTheme] resolves light and dark from `isSystemInDarkTheme()`, which in a preview is the `uiMode`
 * above — so the dark render is the app's own dark scheme rather than an inverted approximation of the
 * light one. [LocalCurrency] is provided because a `Money.text()` anywhere inside would otherwise be
 * reading a composition local that only `AppRoot` normally supplies.
 *
 * `widthDp = 360` on the annotations rather than a width here: a component that fills its parent should
 * be measured against a phone's width, since half of what is worth looking at is what happens when the
 * text no longer fits.
 */
@Composable
fun PreviewSurface(content: @Composable () -> Unit) {
    KlasTheme {
        CompositionLocalProvider(LocalCurrency provides Currency.BYN) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(
                    Modifier.fillMaxWidth().padding(Spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(Spacing.itemGap),
                ) {
                    content()
                }
            }
        }
    }
}
