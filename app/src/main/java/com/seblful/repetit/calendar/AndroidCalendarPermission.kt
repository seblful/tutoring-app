package com.seblful.repetit.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the platform whether calendar access has been granted. Read *and* write are both required:
 * the mirror reads to find a writable calendar and to locate an existing event, and writes to keep
 * it in step.
 */
@Singleton
class AndroidCalendarPermission @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarPermission {

    override fun isGranted(): Boolean =
        granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** The pair a caller must request when [isGranted] is false. */
        val REQUIRED = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    }
}
