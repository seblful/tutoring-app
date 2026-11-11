package com.seblful.klas.di

import com.seblful.klas.format.deviceLocales
import com.seblful.klas.format.toDevicePreferences
import com.seblful.klas.settings.DevicePreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import java.util.Locale

@ContributesTo(AppScope::class)
@BindingContainer
object LocaleModule {

    @Provides
    @DeviceLocales
    fun provideDeviceLocales(): List<Locale> = deviceLocales()
}

@ContributesTo(AppScope::class)
@BindingContainer
object DevicePreferencesModule {
    @Provides
    fun provideDevicePreferences(
        @DeviceLocales locales: List<Locale>,
    ): DevicePreferences = locales.toDevicePreferences()
}
