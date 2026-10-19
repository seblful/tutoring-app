package com.seblful.klas.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier

/**
 * Marks "is this a debuggable build". Injected as a plain flag rather than read from a `Context` at
 * the use site, so the screens that gate on it stay reachable from a JVM test.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild

@ContributesTo(AppScope::class)
@BindingContainer
object BuildModule {

    /** Set by the build type, not by us — release builds drop `FLAG_DEBUGGABLE`. */
    @Provides
    @DebugBuild
    fun provideIsDebugBuild(@ApplicationContext context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
