package com.seblful.tutoringapp.work

import com.seblful.tutoringapp.domain.ReminderScheduler
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
}
