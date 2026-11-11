package com.seblful.klas.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.seblful.klas.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Asks the platform whether calendar access has been granted. Read *and* write are both required:
 * the mirror reads to find a writable calendar and to locate an existing event, and writes to keep
 * it in step.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidCalendarPermission @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : CalendarPermission {

    override fun isGranted(): Boolean = REQUIRED.all { granted(it) }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        /**
         * Read *and* write are both required: the mirror reads to find a writable calendar and to
         * locate an existing event, and writes to keep it in step. Granting one does not imply the
         * other, so both must be requested.
         */
        val REQUIRED = listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    }
}
