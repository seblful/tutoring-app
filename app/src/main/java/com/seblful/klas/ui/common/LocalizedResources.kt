package com.seblful.klas.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.seblful.klas.inLanguage
import java.util.Locale

/**
 * Draws [content] in [locale] — **the app's language, provided to a whole composition.**
 *
 * `MainActivity` cannot pin this. `attachBaseContext` runs before `onCreate`, and the tutor's choice lives in
 * the settings row, which is a suspending query — so the activity's own context is only ever the language the
 * *phone* asks for. Providing it instead of recreating the activity is also what makes the switch in Settings
 * and in first-run setup instant: the next frame is in the new language, with no restart under the tutor's
 * thumb. `AppRoot` is the one caller.
 *
 * **All three locals, because a reader may ask any of them.** `stringResource` goes through
 * [LocalResources]; older code and libraries reach `LocalContext.current.resources`; and a plural's *rule
 * set* comes from the configuration rather than from the folder its strings were loaded from, which is the
 * whole reason this app pins a locale at all (`Localization.kt`). Providing one and not the others is a
 * screen half in each language, which is exactly the failure mode that is hard to see: most strings would
 * follow and the counted nouns would not.
 *
 * The context is a `ContextWrapper` around the one it was handed rather than a bare configuration context,
 * so anything that walks a context's chain looking for what it was built from still finds it — see
 * [inLanguage]. `remember`ed on the locale, because building one allocates a `Resources`.
 *
 * Its own composable rather than four lines inside `AppRoot` so that a device test can exercise the *same*
 * provision the app uses (`LocalizedResourcesTest`). Whether Compose honours a provided locale is a fact
 * about Compose, not about this app: it can only be checked on a device, and it is the one part of the
 * language feature that would fail silently and completely.
 */
@Composable
fun ProvideLocalizedResources(locale: Locale, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, locale) { base.inLanguage(locale) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        @Suppress("DEPRECATION")
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
