package com.seblful.klas.di

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/**
 * Marks "is this a debuggable build". Injected as a plain flag rather than read from a `Context` at
 * the use site, so the screens that gate on it stay reachable from a JVM test.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild

@Module
@InstallIn(SingletonComponent::class)
object BuildModule {

    /** Set by the build type, not by us — release builds drop `FLAG_DEBUGGABLE`. */
    @Provides
    @DebugBuild
    fun provideIsDebugBuild(@ApplicationContext context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}
