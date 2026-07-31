package com.seblful.klas.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {

    @Binds
    @Singleton
    abstract fun bindNotifier(impl: AndroidNotifier): Notifier

    @Binds
    @Singleton
    abstract fun bindNotificationPermission(
        impl: AndroidNotificationPermission,
    ): NotificationPermission

    @Binds
    @Singleton
    abstract fun bindExactAlarmPermission(
        impl: AndroidExactAlarmPermission,
    ): ExactAlarmPermission

    @Binds
    @Singleton
    abstract fun bindAlarmClock(impl: AndroidAlarmClock): AlarmClock

    @Binds
    @Singleton
    abstract fun bindArmedReminders(impl: PrefsArmedReminders): ArmedReminders
}
