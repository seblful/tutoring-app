package com.seblful.repetit.calendar

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CalendarModule {

    @Binds
    @Singleton
    abstract fun bindCalendarSync(impl: DeviceCalendarSync): CalendarSync

    @Binds
    @Singleton
    abstract fun bindCalendarPermission(impl: AndroidCalendarPermission): CalendarPermission
}
