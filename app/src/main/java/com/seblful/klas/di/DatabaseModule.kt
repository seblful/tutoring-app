package com.seblful.klas.di

import android.content.Context
import com.seblful.klas.data.local.KlasDatabase
import com.seblful.klas.data.local.openDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseModule {

    /** Shared schema and migrations, with destructive fallback enabled only by the debug host. */
    @Provides
    @SingleIn(AppScope::class)
    fun provideDatabase(
        @ApplicationContext context: Context,
        @DebugBuild debugBuild: Boolean,
    ): KlasDatabase = openDatabase(context, allowDestructiveMigration = debugBuild)
}
