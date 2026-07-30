package com.seblful.klas.notifications

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Asks `AlarmManager` whether this app may still set an exact alarm. */
@Singleton
class AndroidExactAlarmPermission @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ExactAlarmPermission {

    /**
     * Asked on every call rather than cached: the grant is revocable from system settings while the app
     * is away, and the two callers both need it fresh — one is arming an alarm, the other is drawing the
     * row that reports on it.
     */
    override fun isGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
}
