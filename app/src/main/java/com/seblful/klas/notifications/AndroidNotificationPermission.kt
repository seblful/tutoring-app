package com.seblful.klas.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.seblful.klas.di.ApplicationContext
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Asks the platform whether a reminder would actually be shown.
 *
 * Two things have to be true, and the runtime permission is only one of them: notifications can also be
 * switched off for the whole app in system settings, on **every** Android version — including the ones
 * with no `POST_NOTIFICATIONS` to grant. A tutor who turned them off there and then wondered why no
 * reminder arrived would get the same silence the missing permission used to produce, so both are asked.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AndroidNotificationPermission @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NotificationPermission {

    override fun isGranted(): Boolean = hasRuntimePermission() && notificationsAllowed()

    override val canAsk: Boolean get() = REQUIRED.isNotEmpty()

    private fun hasRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** The app-wide switch in system settings, which no runtime permission covers. */
    private fun notificationsAllowed(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    companion object {
        /**
         * What the prompt asks for — empty below Android 13, where there is nothing to grant.
         *
         * `@SuppressLint("InlinedApi")`: `POST_NOTIFICATIONS` is a compile-time `String` constant, so the
         * reference is inlined and touching it on an older version is harmless — and it is only ever
         * *returned* above Android 13, where it exists to be granted.
         */
        val REQUIRED: List<String>
            @SuppressLint("InlinedApi")
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyList()
            }
    }
}
