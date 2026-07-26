package com.seblful.klas.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that lives as long as the process, for work that **must finish** once it has started.
 *
 * `viewModelScope` is cancelled the moment its screen goes away, which is fine for reading — a
 * query nobody is looking at is waste — but wrong for a write. Tapping «Сохранить» and immediately
 * going back would otherwise cancel the save partway: the lesson's rows are written in one
 * transaction ([com.seblful.klas.data.local.Transactor]), but the calendar mirror and the reminder
 * refresh that follow the commit are not, so a cancelled save could commit a lesson and skip the
 * work that makes it visible.
 *
 * A view model launches the *write* here and keeps the *navigation* in `viewModelScope`, so leaving
 * the screen abandons only the part that concerns the screen.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    /**
     * `SupervisorJob` so one failed write cannot cancel the scope and take every later write with
     * it — each is independent, and the app has no notion of "all saving has stopped".
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
