package com.seblful.klas.work

import com.seblful.klas.domain.reminders.DigestScheduler
import com.seblful.klas.domain.reminders.ReminderScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {

    @Binds
    @Singleton
    abstract fun bindReminderScheduler(impl: WorkScheduler): ReminderScheduler

    /**
     * The same object behind both ports, deliberately: `WorkScheduler` is where the app's time-driven work
     * is arranged, and the two interfaces are how the callers stay ignorant of which mechanism — an alarm
     * or a worker — each kind of thing rides on.
     */
    @Binds
    @Singleton
    abstract fun bindDigestScheduler(impl: WorkScheduler): DigestScheduler
}
