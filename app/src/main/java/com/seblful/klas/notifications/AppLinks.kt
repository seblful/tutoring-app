package com.seblful.klas.notifications

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.seblful.klas.MainActivity

/**
 * The intent that opens the app at [destination] by its `klas://` link ([TrayNavigation.linkFor]), or plainly
 * when null — for notifications, widgets, the launcher's shortcuts and keyboard chords alike.
 */
internal fun Context.openAppIntent(destination: TrayNavigation.Destination?): Intent =
    Intent(this, MainActivity::class.java).apply {
        if (destination == null) {
            action = Intent.ACTION_MAIN
        } else {
            action = Intent.ACTION_VIEW
            data = TrayNavigation.linkFor(destination).toUri()
        }
    }
