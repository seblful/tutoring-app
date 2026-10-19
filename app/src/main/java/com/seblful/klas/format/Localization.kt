package com.seblful.klas.format

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.seblful.klas.domain.model.AppLanguage
import com.seblful.klas.domain.model.Currency
import java.util.Locale

/**
 * How a chosen language reaches the resources — the Android half of
 * [com.seblful.klas.domain.model.AppLanguage].
 *
 * The app has two string folders (`values/` English, `values-ru/` Russian) and **a plural is not resolved
 * from the folder its strings live in.** `Resources.getQuantityString` picks the quantity rule from the
 * *configuration's* locale, so a phone set to English applies English's two forms — `one` and `other` — to
 * whatever text it loaded. That is what made the Russian-only app say «0 занятия» and «5 занятия» on an
 * English phone: `many` was never selected at all, and the counts that read as correct (1, 2) did so by
 * coincidence. So the configuration has to be moved to the language actually being shown, every time, and
 * not merely the folder chosen.
 *
 * **It is the resource configuration only, never `Locale.setDefault`.** The JVM default is the *phone's*
 * answer and is read as such: `WeekStart` asks it which day the tutor starts their week on, a preference
 * the platform hands over as a locale extension. Replacing it would answer that question with the app's own
 * language setting, which is a different question.
 */

/**
 * This context with [locale]'s resources — the same strings, and the plural rules that go with them.
 *
 * A [ContextWrapper] around the original rather than the bare `createConfigurationContext` result, so the
 * chain a caller may still need (an `Activity`, a themed context) survives being localized: only
 * [Resources], [Configuration] and the assets they read come from the new configuration. Handing back the
 * raw configuration context instead would silently sever `LocalActivity` and anything else that walks a
 * context's wrappers looking for what it was built from.
 */
fun Context.inLanguage(locale: Locale): Context = LocalizedContext(this, locale)

/**
 * This context's resources in the language the app is **currently displaying in** ([AppLocale.current]).
 *
 * For the two places that build text from the *application* context and so have no composition to read a
 * localized one from: the notification tray and the calendar mirror's event locations. Every screen gets its
 * own through `AppRoot`, which provides one to the whole composition.
 *
 * Built per call rather than cached, and deliberately: a cached `Resources` would go stale on any
 * configuration change (a density or font-scale change, not only a locale one), and the cost is one
 * `createConfigurationContext` against posting a notification or writing a calendar event.
 */
fun Context.inAppLanguage(): Context = inLanguage(AppLocale.current)

/**
 * The locale [AppLanguage.tagFor] resolves to against the phone's [preferred] locales — what a
 * `Configuration` and a `DateTimeFormatter` both take. The domain speaks BCP 47 tags; this is where a tag
 * becomes the platform's locale.
 */
fun AppLanguage.localeFor(preferred: List<Locale>): Locale =
    Locale.forLanguageTag(tagFor(preferred.map(Locale::toLanguageTag)))

/**
 * The currency each of these locales' **region** uses, as an ISO 4217 code — null for a locale with no
 * region, or one the platform has never heard of. `java.util.Currency` holds the mapping, so the eurozone
 * does not have to be listed anywhere and cannot go stale; [Currency.preferredFor] picks among the answers.
 */
fun List<Locale>.regionCurrencies(): List<String?> =
    map { locale -> runCatching { java.util.Currency.getInstance(locale).currencyCode }.getOrNull() }

/**
 * The phone's locales, most-preferred first — what `AppLanguage.SYSTEM` and `Currency.preferredFor` are
 * resolved against.
 *
 * Read off the **device**, deliberately, and this is the reason it is not simply
 * `resources.configuration.locales`: the app replaces its own configuration locale ([inLanguage]) with a
 * bare `ru` or `en`, so its own context can no longer report either what the tutor's phone is set to or the
 * extension keywords (`ru-BY-u-fw-sun`) the platform attaches to it. `Locale.getDefault` is set by the
 * platform at process start and `Resources.getSystem()` is untouched by this app, so both are asked and
 * every answer is kept in order — the region matters as much as the language here, since it is what says
 * which currency to start on.
 */
fun deviceLocales(): List<Locale> = buildList {
    // The FORMAT category first: it is the one the platform documents its regional preferences under.
    add(Locale.getDefault(Locale.Category.FORMAT))
    add(Locale.getDefault())
    val system = Resources.getSystem().configuration.locales
    for (index in 0 until system.size()) add(system[index])
}

/**
 * A context that answers with one language's resources while remaining the context it wrapped.
 *
 * Private because [inLanguage] is the whole of its public purpose. The resources are built once, at
 * construction: `createConfigurationContext` is not free, and this is created per composition of the app
 * root and per notification posted.
 */
private class LocalizedContext(base: Context, locale: Locale) : ContextWrapper(base) {

    private val localized: Resources = base.createConfigurationContext(
        Configuration(base.resources.configuration).apply {
            // Both: `setLocale` fills in the deprecated single-locale field some framework paths still
            // read, `setLocales` is what getQuantityString consults for its rule set.
            setLocale(locale)
            setLocales(LocaleList(locale))
        },
    ).resources

    override fun getResources(): Resources = localized

    override fun getAssets(): android.content.res.AssetManager = localized.assets
}
