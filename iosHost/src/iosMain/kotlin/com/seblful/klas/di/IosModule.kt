package com.seblful.klas.di

import com.seblful.klas.data.local.KlasDatabase
import com.seblful.klas.data.local.openDatabase
import com.seblful.klas.diagnostics.DeviceInfo
import com.seblful.klas.domain.time.WallClock
import com.seblful.klas.settings.DevicePreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.TimeZone
import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCurrencyCode
import platform.Foundation.preferredLanguages
import platform.UIKit.UIDevice
import kotlin.experimental.ExperimentalNativeApi
import kotlin.time.Clock
import kotlin.time.Instant

/** The iOS facts Android's modules read off a `Context`: the database, the clock, the phone's locales. */
@ContributesTo(AppScope::class)
@BindingContainer
object IosModule {

    /** A debug build may drop a database it cannot migrate, as Android's debuggable build does. */
    @OptIn(ExperimentalNativeApi::class)
    @Provides
    @DebugBuild
    fun provideIsDebugBuild(): Boolean = Platform.isDebugBinary

    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(): KlasDatabase = openDatabase()

    /**
     * The zone read at every use: iOS does not restart an app whose phone moved zones, and Android's
     * `DeviceClock` follows the same change through a broadcast.
     */
    @Provides
    fun provideClock(): WallClock = object : WallClock {
        override fun now(): Instant = Clock.System.now()
        override val zone: TimeZone get() = TimeZone.currentSystemDefault()
    }

    /** The phone's languages, most-preferred first, with the currency of each one's region. */
    @Provides
    fun provideDevicePreferences(): DevicePreferences {
        val tags = NSLocale.preferredLanguages.filterIsInstance<String>()
        return DevicePreferences(
            languageTags = tags,
            currencyCodes = tags.map { NSLocale(localeIdentifier = it).objectForKey(NSLocaleCurrencyCode) as? String },
        )
    }

    @Provides
    @SingleIn(AppScope::class)
    fun provideDeviceInfo(): DeviceInfo = DeviceInfo(
        appVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "?",
        system = "iOS ${UIDevice.currentDevice.systemVersion}",
        device = UIDevice.currentDevice.model,
    )
}
