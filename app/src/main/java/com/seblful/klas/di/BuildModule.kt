package com.seblful.klas.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
object BuildModule {

    /** Set by the build type, not by us — release builds drop `FLAG_DEBUGGABLE`. */
    @Provides
    @DebugBuild
    fun provideIsDebugBuild(@ApplicationContext context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
