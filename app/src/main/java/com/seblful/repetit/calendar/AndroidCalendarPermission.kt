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

    override fun isGranted(): Boolean = REQUIRED.all { granted(it) }

    override val required: Array<String> get() = REQUIRED

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /**
         * Read *and* write are both required: the mirror reads to find a writable calendar and to
         * locate an existing event, and writes to keep it in step. Granting one does not imply the
         * other, so both must be requested.
         */
        val REQUIRED = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    }
}
