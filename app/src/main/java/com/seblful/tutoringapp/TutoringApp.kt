package com.seblful.tutoringapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.seblful.tutoringapp.notifications.Notifier
import com.seblful.tutoringapp.work.WorkScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TutoringApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var workScheduler: WorkScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifier.ensureChannel()
        workScheduler.ensurePeriodicMaintenance()
        // Catch up immediately: auto-complete anything already past and schedule reminders.
        workScheduler.refreshNow()
    }
}
