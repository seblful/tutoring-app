package com.seblful.klas

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * The single locale this app speaks, pinned onto its resources.
 *
 * Every string in `values/` is Russian and the date formatters name Russian months outright, so the app
 * has no other language to fall into — but a **plural** is not resolved from the folder its strings live
 * in. `Resources.getQuantityString` picks the quantity rule from the *configuration's* locale, which is
 * the phone's. On an English phone that applies English rules — two forms, `one` and `other` — to
 * Russian text, so `many` is never selected at all and the app says «0 занятия» and «5 занятия»: the
 * counts that read as correct (1, 2) do so by coincidence, since English `one`/`other` happen to line
 * up with Russian `one`/`few` there.
 *
 * Pinning the configuration makes the resources agree with what they actually contain, and Russian's
 * `one`/`few`/`many` are then chosen as written. It is deliberately the *resource* locale only, not
 * `Locale.setDefault`: the formatters already name their own locale, and money is assembled digit by
 * digit ([com.seblful.klas.domain.model.Money]), so nothing else is waiting on the JVM default.
 */
private val Russian: Locale = Locale.forLanguageTag("ru")

/**
 * This context with Russian resources. Applied in [KlasApp] and [MainActivity] via
 * `attachBaseContext`, which covers everything either of them hands a context to — screens, workers
 * and notification text alike. An activity needs its own call: the system builds its base context
 * from the device configuration rather than from the application's.
 */
fun Context.inRussian(): Context {
    val config = Configuration(resources.configuration)
    // Both: setLocale fills in the deprecated single-locale field some framework paths still read,
    // setLocales is what getQuantityString consults for its rule set.
    config.setLocale(Russian)
    config.setLocales(LocaleList(Russian))
    return createConfigurationContext(config)
}
