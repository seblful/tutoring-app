package com.seblful.klas.di

import com.seblful.klas.format.deviceLocales
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import java.util.Locale

/**
 * The locales the **phone** asks for, most-preferred first — the device half of two settings that start at
 * whatever the tutor's phone already says: which language the app is shown in
 * ([com.seblful.klas.domain.model.AppLanguage.SYSTEM]) and which currency a fresh install captions money
 * with ([com.seblful.klas.domain.model.Currency.preferredFor]).
 *
 * Injected as a value rather than reached for statically, for the reason [FirstDayOfWeek] is: what a phone
 * is set to is precisely the thing a test has to be able to state. "A tutor in Minsk with an English phone
 * is paid in rubles" is a rule about a `ru`-less locale list with a `BY` region in it, and there is no way
 * to write that assertion against `Locale.getDefault()`.
 *
 * Read once per component rather than observed: changing the phone's language restarts the process, so
 * there is no live value to follow. The list, not one locale, because the two questions read different
 * parts of it — the language off the front, the currency off whichever entry first names a region the app
 * knows.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceLocales

@ContributesTo(AppScope::class)
@BindingContainer
object LocaleModule {

    @Provides
    @DeviceLocales
    fun provideDeviceLocales(): List<Locale> = deviceLocales()
}
